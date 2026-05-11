package com.wms.repository;

import com.wms.entity.InboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItem, Long> {
    List<InboundOrderItem> findByOrderId(Long orderId);
}
