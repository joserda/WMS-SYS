package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundOrderListResponse {
    private Long id;
    private String orderNo;
    private String supplierName;
    private String status;
    private int itemCount;
    private LocalDateTime createdAt;
}
