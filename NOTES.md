# NOTES.md — 开发记录与方案说明

> WMS 仓储管理系统 | 2026-05-11

---

## 一、技术栈与架构

| 层 | 技术 |
|----|------|
| 后端 | Java 17, Spring Boot 3.2.5, Spring Data JPA, PostgreSQL 16 |
| 前端 | Vue 3.4 + TypeScript, Element Plus 2.7, Vite 5, Pinia |
| 数据库 | PostgreSQL 16 (Docker), Hibernate ddl-auto |
| 部署 | Docker Compose (仅 PG) + 本地 Maven/Vite |

### 数据库命令

```bash
# 启动 PostgreSQL
docker-compose up -d

# 启动后端
cd backend-java && mvn spring-boot:run

# 启动前端
cd frontend-vue && npm run dev
```

### 演示数据路径

| 资源 | 访问 |
|------|------|
| 后端 API | `http://localhost:8080` |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| 前端 | `http://localhost:5173` |

---

## 二、Bug 修复记录（任务3）

### Bug 1：后端 — 删除商品未校验关联库存

**定位**：[`ProductService.delete()`](backend-java/src/main/java/com/wms/service/ProductService.java) 方法中只检查了商品是否存在，直接执行 `productRepository.deleteById(id)`，未检查 `inventory` 表是否存在关联数据。

**后果**：删除商品后 `inventory` 表中的 `product_id` 变成孤立引用，库存查询出现脏数据。

**修复**：在 `ProductService` 中注入 `InventoryRepository`，删除前调用 `inventoryRepository.countByProductId(id) > 0` 判断，存在库存则抛出 `BusinessException("该商品存在库存记录，无法删除")`。

### Bug 2：前端 — 编辑商品后页码跳回第 1 页

**定位**：[`ProductsView.vue`](frontend-vue/src/views/ProductsView.vue) 的 `handleSubmit()` 方法中无论新增还是编辑，都执行 `currentPage.value = 1`。

**后果**：用户在第 3 页编辑某商品后弹窗关闭，列表跳回第 1 页，丢失浏览上下文。

**修复**：改为仅新增时重置页码：

```typescript
if (!form.value.id) {
  currentPage.value = 1
}
```

编辑时保持当前页码不变。

---

## 三、必做任务实现要点

### 任务 1：入库单创建

**后端**：
- 单号生成：`IN-YYYYMMDD-XXX`，查当天 `COUNT(*)` + 1
- 库存累加：PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` UPSERT，原子操作保证并发安全
- 事务：`@Transactional` 覆盖单号生成→写主表→写明细→累加库存全流程

**前端**：
- 供应商名称 + 多行明细（商品搜索下拉 → 仓库下拉 → 库位级联 → 数量）
- 提交前前端逐行校验，提交后重置表单
- 列表 Tab：服务端分页 + 300ms 防抖搜索

### 任务 2：库存查询

**后端**：
- JPQL 3 表 JOIN（`Inventory → Product → Location → Warehouse`）
- `IS NULL` 恒真模式动态条件：不传 keyword/warehouseId 自动跳过筛选
- 服务端分页 `PageRequest` + `Page<>`
- 核心索引：`idx_inventory_product_id` + `idx_inventory_updated_at`

**前端**：
- 搜索栏：商品名称/SKU 输入 + 仓库下拉
- 库存 < 10 行浅红背景 + 数字红色加粗
- 300ms 防抖输入搜索

---

## 四、选做任务实现说明

### 选做 A：出库单 + 库存扣减并发安全

**方案**：PostgreSQL `UPDATE` + 行级锁 + 条件检查

```sql
UPDATE inventory
SET quantity = quantity - :quantity, updated_at = NOW()
WHERE product_id = :productId
  AND location_code = :locationCode
  AND quantity >= :quantity
