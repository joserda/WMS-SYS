# 入库单创建 — 接口设计说明

> 版本：v1.0 | 日期：2026-05-11 | 对应任务：TASKS.md 任务1

---

## 1. 接口概览

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/inbound-orders` | 创建入库单，事务中写入主表、明细并原子累加库存 |

---

## 2. 请求

### 2.1 Request

```
POST /api/inbound-orders
Content-Type: application/json
```

### 2.2 Request Body

```json
{
  "supplierName": "供应商A",
  "items": [
    {
      "productId": 1,
      "quantity": 100,
      "locationCode": "WH-A-01-01"
    }
  ]
}
```

### 2.3 字段约束

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `supplierName` | string | 是 | `@NotBlank` |
| `items` | array | 是 | `@NotEmpty`，至少 1 条 |
| `items[].productId` | long | 是 | `@NotNull` + 数据库存在 |
| `items[].quantity` | int | 是 | `@Min(1)` |
| `items[].locationCode` | string | 是 | `@NotBlank` + 数据库存在 |

---

## 3. 响应

### 3.1 成功 (201)

```json
{
  "code": 201,
  "message": "入库单创建成功",
  "data": {
    "id": 1,
    "orderNo": "IN-20260511-001",
    "supplierName": "供应商A",
    "status": "COMPLETED",
    "items": [
      {
        "productId": 1,
        "productName": "蓝牙耳机 Pro",
        "quantity": 100,
        "locationCode": "WH-A-01-01"
      }
    ],
    "createdAt": "2026-05-11T10:00:00"
  }
}
```

### 3.2 错误响应

| 场景 | code | message |
|------|------|---------|
| 供应商为空 | 400 | 供应商名称不能为空 |
| 明细为空 | 400 | 入库明细不能为空 |
| 商品不存在 | 404 | 商品不存在: id=99 |
| 库位不存在 | 404 | 库位不存在: XX-INVALID |
| 数量 ≤ 0 | 400 | 数量必须大于0 |
| 系统内部错误 | 500 | 系统内部错误 |

---

## 4. 业务逻辑

### 4.1 单号生成

```
格式：IN-YYYYMMDD-XXX（例 IN-20260511-001）

算法：
1. 查询当天已创建的单数
   SELECT COUNT(*) FROM inbound_orders
   WHERE created_at >= :todayStart AND created_at < :tomorrowStart

2. 序号 = count + 1，格式化为 3 位（001, 002, ...）

3. 结果 = String.format("IN-%s-%03d", yyyyMMdd, seq)
```

> 选型理由：COUNT 避免解析 order_no 字符串，利用 created_at 索引，简单高效。

### 4.2 库存累加 (PostgreSQL UPSERT)

```sql
INSERT INTO inventory (product_id, location_code, quantity)
VALUES (:productId, :locationCode, :quantity)
ON CONFLICT (product_id, location_code)
DO UPDATE SET quantity = inventory.quantity + :quantity
```

> `ON CONFLICT` 命中 `uk_product_location` 唯一约束。
> `DO UPDATE` 中 `inventory.quantity` 引用表中已存在的值，PG 自动对冲突行加排他锁，保证原子读写，消除 Lost Update。

### 4.3 事务边界

```java
@Transactional
public InboundOrderResponse createInboundOrder(request) {
    // 1. 生成单号 (查当天 count)
    // 2. 校验所有商品存在
    // 3. 校验所有库位存在
    // 4. save InboundOrder (主表, status=COMPLETED)
    // 5. saveAll InboundOrderItem (明细)
    // 6. upsertQuantity × N (PG UPSERT)
    // 7. 构建并返回响应
}
```

任一步骤抛异常，`@Transactional` 保证全部回滚，数据库不会出现孤立数据。

---

## 5. 技术方案选型

**方案 B：JPA + 原生 SQL UPSERT**

| 维度 | 说明 |
|------|------|
| 单号生成 | JPA `@Query` COUNT 当天记录 |
| 校验 | JPA `findById` / `existsByCode` |
| 库存累加 | `@Modifying` + `nativeQuery` + PG `ON CONFLICT` |
| 事务 | Spring `@Transactional` |
| 并发安全 | PG 行级锁，数据库层原子保证 |

---

## 6. 代码变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `repository/InboundOrderRepository.java` | ✏️ 修改 | 添加当天序号查询方法 |
| `repository/InboundOrderItemRepository.java` | ➕ 新建 | 入库明细 Repository |
| `repository/InventoryRepository.java` | ✏️ 修改 | 添加 `upsertQuantity` 原生 SQL |
| `dto/InboundOrderResponse.java` | ➕ 新建 | 入库单响应 DTO |
| `service/InventoryService.java` | ✏️ 修改 | 实现 `createInboundOrder()` |
| `controller/InventoryController.java` | ✏️ 修改 | 调用 service 并返回 201 |

---

## 7. 测试要点

- [ ] 正常创建：单号递增，库存正确累加
- [ ] 商品不存在：返回 404
- [ ] 库位不存在：返回 404
- [ ] 数量 ≤ 0：返回 400
- [ ] 空明细：返回 400
- [ ] 库存累加验证：对同一库位多次入库，quantity 正确累加
- [ ] 事务回滚：中途抛异常，主表和库存均无变化
