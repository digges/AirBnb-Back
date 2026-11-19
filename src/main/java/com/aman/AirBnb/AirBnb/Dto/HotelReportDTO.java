package com.aman.AirBnb.AirBnb.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelReportDTO {

    // ✅ CORRECT field name to match service
    private Long totalConfirmedBookings;

    // ✅ Can also be called totalRevenueOfConfirmedBookings (same thing)
    private BigDecimal totalRevenue;

    // ✅ Average revenue per booking
    private BigDecimal avgRevenue;
}