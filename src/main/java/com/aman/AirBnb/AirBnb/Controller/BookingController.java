package com.aman.AirBnb.AirBnb.Controller;

import com.aman.AirBnb.AirBnb.Dto.BookingDTO;
import com.aman.AirBnb.AirBnb.Dto.BookingRequest;
import com.aman.AirBnb.AirBnb.Dto.GuestDTO;
import com.aman.AirBnb.AirBnb.Service.Interfaces.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // ✅ POST /api/v1/bookings/init
    @PostMapping("/init")
    public ResponseEntity<?> initialiseBooking(@RequestBody BookingRequest bookingRequest) {
        try {
            log.info("Initialising booking: {}", bookingRequest);
            BookingDTO booking = bookingService.initialiseBooking(bookingRequest);
            return ResponseEntity.ok(Map.of("data", booking));
        } catch (IllegalStateException e) {
            log.warn("Booking error: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Booking init error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ✅ POST /api/v1/bookings/{bookingId}/addGuests
    @PostMapping("/{bookingId}/addGuests")
    public ResponseEntity<?> addGuests(@PathVariable Long bookingId,
                                       @RequestBody List<GuestDTO> guestDTOs) {
        try {
            BookingDTO booking = bookingService.addGuests(bookingId, guestDTOs);
            return ResponseEntity.ok(Map.of("data", booking));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ✅ POST /api/v1/bookings/{bookingId}/cancel
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long bookingId) {
        try {
            bookingService.cancelBooking(bookingId);
            return ResponseEntity.ok(Map.of("message", "Booking cancelled successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ✅ GET /api/v1/bookings/{bookingId}/status
    @GetMapping("/{bookingId}/status")
    public ResponseEntity<?> getBookingStatus(@PathVariable Long bookingId) {
        try {
            var status = bookingService.getBookingStatus(bookingId);
            return ResponseEntity.ok(Map.of("data", status.toString()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}