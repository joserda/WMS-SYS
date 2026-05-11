import api from './client'

export interface Product {
  id: number
  name: string
  sku: string
  unit: string
  createdAt: string
  updatedAt: string
}

export const getProducts = (keyword?: string) =>
  api.get<any, { code: number; data: Product[] }>('/products', { params: { keyword } })

export const getProduct = (id: number) =>
  api.get<any, { code: number; data: Product }>(`/products/${id}`)

export const createProduct = (data: { name: string; sku: string; unit?: string }) =>
  api.post('/products', data)

export const updateProduct = (id: number, data: { name: string; unit?: string }) =>
  api.put(`/products/${id}`, data)

export const deleteProduct = (id: number) =>
  api.delete(`/products/${id}`)

export interface Warehouse {
  id: number
  code: string
  name: string
}

export interface Location {
  id: number
  warehouseId: number
  code: string
  status: string
}

export const getWarehouses = () =>
  api.get<any, { code: number; data: Warehouse[] }>('/warehouses')

export const getLocations = (warehouseId: number) =>
  api.get<any, { code: number; data: Location[] }>(`/warehouses/${warehouseId}/locations`)

export interface InventoryItem {
  productId: number
  productName: string
  sku: string
  locationCode: string
  warehouseName: string
  quantity: number
  updatedAt: string
}

export const getInventory = (params: {
  keyword?: string
  warehouseId?: number
  page?: number
  pageSize?: number
}) =>
  api.get<any, { code: number; data: { list: InventoryItem[]; total: number; page: number; pageSize: number } }>(
    '/inventory',
    { params }
  )

export interface InboundItemRequest {
  productId: number
  quantity: number
  locationCode: string
}

export const createInboundOrder = (data: {
  supplierName: string
  items: InboundItemRequest[]
}) =>
  api.post<any, { code: number; message: string; data: any }>('/inbound-orders', data)

export interface InboundOrderListItem {
  id: number
  orderNo: string
  supplierName: string
  status: string
  itemCount: number
  createdAt: string
}

export const getInboundOrders = (params: {
  keyword?: string
  page?: number
  pageSize?: number
}) =>
  api.get<any, { code: number; data: { list: InboundOrderListItem[]; total: number; page: number; pageSize: number } }>(
    '/inbound-orders',
    { params }
  )

export interface OutboundItemRequest {
  productId: number
  quantity: number
  locationCode: string
}

export const createOutboundOrder = (data: {
  customerName: string
  items: OutboundItemRequest[]
}) =>
  api.post<any, { code: number; message: string; data: any }>('/outbound-orders', data)

export interface OutboundOrderListItem {
  id: number
  orderNo: string
  supplierName: string
  status: string
  itemCount: number
  createdAt: string
}

export const getOutboundOrders = (params: {
  keyword?: string
  page?: number
  pageSize?: number
}) =>
  api.get<any, { code: number; data: { list: OutboundOrderListItem[]; total: number; page: number; pageSize: number } }>(
    '/outbound-orders',
    { params }
  )
