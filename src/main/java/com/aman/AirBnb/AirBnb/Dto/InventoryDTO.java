package com.aman.AirBnb.AirBnb.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDTO {

    private Long id;
    private LocalDate date;
    private Integer totalCount;
    private Integer bookCount;
    private Integer reservedCount;
    private BigDecimal price;
    private BigDecimal surgeFactor;
    private String city;
    private Boolean closed;

    // ✅ Calculate available rooms
    public Integer getAvailableCount() {
        return totalCount - bookCount - reservedCount;
    }
}