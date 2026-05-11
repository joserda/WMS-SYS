# 出库单 — 接口设计说明

> 版本：v1.0 | 日期：2026-05-11 | 对应任务：TASKS.md 选做A

---

## 1. 接口概览

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/outbound-orders` | 创建出库单，事务中写入出库单并扣减库存 |

---

## 2. 请求

### 2.1 Request Body

```json
{
  "customerName": "客户X",
  "items": [
    {
      "productId": 1,
      "quantity": 10,
      "locationCode": "WH-A-01-01"
    }
  ]
}
```

### 2.2 字段约束

| 字段 | 类型 | 必填 | 校验 |
|------|------|------|------|
| `customerName` | string | 是 | `@NotBlank` |
| `items` | array | 是 | `@NotEmpty` |
| `items[].productId` | long | 是 | `@NotNull` |
| `items[].quantity` | int | 是 | `@Min(1)` |
| `items[].locationCode` | string | 是 | `@NotBlank` |

---

## 3. 响应

### 3.1 成功 (201)

```json
{
  "code": 201,
  "message": "出库单创建成功",
  "data": {
    "id": 1,
    "orderNo": "OUT-20260511-001",
    "customerName": "客户X",
    "status": "COMPLETED",
    "items": [
      {
        "productId": 1,
        "productName": "蓝牙耳机 Pro",
        "quantity": 10,
        "locationCode": "WH-A-01-01"
      }
    ],
    "createdAt": "2026-05-11T15:00:00"
  }
}
```

### 3.2 错误响应

| 场景 | code | message |
|------|------|---------|
| 客户为空 | 400 | 客户名称不能为空 |
| 明细为空 | 400 | 出库明细不能为空 |
| 商品不存在 | 404 | 商品不存在: id=99 |
| 库位不存在 | 404 | 库位不存在: XX |
| 库存不足 | 400 | 库存不足: 蓝牙耳机 Pro @ WH-A-01-01 (当前: 5, 需要: 10) |
| 数量 ≤ 0 | 400 | 数量必须大于0 |

---

## 4. 并发安全方案

### 4.1 核心 SQL

```sql
UPDATE inventory
SET quantity = quantity - :quantity, updated_at = NOW()
WHERE product_id = :productId
  AND location_code = :locationCode
  AND quantity >= :quantity
```

### 4.2 工作原理

```
事务A (扣 10):                         事务B (扣 95, 同时到达):
                                         
UPDATE ... WHERE quantity >= 10         UPDATE ... WHERE quantity >= 95
  → PG 对该行加排他锁 🔒                  → PG 扫描到该行, 等待 A 释放锁 ⏳
  → 检查 quantity(100) >= 10 ✅          → ...
  → 写入: 100 - 10 = 90 ✅              
  → 释放锁, 受影响行数 = 1              
                                         → B 获得锁
                                         → 检查 quantity(90) >= 95 ❌
                                         → 不更新该行, 受影响行数 = 0
                                         
应用层检查受影响行数: = 1 → ✅          应用层检查受影响行数: = 0 → ❌ 库存不足
```

**关键保障**：
- PG 对 UPDATE 匹配的行**自动加排他锁**，第二个事务必须等待
- `WHERE quantity >= :quantity` 在加锁后**再次检查**，保证不会读到脏数据
- 一条 SQL 完成 **检查+扣减**，无 check-then-act 间隙

### 4.3 接口声明

```java
@Modifying
@Query(value = """
    UPDATE inventory
    SET quantity = quantity - :quantity, updated_at = NOW()
    WHERE product_id = :productId
      AND location_code = :locationCode
      AND quantity >= :quantity
    """, nativeQuery = true)
int deductQuantity(@Param("productId") Long productId,
                   @Param("locationCode") String locationCode,
                   @Param("quantity") int quantity);
```

返回值 = 受影响行数（0=失败, 1=成功）。

---

## 5. 业务逻辑

### 5.1 出库单号生成

格式 `OUT-YYYYMMDD-XXX`，与入库同算法（查当天 outbound_orders 的 count）。

### 5.2 Service 事务流程

```java
@Transactional
public OutboundOrderResponse createOutboundOrder(request) {
    // 1. 生成单号 OUT-YYYYMMDD-XXX
    // 2. 校验所有商品存在
    // 3. 校验所有库位存在
    // 4. save OutboundOrder (主表, status=COMPLETED)
    // 5. saveAll OutboundOrderItem (明细)
    // 6. 逐个调用 deductQuantity() → 受影响行数 = 0 则抛异常"库存不足"
    // 7. 返回响应
}
```

任一步骤失败 → `@Transactional` 回滚全部操作。

---

## 6. 代码变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `entity/OutboundOrder.java` | ➕ 新建 | 出库单主表实体 |
| `entity/OutboundOrderItem.java` | ➕ 新建 | 出库单明细实体 |
| `repository/OutboundOrderRepository.java` | ➕ 新建 | 含 `countTodayOrders` |
| `repository/OutboundOrderItemRepository.java` | ➕ 新建 | 出库明细 Repository |
| `dto/OutboundOrderCreateRequest.java` | ➕ 新建 | 请求 DTO |
| `dto/OutboundOrderResponse.java` | ➕ 新建 | 响应 DTO |
| `repository/InventoryRepository.java` | ✏️ 修改 | 添加 `deductQuantity` 原生 SQL |
| `service/InventoryService.java` | ✏️ 修改 | 实现 `createOutboundOrder()` |
| `controller/InventoryController.java` | ✏️ 修改 | `POST /api/outbound-orders` |

---

## 7. 测试要点

- [ ] 正常出库：库存正确扣减
- [ ] 库存不足：返回错误，出库单和库存均无变化（事务回滚）
- [ ] 并发安全：两个请求同时扣减，正确拒绝超卖
- [ ] 商品不存在：404
- [ ] 库位不存在：404
- [ ] 空明细/数量≤0：400
