# 库存查询 — 接口设计说明

> 版本：v1.0 | 日期：2026-05-11 | 对应任务：TASKS.md 任务2

---

## 1. 接口概览

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/api/inventory?keyword=&warehouseId=&page=1&pageSize=20` | 库存分页查询，支持商品名称/SKU 模糊搜索 + 仓库筛选 |

---

## 2. 请求

### 2.1 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `keyword` | string | 否 | — | 商品名称或 SKU 模糊搜索 |
| `warehouseId` | int | 否 | — | 仓库 ID 筛选 |
| `page` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 20 | 每页条数 |

### 2.2 示例

```
GET /api/inventory?keyword=蓝牙&warehouseId=1&page=1&pageSize=20
```

---

## 3. 响应

### 3.1 成功 (200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "productId": 1,
        "productName": "蓝牙耳机 Pro",
        "sku": "SKU-001",
        "locationCode": "WH-A-01-01",
        "warehouseName": "广州主仓",
        "quantity": 100,
        "updatedAt": "2026-05-11T09:30:00"
      }
    ],
    "total": 15,
    "page": 1,
    "pageSize": 20
  }
}
```

### 3.2 空结果

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "total": 0,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 4. 业务逻辑

### 4.1 JPQL 查询

```sql
SELECT new com.wms.dto.InventoryResponse(
    i.productId, p.name, p.sku, i.locationCode, w.name, i.quantity, i.updatedAt
)
FROM Inventory i
JOIN Product p    ON i.productId = p.id
JOIN Location l   ON i.locationCode = l.code
JOIN Warehouse w  ON l.warehouseId = w.id
WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%)
  AND (:warehouseId IS NULL OR w.id = :warehouseId)
ORDER BY i.updatedAt DESC
```

- 3 表 JOIN 一次查询完成，无 N+1
- `keyword` 为 `NULL` 时 `IS NULL` 恒真，条件自动跳过
- `warehouseId` 同理，不传则不限仓库
- Spring Data Page 自动处理 `COUNT` + `LIMIT/OFFSET`

### 4.2 索引策略

| 表 | 列 | 类型 | 作用 |
|----|-----|------|------|
| `inventory` | `product_id` | BTREE | JOIN products 时加速 |
| `inventory` | `updated_at` | BTREE | ORDER BY 排序不触发全表排序 |

通过 JPA `@Table(indexes = {...})` 注解，`ddl-auto: update` 自动创建。

---

## 5. 代码变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `entity/Inventory.java` | ✏️ | 添加 `@Table(indexes = ...)` 2 个索引 |
| `repository/InventoryRepository.java` | ✏️ | 添加 JPQL JOIN 分页查询 |
| `service/InventoryService.java` | ✏️ | 实现 `queryInventory()` 返回 PageResult |
| `controller/InventoryController.java` | ✏️ | 返回 PageResult 替代 501 |

---

## 6. 测试要点

- [ ] 无参数查询：返回全部库存，按更新时间倒序
- [ ] keyword 模糊搜索：商品名称匹配
- [ ] keyword 模糊搜索：SKU 匹配
- [ ] warehouseId 筛选：只返回指定仓库库存
- [ ] keyword + warehouseId 组合筛选
- [ ] 分页：page=1 返回前 20 条
- [ ] 空结果：total=0, list=[]
