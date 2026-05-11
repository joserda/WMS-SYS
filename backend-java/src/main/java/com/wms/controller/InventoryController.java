package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderListResponse;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.dto.OutboundOrderCreateRequest;
import com.wms.dto.OutboundOrderResponse;
import com.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderResponse> createInboundOrder(@Valid @RequestBody InboundOrderCreateRequest request) {
        InboundOrderResponse response = inventoryService.createInboundOrder(request);
        return ApiResponse.success("入库单创建成功", response);
    }

    @GetMapping("/inbound-orders")
    public ApiResponse<PageResult<InboundOrderListResponse>> listInboundOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(inventoryService.queryInboundOrders(keyword, page, pageSize));
    }

    @GetMapping("/inbound-orders/{id}")
    public ApiResponse<InboundOrderResponse> getInboundOrder(@PathVariable Long id) {
        return ApiResponse.success(inventoryService.getInboundOrderById(id));
    }

    @PostMapping("/outbound-orders")
    public ApiResponse<OutboundOrderResponse> createOutboundOrder(@Valid @RequestBody OutboundOrderCreateRequest request) {
        OutboundOrderResponse response = inventoryService.createOutboundOrder(request);
        return ApiResponse.success("出库单创建成功", response);
    }

    @GetMapping("/outbound-orders")
    public ApiResponse<PageResult<InboundOrderListResponse>> listOutboundOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(inventoryService.queryOutboundOrders(keyword, page, pageSize));
    }

    @GetMapping("/inventory")
    public ApiResponse<PageResult<InventoryResponse>> queryInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(inventoryService.queryInventory(keyword, warehouseId, page, pageSize));
    }
}
