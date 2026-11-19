package com.aman.AirBnb.AirBnb.Controller;

import com.aman.AirBnb.AirBnb.Dto.BookingPaymentInitResponseDTO;
import com.aman.AirBnb.AirBnb.Service.Interfaces.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Handle booking payments")
public class PaymentController {

    private final BookingService bookingService;

    /**
     * Initiate payment for a booking
     * POST /api/v1/bookings/{bookingId}/payments
     */
    @PostMapping("/{bookingId}/payments")
    @Operation(summary = "Initiate payment for booking", tags = {"Payment"})
    public ResponseEntity<?> initiatePayment(@PathVariable Long bookingId) {
        try {
            log.info("Payment initiated for booking: {}", bookingId);

            // Call existing service method
            String sessionUrl = bookingService.initiatePayments(bookingId);

            // Return response using existing DTO
            BookingPaymentInitResponseDTO response = BookingPaymentInitResponseDTO.builder()
                    .sessionUrl(sessionUrl)
                    .bookingId(bookingId)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.warn("Booking error: {}", e.getMessage());
            return ResponseEntity.status(400).body(
                    Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Payment error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    Map.of("error", "Payment initiation failed: " + e.getMessage())
            );
        }
    }

    /**
     * Check payment/booking status
     * GET /api/v1/bookings/{bookingId}/payment/status
     */
    @GetMapping("/{bookingId}/payment/status")
    @Operation(summary = "Check booking status", tags = {"Payment"})
    public ResponseEntity<?> getPaymentStatus(@PathVariable Long bookingId) {
        try {
            log.info("Checking status for booking: {}", bookingId);
            var status = bookingService.getBookingStatus(bookingId);

            return ResponseEntity.ok(
                    Map.of(
                            "bookingId", bookingId,
                            "status", status.toString(),
                            "isConfirmed", status.toString().equals("CONFIRMED")
                    )
            );
        } catch (Exception e) {
            log.error("Status check error: {}", e.getMessage());
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }
}