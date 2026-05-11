<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventory, getWarehouses, type Warehouse } from '@/api'

const keyword = ref('')
const warehouseId = ref<number | undefined>(undefined)
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const warehouses = ref<Warehouse[]>([])

let searchTimer: ReturnType<typeof setTimeout> | null = null

onMounted(async () => {
  try {
    const res = await getWarehouses()
    warehouses.value = res.data
  } catch { /* */ }
  loadData()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getInventory({
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e: any) {
    ElMessage.error('加载库存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  page.value = 1
  loadData()
}

const onKeywordInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadData() }, 300)
}

const onPageChange = () => {
  loadData()
}

const getRowClass = ({ row }: any) => {
  if (row.quantity < 10) return 'row-low-stock'
  return ''
}

const getCellClass = ({ row, column }: any) => {
  if (column.property === 'quantity' && row.quantity < 10) {
    return 'cell-low-stock'
  }
  return ''
}
</script>

<template>
  <div class="inventory-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">库存查询</span>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称 / SKU..."
          style="width: 300px"
          clearable
          @input="onKeywordInput"
          @clear="onKeywordInput"
        />
        <el-select
          v-model="warehouseId"
          placeholder="全部仓库"
          clearable
          style="width: 180px"
          @change="onSearch"
        >
          <el-option
            v-for="wh in warehouses"
            :key="wh.id"
            :label="wh.name"
            :value="wh.id"
          />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
      </div>

      <el-table
        :data="list"
        v-loading="loading"
        border
        :row-class-name="getRowClass"
        :cell-class-name="getCellClass"
        empty-text="暂无库存数据，请先创建入库单或调整筛选条件"
      >
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="locationCode" label="库位编码" width="140" />
        <el-table-column prop="warehouseName" label="仓库" width="120" />
        <el-table-column prop="quantity" label="库存数量" width="100" align="center" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {}
</script>

<style scoped>
.inventory-page {
  max-width: 1400px;
  width: 100%;
  min-height: 420px;
}

.card-header {
  display: flex;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.card-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 18px;
  background: #409eff;
  border-radius: 2px;
  margin-right: 10px;
  vertical-align: middle;
  position: relative;
  top: -1px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: center;
}

:deep(.row-low-stock) {
  background-color: #fef0f0 !important;
}

:deep(.cell-low-stock) {
  color: #f56c6c;
  font-weight: 700;
}

.pagination-bar {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
