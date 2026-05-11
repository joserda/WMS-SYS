# Bug 定位与修复报告 — 任务3

> 日期：2026-05-11

---

## Bug 1：后端 — 删除商品未校验关联库存

### 定位

**文件**: `backend-java/src/main/java/com/wms/service/ProductService.java` L75-L82

```java
@Transactional
public void delete(Long id) {
    if (!productRepository.existsById(id)) {
        throw new BusinessException(404, "商品不存在");
    }
    productRepository.deleteById(id);  // ← 未检查 inventory 表
    log.info("删除商品: id={}", id);
}
```

### 后果

删除商品后，`inventory` 表中关联此 `product_id` 的行变为**孤立数据**（外键引用失效），导致库存查询出现商品名称为空的脏数据。

### 修复

删除前查 `inventory` 表，存在关联库存则拒绝删除：

```java
if (inventoryRepository.countByProductId(id) > 0) {
    throw new BusinessException("该商品存在库存记录，无法删除");
}
```

### 改动文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `repository/InventoryRepository.java` | ✏️ | 添加 `countByProductId()` |
| `service/ProductService.java` | ✏️ | 注入 `InventoryRepository` + 库存校验 |

---

## Bug 2：前端 — 编辑商品后页码跳回第 1 页

### 定位

**文件**: `frontend-vue/src/views/ProductsView.vue` L86

```typescript
const handleSubmit = async () => {
    // ... 保存逻辑 ...
    dialogVisible.value = false
    currentPage.value = 1   // ← 无差别重置页码
    await loadProducts()
}
```

### 后果

用户在任意页（如第 3 页）编辑商品后弹窗关闭，列表强制跳回第 1 页，**丢失浏览上下文**。

### 修复

区分新增和编辑场景：
- **新增**：重置到第 1 页（新商品应出现在列表前面）
- **编辑**：保持当前页码不变

```typescript
const isEdit = !!form.value.id
dialogVisible.value = false
if (!isEdit) {
    currentPage.value = 1  // 新增时跳到第 1 页
}
await loadProducts()
```

### 改动文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `views/ProductsView.vue` | ✏️ | 仅新增时重置页码 |

---

## 修复后验证

- [ ] 删除有库存的商品 → 提示"该商品存在库存记录，无法删除"
- [ ] 删除无库存的商品 → 正常删除
- [ ] 编辑第 3 页商品 → 弹窗关闭后停留在第 3 页
- [ ] 新增商品 → 弹窗关闭后跳回第 1 页
