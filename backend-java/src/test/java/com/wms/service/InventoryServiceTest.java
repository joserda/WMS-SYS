package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderListResponse;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.OutboundOrderCreateRequest;
import com.wms.dto.OutboundItemRequest;
import com.wms.dto.OutboundOrderResponse;
import com.wms.entity.InboundOrder;
import com.wms.entity.InboundOrderItem;
import com.wms.entity.Location;
import com.wms.entity.OutboundOrder;
import com.wms.entity.OutboundOrderItem;
import com.wms.entity.Product;
import com.wms.repository.InboundOrderItemRepository;
import com.wms.repository.InboundOrderRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.OutboundOrderItemRepository;
import com.wms.repository.OutboundOrderRepository;
import com.wms.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InboundOrderRepository inboundOrderRepository;

    @Mock
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Mock
    private OutboundOrderRepository outboundOrderRepository;

    @Mock
    private OutboundOrderItemRepository outboundOrderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        product1 = Product.builder().id(1L).name("蓝牙耳机 Pro").sku("SKU-001").unit("个").build();
        product2 = Product.builder().id(2L).name("Type-C 数据线").sku("SKU-002").unit("条").build();
    }

    @Test
    void createInboundOrder_shouldSucceed() {
        InboundItemRequest item1 = new InboundItemRequest();
        item1.setProductId(1L);
        item1.setQuantity(100);
        item1.setLocationCode("WH-A-01-01");

        InboundItemRequest item2 = new InboundItemRequest();
        item2.setProductId(2L);
        item2.setQuantity(50);
        item2.setLocationCode("WH-A-01-02");

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("供应商A");
        List<InboundItemRequest> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        request.setItems(items);

        when(inboundOrderRepository.countTodayOrders(any(), any())).thenReturn(0L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
        when(locationRepository.existsByCode("WH-A-01-01")).thenReturn(true);
        when(locationRepository.existsByCode("WH-A-01-02")).thenReturn(true);

        InboundOrder savedOrder = InboundOrder.builder()
                .id(1L)
                .orderNo("IN-20260511-001")
                .supplierName("供应商A")
                .status("COMPLETED")
                .build();
        savedOrder.setCreatedAt(LocalDateTime.now());
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(savedOrder);
        when(inboundOrderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        assertThat(response.getOrderNo()).startsWith("IN-");
        assertThat(response.getOrderNo()).contains("-001");
        assertThat(response.getSupplierName()).isEqualTo("供应商A");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("蓝牙耳机 Pro");
        assertThat(response.getItems().get(1).getProductName()).isEqualTo("Type-C 数据线");

        verify(inboundOrderRepository).save(any(InboundOrder.class));
        verify(inboundOrderItemRepository).saveAll(anyList());
        verify(inventoryRepository).upsertQuantity(1L, "WH-A-01-01", 100);
        verify(inventoryRepository).upsertQuantity(2L, "WH-A-01-02", 50);
    }

    @Test
    void createInboundOrder_shouldFailWhenProductNotFound() {
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(99L);
        item.setQuantity(10);
        item.setLocationCode("WH-A-01-01");

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("供应商B");
        request.setItems(List.of(item));

        when(inboundOrderRepository.countTodayOrders(any(), any())).thenReturn(0L);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.createInboundOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");

        verify(inboundOrderRepository, never()).save(any());
        verify(inventoryRepository, never()).upsertQuantity(anyLong(), anyString(), anyInt());
    }

    @Test
    void createInboundOrder_shouldFailWhenLocationNotFound() {
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(1L);
        item.setQuantity(10);
        item.setLocationCode("INVALID-LOC");

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("供应商C");
        request.setItems(List.of(item));

        when(inboundOrderRepository.countTodayOrders(any(), any())).thenReturn(0L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(locationRepository.existsByCode("INVALID-LOC")).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.createInboundOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库位不存在");

        verify(inboundOrderRepository, never()).save(any());
        verify(inventoryRepository, never()).upsertQuantity(anyLong(), anyString(), anyInt());
    }

    @Test
    void createInboundOrder_shouldGenerateSequentialOrderNo() {
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(1L);
        item.setQuantity(1);
        item.setLocationCode("WH-A-01-01");

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("供应商D");
        request.setItems(List.of(item));

        when(inboundOrderRepository.countTodayOrders(any(), any())).thenReturn(5L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(locationRepository.existsByCode("WH-A-01-01")).thenReturn(true);

        InboundOrder savedOrder = InboundOrder.builder()
                .id(1L)
                .orderNo("IN-20260511-006")
                .supplierName("供应商D")
                .status("COMPLETED")
                .build();
        savedOrder.setCreatedAt(LocalDateTime.now());
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(savedOrder);
        when(inboundOrderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        assertThat(response.getOrderNo()).endsWith("-006");
    }

    @Test
    void queryInboundOrders_shouldReturnPagedResults() {
        InboundOrder order1 = InboundOrder.builder()
                .id(1L).orderNo("IN-20260511-001")
                .supplierName("供应商A").status("COMPLETED").build();
        order1.setCreatedAt(LocalDateTime.now());
        InboundOrder order2 = InboundOrder.builder()
                .id(2L).orderNo("IN-20260511-002")
                .supplierName("供应商B").status("COMPLETED").build();
        order2.setCreatedAt(LocalDateTime.now());

        Page<InboundOrder> page = new PageImpl<>(List.of(order1, order2));

        when(inboundOrderRepository.search(any(), (PageRequest) any())).thenReturn(page);
        when(inboundOrderItemRepository.findByOrderId(1L)).thenReturn(List.of(new InboundOrderItem(), new InboundOrderItem(), new InboundOrderItem()));
        when(inboundOrderItemRepository.findByOrderId(2L)).thenReturn(List.of(new InboundOrderItem()));

        var result = inventoryService.queryInboundOrders(null, 1, 20);

        assertThat(result.getList()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getList().get(0).getItemCount()).isEqualTo(3);
        assertThat(result.getList().get(1).getItemCount()).isEqualTo(1);
    }

    @Test
    void getInboundOrderById_shouldReturnOrderWithItems() {
        InboundOrder order = InboundOrder.builder()
                .id(1L).orderNo("IN-20260511-001")
                .supplierName("供应商A").status("COMPLETED").build();
        order.setCreatedAt(LocalDateTime.now());

        InboundOrderItem orderItem = InboundOrderItem.builder()
                .id(1L).orderId(1L).productId(1L)
                .quantity(100).locationCode("WH-A-01-01").build();

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(inboundOrderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));

        InboundOrderResponse response = inventoryService.getInboundOrderById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOrderNo()).isEqualTo("IN-20260511-001");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("蓝牙耳机 Pro");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(100);
        assertThat(response.getItems().get(0).getLocationCode()).isEqualTo("WH-A-01-01");
    }

    @Test
    void getInboundOrderById_shouldFailWhenNotFound() {
        when(inboundOrderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInboundOrderById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入库单不存在");
    }

    @Test
    void createOutboundOrder_shouldSucceed() {
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(1L);
        item.setQuantity(10);
        item.setLocationCode("WH-A-01-01");

        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("客户X");
        request.setItems(List.of(item));

        when(outboundOrderRepository.countTodayOrders(any(), any())).thenReturn(0L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(locationRepository.existsByCode("WH-A-01-01")).thenReturn(true);

        OutboundOrder savedOrder = OutboundOrder.builder()
                .id(1L).orderNo("OUT-20260511-001")
                .customerName("客户X").status("COMPLETED").build();
        savedOrder.setCreatedAt(LocalDateTime.now());
        when(outboundOrderRepository.save(any(OutboundOrder.class))).thenReturn(savedOrder);
        when(outboundOrderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(inventoryRepository.deductQuantity(1L, "WH-A-01-01", 10)).thenReturn(1);

        OutboundOrderResponse response = inventoryService.createOutboundOrder(request);

        assertThat(response.getOrderNo()).startsWith("OUT-");
        assertThat(response.getCustomerName()).isEqualTo("客户X");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("蓝牙耳机 Pro");

        verify(outboundOrderRepository).save(any(OutboundOrder.class));
        verify(outboundOrderItemRepository).saveAll(anyList());
        verify(inventoryRepository).deductQuantity(1L, "WH-A-01-01", 10);
    }

    @Test
    void createOutboundOrder_shouldFailWhenInsufficientStock() {
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(1L);
        item.setQuantity(999);
        item.setLocationCode("WH-A-01-01");

        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("客户Y");
        request.setItems(List.of(item));

        when(outboundOrderRepository.countTodayOrders(any(), any())).thenReturn(0L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(locationRepository.existsByCode("WH-A-01-01")).thenReturn(true);

        OutboundOrder savedOrder = OutboundOrder.builder()
                .id(1L).orderNo("OUT-20260511-001")
                .customerName("客户Y").status("COMPLETED").build();
        savedOrder.setCreatedAt(LocalDateTime.now());
        when(outboundOrderRepository.save(any(OutboundOrder.class))).thenReturn(savedOrder);
        when(outboundOrderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(inventoryRepository.deductQuantity(1L, "WH-A-01-01", 999)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.createOutboundOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");

        verify(inventoryRepository).deductQuantity(1L, "WH-A-01-01", 999);
    }

    @Test
    void createOutboundOrder_shouldFailWhenProductNotFound() {
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(99L);
        item.setQuantity(10);
        item.setLocationCode("WH-A-01-01");

        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("客户Z");
        request.setItems(List.of(item));

        when(outboundOrderRepository.countTodayOrders(any(), any())).thenReturn(0L);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.createOutboundOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");

        verify(outboundOrderRepository, never()).save(any());
        verify(inventoryRepository, never()).deductQuantity(anyLong(), anyString(), anyInt());
    }
}
