package com.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.BusinessException;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderListResponse;
import com.wms.dto.InboundOrderResponse;
import com.wms.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void createInboundOrder_shouldReturnSuccess() throws Exception {
        InboundOrderResponse response = InboundOrderResponse.builder()
                .id(1L)
                .orderNo("IN-20260511-001")
                .supplierName("供应商A")
                .status("COMPLETED")
                .items(List.of(InboundOrderResponse.InboundItemResponse.builder()
                        .productId(1L).productName("蓝牙耳机 Pro")
                        .quantity(100).locationCode("WH-A-01-01").build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(inventoryService.createInboundOrder(any())).thenReturn(response);

        String body = """
                {
                    "supplierName": "供应商A",
                    "items": [
                        { "productId": 1, "quantity": 100, "locationCode": "WH-A-01-01" }
                    ]
                }""";

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("IN-20260511-001"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].productName").value("蓝牙耳机 Pro"));
    }

    @Test
    void createInboundOrder_shouldFailWhenSupplierNameEmpty() throws Exception {
        String body = """
                {
                    "supplierName": "",
                    "items": [
                        { "productId": 1, "quantity": 100, "locationCode": "WH-A-01-01" }
                    ]
                }""";

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createInboundOrder_shouldFailWhenItemsEmpty() throws Exception {
        String body = """
                {
                    "supplierName": "供应商A",
                    "items": []
                }""";

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createInboundOrder_shouldFailWhenQuantityZero() throws Exception {
        String body = """
                {
                    "supplierName": "供应商A",
                    "items": [
                        { "productId": 1, "quantity": 0, "locationCode": "WH-A-01-01" }
                    ]
                }""";

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createInboundOrder_shouldReturnErrorWhenServiceThrows() throws Exception {
        when(inventoryService.createInboundOrder(any()))
                .thenThrow(new BusinessException(404, "商品不存在: id=99"));

        String body = """
                {
                    "supplierName": "供应商A",
                    "items": [
                        { "productId": 99, "quantity": 10, "locationCode": "WH-A-01-01" }
                    ]
                }""";

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("商品不存在: id=99"));
    }

    @Test
    void listInboundOrders_shouldReturnPagedResults() throws Exception {
        InboundOrderListResponse item = InboundOrderListResponse.builder()
                .id(1L)
                .orderNo("IN-20260511-001")
                .supplierName("供应商A")
                .status("COMPLETED")
                .itemCount(3)
                .createdAt(LocalDateTime.now())
                .build();

        PageResult<InboundOrderListResponse> pageResult = new PageResult<>(
                List.of(item), 1, 1, 20);

        when(inventoryService.queryInboundOrders(1, 20)).thenReturn(pageResult);

        mockMvc.perform(get("/api/inbound-orders")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("IN-20260511-001"))
                .andExpect(jsonPath("$.data.list[0].itemCount").value(3))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void getInboundOrder_shouldReturnOrderDetail() throws Exception {
        InboundOrderResponse response = InboundOrderResponse.builder()
                .id(1L)
                .orderNo("IN-20260511-001")
                .supplierName("供应商A")
                .status("COMPLETED")
                .items(List.of(InboundOrderResponse.InboundItemResponse.builder()
                        .productId(1L).productName("蓝牙耳机 Pro")
                        .quantity(100).locationCode("WH-A-01-01").build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(inventoryService.getInboundOrderById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/inbound-orders/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("蓝牙耳机 Pro"));
    }

    @Test
    void getInboundOrder_shouldReturnErrorWhenNotFound() throws Exception {
        when(inventoryService.getInboundOrderById(999L))
                .thenThrow(new BusinessException(404, "入库单不存在: id=999"));

        mockMvc.perform(get("/api/inbound-orders/{id}", 999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("入库单不存在: id=999"));
    }
}
