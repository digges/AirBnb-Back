package com.aman.AirBnb.AirBnb.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookingPaymentInitResponseDTO {
    private String sessionUrl;
    private Long bookingId;
}
