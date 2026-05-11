package com.wms.repository;

import com.wms.entity.OutboundOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    @Query("SELECT COUNT(o) FROM OutboundOrder o WHERE o.createdAt >= :todayStart AND o.createdAt < :tomorrowStart")
    long countTodayOrders(@Param("todayStart") LocalDateTime todayStart,
                          @Param("tomorrowStart") LocalDateTime tomorrowStart);

    @Query("SELECT o FROM OutboundOrder o WHERE " +
           "(:keyword IS NULL OR o.orderNo LIKE %:keyword% OR o.customerName LIKE %:keyword%)")
    Page<OutboundOrder> search(@Param("keyword") String keyword, Pageable pageable);
}
