package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderListResponse;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(inventoryService.queryInboundOrders(page, pageSize));
    }

    @GetMapping("/inbound-orders/{id}")
    public ApiResponse<InboundOrderResponse> getInboundOrder(@PathVariable Long id) {
        return ApiResponse.success(inventoryService.getInboundOrderById(id));
    }

    @GetMapping("/inventory")
    public ApiResponse<List<InventoryResponse>> queryInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.error(501, "请实现库存查询功能（任务2）");
    }
}
