package com.wms.repository;

import com.wms.entity.InboundOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    @Query("SELECT COUNT(o) FROM InboundOrder o WHERE o.createdAt >= :todayStart AND o.createdAt < :tomorrowStart")
    long countTodayOrders(@Param("todayStart") LocalDateTime todayStart,
                          @Param("tomorrowStart") LocalDateTime tomorrowStart);

    @Query("SELECT o FROM InboundOrder o WHERE " +
           "(:keyword IS NULL OR o.orderNo LIKE %:keyword% OR o.supplierName LIKE %:keyword%)")
    Page<InboundOrder> search(@Param("keyword") String keyword, Pageable pageable);
}