```

**选择理由**：
1. 一条 SQL 原子完成"检查+扣减"，无 check-then-act 间隙
2. PG 对 UPDATE 匹配的行自动加排他锁，第二个事务必须等待
3. `WHERE quantity >= :quantity` 在锁获取后再次检查，杜绝脏读
4. 应用层检查受影响行数：`= 0` 表示库存不足，`= 1` 表示扣减成功
5. 与入库 UPSERT 风格一致，无需额外依赖

**对比淘汰方案**：
- `@Version` 乐观锁：高并发下自旋重试浪费 CPU，侵入实体设计
- Redis 分布式锁：引入额外组件，锁与数据库不在同一事务
- `SELECT ... FOR UPDATE`：多一次无效 SELECT 往返

### 选做 B：单元测试

- 后端：`InventoryServiceTest`（10 用例：入库 4 + 出库 3 + 列表 1 + 详情 2）— Mock Repository
- 后端：`InventoryControllerTest`（11 用例：入库 5 + 出库 3 + 列表 1 + 详情 2）— MockMvc
- **前后端合计 21/21 全部通过**

测试覆盖场景：
  - 正常创建 / 商品不存在 / 库位不存在 / 单号递增 / 库存不足
  - 供应商/客户空校验 / 明细空校验 / 数量为 0 校验
  - 分页列表 / 详情 / 404

### 选做 C：前端性能优化

| 优化策略 | 实现 |
|----------|------|
| **服务端分页** | 所有列表接口使用 `PageRequest` + 后端分页，每页 20 条，DOM 永远 ≤20 行 |
| **防抖搜索** | 手写 `setTimeout` 300ms debounce，`@input` 事件驱动，边输入边搜 |
| **虚拟滚动** | 不采用 — 服务端分页已解决性能问题，500+ 数据集每次仅渲染 20 行 |

商品管理页从"前端分页（一次拉所有数据 + JS 切片）"改为后端分页，彻底解决大数据量卡顿问题。

---

## 五、UI 设计：Precision Lab 亮色科技风

| 要素 | 实现 |
|------|------|
| 页面底色 | `#f0f4f8` 浅灰蓝，轻盈通透 |
| 头部 | `linear-gradient(135deg, #3a7bd5, #409eff)` + 底部发光带 |
| 卡片 | 12px 圆角 + 轻阴影 + hover 浮起 |
| 表格 | 表头淡蓝 `#f0f7ff` + 行 hover 浅蓝光晕 |
| 单号列 | `JetBrains Mono` 等宽字体（科技风辨识点） |
| 按钮 | 彩色阴影 + hover 上浮 1px |
| 动效 | 页面入场 `fadeInUp` 0.4s |
| 空状态 | 引导性文案，指示下一步操作 |
| 宽度 | 自适应 `max-width: 1400px`，大屏不浪费空间 |

---

## 六、API 汇总

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/inbound-orders` | 创建入库单 |
| GET | `/api/inbound-orders` | 入库单列表 (分页 + keyword) |
| GET | `/api/inbound-orders/{id}` | 入库单详情 |
| POST | `/api/outbound-orders` | 创建出库单 |
| GET | `/api/outbound-orders` | 出库单列表 (分页 + keyword) |
| GET | `/api/inventory` | 库存查询 (分页 + keyword + warehouseId) |
| GET | `/api/products` | 商品列表 (分页 + keyword) |
| POST | `/api/products` | 新增商品 |
| PUT | `/api/products/{id}` | 更新商品 |
| DELETE | `/api/products/{id}` | 删除商品 (含库存校验) |
| GET | `/api/warehouses` | 仓库列表 |
| GET | `/api/warehouses/{id}/locations` | 库位列表 |

---

## 七、设计文档索引

| 文档 | 路径 |
|------|------|
| 入库单接口设计 | `docs/specs/2026-05-11-inbound-order-design.md` |
| 库存查询接口设计 | `docs/specs/2026-05-11-inventory-query-design.md` |
| Bug 定位与修复 | `docs/specs/2026-05-11-bug-report.md` |
| 出库单接口设计 | `docs/specs/2026-05-11-outbound-order-design.md` |
| 列表查询页设计 | `docs/specs/2026-05-11-order-list-design.md` |

---

## 八、提交检查清单

- [x] 所有必做任务功能正常运行
- [x] 代码可以一键启动（`docker-compose up -d` + `mvn spring-boot:run` + `npm run dev`）
- [x] 没有明显的 bug 或报错
- [x] Git 提交记录清晰（小步提交，message 有意义）
- [x] `NOTES.md` 已填写
- [x] 选做任务已在 NOTES.md 中说明
