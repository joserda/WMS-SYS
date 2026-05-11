<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProducts, getWarehouses, getLocations, createInboundOrder, getInboundOrders, type Warehouse, type Location } from '@/api'

interface DetailRow {
  productId: number | null
  productName: string
  quantity: number
  warehouseId: number | null
  locationCode: string
}

const activeTab = ref('create')

const supplierName = ref('')
const rows = ref<DetailRow[]>([])
const submitting = ref(false)

const products = ref<{ id: number; name: string; sku: string }[]>([])
const warehouses = ref<Warehouse[]>([])
const rowLocations = ref<Record<string, Location[]>>({})

const listKeyword = ref('')
const listLoading = ref(false)
const orderList = ref<any[]>([])
const listTotal = ref(0)
const listPage = ref(1)
const listPageSize = ref(20)

let searchTimer: ReturnType<typeof setTimeout> | null = null

onMounted(async () => {
  try {
    const [prodRes, whRes] = await Promise.all([getProducts(), getWarehouses()])
    products.value = prodRes.data
    warehouses.value = whRes.data
  } catch {
    ElMessage.error('加载基础数据失败')
  }
})

const addRow = () => {
  rows.value.push({ productId: null, productName: '', quantity: 1, warehouseId: null, locationCode: '' })
}

const removeRow = (index: number) => {
  rows.value.splice(index, 1)
}

const onProductChange = (index: number, val: number) => {
  const p = products.value.find(p => p.id === val)
  rows.value[index].productName = p?.name || ''
}

const onWarehouseChange = async (index: number, warehouseId: number) => {
  rows.value[index].locationCode = ''
  rowLocations.value[index] = []
  if (!warehouseId) return
  try {
    const res = await getLocations(warehouseId)
    rowLocations.value[index] = res.data
    if (res.data.length > 0) {
      rows.value[index].locationCode = res.data[0].code
    }
  } catch {
    ElMessage.error('加载库位失败')
  }
}

const handleSubmit = async () => {
  if (!supplierName.value.trim()) { ElMessage.warning('请输入供应商名称'); return }
  if (rows.value.length === 0) { ElMessage.warning('请添加至少一条入库明细'); return }
  for (let i = 0; i < rows.value.length; i++) {
    const r = rows.value[i]
    if (!r.productId) { ElMessage.warning(`第 ${i + 1} 行：请选择商品`); return }
    if (!r.locationCode) { ElMessage.warning(`第 ${i + 1} 行：请选择库位`); return }
    if (r.quantity < 1) { ElMessage.warning(`第 ${i + 1} 行：数量必须大于 0`); return }
  }
  submitting.value = true
  try {
    await createInboundOrder({
      supplierName: supplierName.value.trim(),
      items: rows.value.map(r => ({ productId: r.productId!, quantity: r.quantity, locationCode: r.locationCode })),
    })
    ElMessage.success('入库单创建成功')
    supplierName.value = ''
    rows.value = []
    rowLocations.value = {}
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

const loadOrders = async () => {
  listLoading.value = true
  try {
    const res = await getInboundOrders({
      keyword: listKeyword.value || undefined,
      page: listPage.value,
      pageSize: listPageSize.value,
    })
    orderList.value = res.data.list || []
    listTotal.value = res.data.total || 0
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e.response?.data?.message || e.message))
  } finally {
    listLoading.value = false
  }
}

const onKeywordInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { listPage.value = 1; loadOrders() }, 300)
}

const onPageChange = () => {
  loadOrders()
}

const onTabChange = (tab: string) => {
  activeTab.value = tab
  if (tab === 'list' && orderList.value.length === 0) {
    loadOrders()
  }
}
</script>

<template>
  <div class="inbound-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">入库管理</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="创建入库单" name="create">
          <el-form label-width="100px" @submit.prevent>
            <el-form-item label="供应商名称" required>
              <el-input v-model="supplierName" placeholder="请输入供应商名称" style="max-width: 400px" clearable />
            </el-form-item>
          </el-form>
          <div style="margin-bottom: 20px">
            <el-button type="primary" @click="addRow"><el-icon style="margin-right: 4px"><Plus /></el-icon>添加明细</el-button>
          </div>
          <div v-for="(row, index) in rows" :key="index" class="detail-row">
            <div class="detail-row-index">#{{ index + 1 }}</div>
            <el-select v-model="row.productId" placeholder="选择商品" filterable style="width: 220px" @change="(val: number) => onProductChange(index, val)">
              <el-option v-for="p in products" :key="p.id" :label="`${p.name} (${p.sku})`" :value="p.id" />
            </el-select>
            <el-select v-model="row.warehouseId" placeholder="选择仓库" style="width: 160px" @change="(val: number) => onWarehouseChange(index, val)">
              <el-option v-for="wh in warehouses" :key="wh.id" :label="wh.name" :value="wh.id" />
            </el-select>
            <el-select v-model="row.locationCode" placeholder="选择库位" style="width: 180px" :disabled="!row.warehouseId">
              <el-option v-for="loc in (rowLocations[index] || [])" :key="loc.id" :label="`${loc.code} (${loc.status === 'FREE' ? '空闲' : '已占用'})`" :value="loc.code" />
            </el-select>
            <el-input-number v-model="row.quantity" :min="1" :max="99999" style="width: 140px" />
            <el-button type="danger" :icon="Delete" circle size="small" @click="removeRow(index)" />
          </div>
          <el-empty v-if="rows.length === 0" description="暂无入库明细，请点击「添加明细」" :image-size="100" />
          <div class="submit-bar">
            <el-button type="success" size="large" :loading="submitting" :disabled="rows.length === 0" @click="handleSubmit">提交入库单</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="入库单列表" name="list">
          <div class="search-bar">
            <el-input v-model="listKeyword" placeholder="搜索单号 / 供应商..." style="width: 320px" clearable @input="onKeywordInput" @clear="onKeywordInput" />
          </div>
          <el-table :data="orderList" v-loading="listLoading" border empty-text="暂无入库单">
            <el-table-column prop="orderNo" label="单号" width="180" />
            <el-table-column prop="supplierName" label="供应商" min-width="160" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="itemCount" label="明细数" width="80" align="center" />
            <el-table-column prop="createdAt" label="创建时间" width="180" />
          </el-table>
          <div class="pagination-bar">
            <el-pagination v-model:current-page="listPage" :page-size="listPageSize" :total="listTotal" layout="total, prev, pager, next" @current-change="onPageChange" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script lang="ts">
import { Plus, Delete } from '@element-plus/icons-vue'
export default { components: { Plus, Delete } }
</script>

<style scoped>
.inbound-page { max-width: 960px; }
.card-header { display: flex; align-items: center; }
.card-title { font-size: 18px; font-weight: 600; color: #303133; }
.card-title::before { content: ''; display: inline-block; width: 4px; height: 18px; background: #409eff; border-radius: 2px; margin-right: 10px; vertical-align: middle; position: relative; top: -1px; }
.detail-row { display: flex; align-items: center; gap: 12px; padding: 14px 16px; margin-bottom: 10px; background: #f5f7fa; border-radius: 8px; border: 1px solid #e4e7ed; transition: border-color 0.2s; }
.detail-row:hover { border-color: #c0c4cc; }
.detail-row-index { font-size: 12px; font-weight: 700; color: #909399; width: 28px; text-align: center; flex-shrink: 0; }
.submit-bar { margin-top: 24px; padding-top: 20px; border-top: 1px solid #ebeef5; }
.search-bar { display: flex; gap: 12px; margin-bottom: 20px; align-items: center; }
.pagination-bar { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
