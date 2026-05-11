package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderListResponse;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InventoryResponse;
import com.wms.entity.InboundOrder;
import com.wms.entity.InboundOrderItem;
import com.wms.entity.Product;
import com.wms.repository.InboundOrderItemRepository;
import com.wms.repository.InboundOrderRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public InboundOrderResponse createInboundOrder(InboundOrderCreateRequest request) {
        String orderNo = generateOrderNo();

        List<Product> products = new ArrayList<>();
        for (InboundItemRequest item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new BusinessException(404, "商品不存在: id=" + item.getProductId()));
            products.add(product);
        }

        for (InboundItemRequest item : request.getItems()) {
            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new BusinessException(404, "库位不存在: " + item.getLocationCode());
            }
        }

        InboundOrder order = InboundOrder.builder()
                .orderNo(orderNo)
                .supplierName(request.getSupplierName())
                .status("COMPLETED")
                .build();
        order = inboundOrderRepository.save(order);

        List<InboundOrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            InboundItemRequest item = request.getItems().get(i);
            InboundOrderItem orderItem = InboundOrderItem.builder()
                    .orderId(order.getId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .locationCode(item.getLocationCode())
                    .build();
            orderItems.add(orderItem);
        }
        inboundOrderItemRepository.saveAll(orderItems);

        for (InboundItemRequest item : request.getItems()) {
            inventoryRepository.upsertQuantity(item.getProductId(), item.getLocationCode(), item.getQuantity());
        }

        List<InboundOrderResponse.InboundItemResponse> itemResponses = new ArrayList<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            InboundItemRequest item = request.getItems().get(i);
            Product product = products.get(i);
            itemResponses.add(InboundOrderResponse.InboundItemResponse.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(item.getQuantity())
                    .locationCode(item.getLocationCode())
                    .build());
        }

        log.info("入库单创建成功: orderNo={}, items={}", orderNo, request.getItems().size());
        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String generateOrderNo() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        long count = inboundOrderRepository.countTodayOrders(todayStart, tomorrowStart);
        String datePart = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("IN-%s-%03d", datePart, count + 1);
    }

    public PageResult<InboundOrderListResponse> queryInboundOrders(int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<InboundOrder> orderPage = inboundOrderRepository.findAll(pageRequest);

        List<InboundOrderListResponse> list = orderPage.getContent().stream()
                .map(order -> {
                    int itemCount = inboundOrderItemRepository.findByOrderId(order.getId()).size();
                    return InboundOrderListResponse.builder()
                            .id(order.getId())
                            .orderNo(order.getOrderNo())
                            .supplierName(order.getSupplierName())
                            .status(order.getStatus())
                            .itemCount(itemCount)
                            .createdAt(order.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return new PageResult<>(list, orderPage.getTotalElements(), page, pageSize);
    }

    public InboundOrderResponse getInboundOrderById(Long id) {
        InboundOrder order = inboundOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "入库单不存在: id=" + id));

        List<InboundOrderItem> orderItems = inboundOrderItemRepository.findByOrderId(id);

        List<InboundOrderResponse.InboundItemResponse> itemResponses = new ArrayList<>();
        for (InboundOrderItem item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElse(null);
            String productName = product != null ? product.getName() : "未知商品";
            itemResponses.add(InboundOrderResponse.InboundItemResponse.builder()
                    .productId(item.getProductId())
                    .productName(productName)
                    .quantity(item.getQuantity())
                    .locationCode(item.getLocationCode())
                    .build());
        }

        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    public PageResult<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                   int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by("updatedAt").descending());
        Page<InventoryResponse> result = inventoryRepository.search(keyword, warehouseId, pageRequest);
        return new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize);
    }
}
