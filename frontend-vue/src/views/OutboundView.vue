<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProducts, getWarehouses, getLocations, createOutboundOrder, type Warehouse, type Location } from '@/api'

interface DetailRow {
  productId: number | null
  productName: string
  quantity: number
  warehouseId: number | null
  locationCode: string
}

const customerName = ref('')
const rows = ref<DetailRow[]>([])
const submitting = ref(false)

const products = ref<{ id: number; name: string; sku: string }[]>([])
const warehouses = ref<Warehouse[]>([])
const rowLocations = ref<Record<string, Location[]>>({})

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
  if (!customerName.value.trim()) {
    ElMessage.warning('请输入客户名称')
    return
  }
  if (rows.value.length === 0) {
    ElMessage.warning('请添加至少一条出库明细')
    return
  }
  for (let i = 0; i < rows.value.length; i++) {
    const r = rows.value[i]
    if (!r.productId) { ElMessage.warning(`第 ${i + 1} 行：请选择商品`); return }
    if (!r.locationCode) { ElMessage.warning(`第 ${i + 1} 行：请选择库位`); return }
    if (r.quantity < 1) { ElMessage.warning(`第 ${i + 1} 行：数量必须大于 0`); return }
  }

  submitting.value = true
  try {
    await createOutboundOrder({
      customerName: customerName.value.trim(),
      items: rows.value.map(r => ({
        productId: r.productId!,
        quantity: r.quantity,
        locationCode: r.locationCode,
      })),
    })
    ElMessage.success('出库单创建成功')
    customerName.value = ''
    rows.value = []
    rowLocations.value = {}
  } catch (e: any) {
    const msg = e.response?.data?.message || '创建失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="outbound-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">出库管理</span>
        </div>
      </template>

      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="客户名称" required>
          <el-input
            v-model="customerName"
            placeholder="请输入客户名称"
            style="max-width: 400px"
            clearable
          />
        </el-form-item>
      </el-form>

      <div style="margin-bottom: 20px">
        <el-button type="primary" @click="addRow">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          添加明细
        </el-button>
      </div>

      <div
        v-for="(row, index) in rows"
        :key="index"
        class="detail-row"
      >
        <div class="detail-row-index">#{{ index + 1 }}</div>

        <el-select
          v-model="row.productId"
          placeholder="选择商品"
          filterable
          style="width: 220px"
          @change="(val: number) => onProductChange(index, val)"
        >
          <el-option
            v-for="p in products"
            :key="p.id"
            :label="`${p.name} (${p.sku})`"
            :value="p.id"
          />
        </el-select>

        <el-select
          v-model="row.warehouseId"
          placeholder="选择仓库"
          style="width: 160px"
          @change="(val: number) => onWarehouseChange(index, val)"
        >
          <el-option
            v-for="wh in warehouses"
            :key="wh.id"
            :label="wh.name"
            :value="wh.id"
          />
        </el-select>

        <el-select
          v-model="row.locationCode"
          placeholder="选择库位"
          style="width: 180px"
          :disabled="!row.warehouseId"
        >
          <el-option
            v-for="loc in (rowLocations[index] || [])"
            :key="loc.id"
            :label="`${loc.code} (${loc.status === 'FREE' ? '空闲' : '已占用'})`"
            :value="loc.code"
          />
        </el-select>

        <el-input-number
          v-model="row.quantity"
          :min="1"
          :max="99999"
          style="width: 140px"
        />

        <el-button
          type="danger"
          :icon="Delete"
          circle
          size="small"
          @click="removeRow(index)"
        />
      </div>

      <el-empty
        v-if="rows.length === 0"
        description="暂无出库明细，请点击「添加明细」"
        :image-size="100"
      />

      <div class="submit-bar">
        <el-button
          type="danger"
          size="large"
          :loading="submitting"
          :disabled="rows.length === 0"
          @click="handleSubmit"
        >
          提交出库单
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { Plus, Delete } from '@element-plus/icons-vue'
export default { components: { Plus, Delete } }
</script>

<style scoped>
.outbound-page {
  max-width: 960px;
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
  background: #e6a23c;
  border-radius: 2px;
  margin-right: 10px;
  vertical-align: middle;
  position: relative;
  top: -1px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: border-color 0.2s;
}

.detail-row:hover {
  border-color: #c0c4cc;
}

.detail-row-index {
  font-size: 12px;
  font-weight: 700;
  color: #909399;
  width: 28px;
  text-align: center;
  flex-shrink: 0;
}

.submit-bar {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}
</style>
