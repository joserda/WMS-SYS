# 入库单/出库单查询 + 列表页 — 设计说明

> 版本：v1.0 | 日期：2026-05-11 | 对应：选做 C + 扩展功能

---

## 1. 概述

在已有的入库/出库创建页面基础上，新增列表查询 Tab，实现 **分页查询 + 防抖搜索**。

---

## 2. 方案选型

### 2.1 分页 — 服务端分页

```
GET /api/inbound-orders?keyword=&page=1&pageSize=20
GET /api/outbound-orders?keyword=&page=1&pageSize=20
```

前端每次只请求一页数据（最多 20 条），后端返回 `PageResult`。与库存查询 `InventoryView.vue` 模式一致。

### 2.2 防抖搜索

用户输入关键词后 **300ms** 内无新输入则自动触发查询，无需点击按钮或按回车。

```ts
let timer: ReturnType<typeof setTimeout>
const onKeywordInput = () => {
  clearTimeout(timer)
  timer = setTimeout(() => { page.value = 1; loadData() }, 300)
}
```

> 手写防抖，不引入 lodash，保持零额外依赖。

### 2.3 虚拟滚动 — 不需要

服务端分页每页最多 20 条 DOM 行，渲染性能无瓶颈。无需虚拟滚动。

---

## 3. 后端接口

### 3.1 出库单列表（新增）

```
GET /api/outbound-orders?keyword=&page=1&pageSize=20
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `keyword` | string | 否 | — | 单号 / 客户名称模糊搜索 |
| `page` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 20 | 每页条数 |

### 3.2 入库单列表接口增强（已有）

`GET /api/inbound-orders` 已存在，但当前不支持 keyword 筛选。新增 `keyword` 参数，支持按单号/供应商名称搜索。

### 3.3 Repository 层

```java
// InboundOrderRepository / OutboundOrderRepository
@Query("SELECT o FROM InboundOrder o WHERE " +
       "(:keyword IS NULL OR o.orderNo LIKE %:keyword% OR o.supplierName LIKE %:keyword%)")
Page<InboundOrder> search(@Param("keyword") String keyword, Pageable pageable);
```

> `IS NULL` 恒真模式：不传 keyword 则不过滤。

---

## 4. 前端设计

### 4.1 Tab 页结构

每个管理页面拆为两个 Tab：

```
InboundView.vue                     OutboundView.vue
┌─────────────────────┐             ┌─────────────────────┐
│ el-tabs              │             │ el-tabs              │
│  [创建入库单] [入库单列表] │             │  [创建出库单] [出库单列表] │
└─────────────────────┘             └─────────────────────┘
```

- **Tab1**：现有创建表单（不变）
- **Tab2**：新增列表页 = 搜索栏 + `el-table` + `el-pagination`

### 4.2 列表页布局

```
┌─ 搜索栏 ────────────────────────────────┐
│ [单号/供应商] input  [搜索] btn          │
└──────────────────────────────────────────┘

┌─ el-table ───────────────────────────────┐
│ 单号 | 供应商 | 状态 | 明细数 | 创建时间   │
│ IN-20260511-001 | 供应商A | COMPLETED | 3 │
└──────────────────────────────────────────┘

                     [分页: total / pager]
```

### 4.3 防抖实现

```ts
// 输入框绑定 @input 事件
// 每次输入清除旧定时器，300ms 无新输入后自动查
<el-input @input="onKeywordInput" @clear="onKeywordInput" />
```

### 4.4 分页

与 `InventoryView.vue` 完全一致：`el-pagination` + `@current-change="loadData"`，后端分页。

---

## 5. 代码变更

### 后端

| 文件 | 操作 | 说明 |
|------|------|------|
| `repository/InboundOrderRepository.java` | ✏️ | `search()` 支持 keyword |
| `repository/OutboundOrderRepository.java` | ✏️ | `search()` 支持 keyword |
| `service/InventoryService.java` | ✏️ | `queryOutboundOrders()` 出库单列表 |
| `controller/InventoryController.java` | ✏️ | `GET /api/outbound-orders` |

### 前端

| 文件 | 操作 | 说明 |
|------|------|------|
| `api/index.ts` | ✏️ | + `getInboundOrders()` + `getOutboundOrders()` |
| `views/InboundView.vue` | ✏️ | Tab 改造：创建 + 列表 + 防抖搜索 |
| `views/OutboundView.vue` | ✏️ | Tab 改造：创建 + 列表 + 防抖搜索 |
