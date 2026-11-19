package com.aman.AirBnb.AirBnb.Service;

import com.aman.AirBnb.AirBnb.Dto.BookingDTO;
import com.aman.AirBnb.AirBnb.Dto.BookingRequest;
import com.aman.AirBnb.AirBnb.Dto.GuestDTO;
import com.aman.AirBnb.AirBnb.Dto.HotelReportDTO;
import com.aman.AirBnb.AirBnb.Entities.*;
import com.aman.AirBnb.AirBnb.Enums.BookingStatus;
import com.aman.AirBnb.AirBnb.Exceptions.ResourceNotFoundException;
import com.aman.AirBnb.AirBnb.Exceptions.UnAuthorisedException;
import com.aman.AirBnb.AirBnb.Repositories.*;
import com.aman.AirBnb.AirBnb.Service.Interfaces.BookingService;
import com.aman.AirBnb.AirBnb.Service.Interfaces.CheckoutService;
import com.aman.AirBnb.AirBnb.Strategy.PricingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static com.aman.AirBnb.AirBnb.Utils.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public BookingDTO initialiseBooking(BookingRequest bookingRequest) {
        log.info("Initialising booking for hotel: {}, room: {}, date {}-{}",
                bookingRequest.getHotelId(), bookingRequest.getRoomId(),
                bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        HotelEntity hotel = hotelRepository.findById(bookingRequest.getHotelId()).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with id: " + bookingRequest.getHotelId()));

        RoomEntity room = roomRepository.findById(bookingRequest.getRoomId()).orElseThrow(() ->
                new ResourceNotFoundException("Room not found with id: " + bookingRequest.getRoomId()));

        List<InventoryEntity> inventoryList = inventoryRepository.findAndLockAvailableInventory(
                room.getId(),
                bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(),
                bookingRequest.getRoomsCount());

        // ✅ FIXED: removed +1, checkout date is not a stay night
        long daysCount = ChronoUnit.DAYS.between(
                bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate());

        if (inventoryList.size() != daysCount) {
            throw new IllegalStateException("Room is not available anymore");
        }

        inventoryRepository.initBooking(
                room.getId(),
                bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(),
                bookingRequest.getRoomsCount());

        BigDecimal priceForOneRoom = pricingService.calculateTotalPrice(inventoryList);
        BigDecimal totalPrice = priceForOneRoom.multiply(BigDecimal.valueOf(bookingRequest.getRoomsCount()));

        BookingEntity booking = BookingEntity.builder()
                .bookingStatus(BookingStatus.RESERVED)
                .hotel(hotel)
                .room(room)
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .user(getCurrentUser())
                .roomsCount(bookingRequest.getRoomsCount())
                .amount(totalPrice)
                .build();

        booking = bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDTO.class);
    }

    @Override
    @Transactional
    public BookingDTO addGuests(Long bookingId, List<GuestDTO> guestDtoList) {
        log.info("Adding guests for booking with id: {}", bookingId);

        BookingEntity booking = bookingRepository.findById(bookingId).orElseThrow(() ->
                new ResourceNotFoundException("Booking not found with id: " + bookingId));

        UserEntity user = getCurrentUser();

        if (!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: " + user.getId());
        }

        if (hasBookingExpired(booking)) {
            throw new IllegalStateException("Booking has already expired");
        }

        if (booking.getBookingStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException("Booking is not under reserved state, cannot add guests");
        }

        for (GuestDTO guestDto : guestDtoList) {
            GuestEntity guest = modelMapper.map(guestDto, GuestEntity.class);
            guest.setUser(getCurrentUser());
            guest = guestRepository.save(guest);
            booking.getGuests().add(guest);
        }

        booking.setBookingStatus(BookingStatus.GUESTS_ADDED);
        booking = bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDTO.class);
    }

    @Override
    @Transactional
    public String initiatePayments(Long bookingId) {
        BookingEntity booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        UserEntity user = getCurrentUser();

        if (!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: " + user.getId());
        }

        if (hasBookingExpired(booking)) {
            throw new IllegalStateException("Booking has already expired");
        }

        String sessionUrl = checkoutService.getCheckoutSession(booking,
                frontendUrl + "?session_id={CHECKOUT_SESSION_ID}&booking_id=" + bookingId,
                frontendUrl + "?canceled=true&booking_id=" + bookingId);

        booking.setBookingStatus(BookingStatus.PAYMENTS_PENDING);
        bookingRepository.save(booking);

        return sessionUrl;
    }

    @Override
    @Transactional
    public void capturePayment(Event event) {
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if (session == null) return;

            String sessionId = session.getId();
            BookingEntity booking = bookingRepository.findByPaymentSessionId(sessionId).orElseThrow(() ->
                    new ResourceNotFoundException("Booking not found for session ID: " + sessionId));

            booking.setBookingStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            inventoryRepository.findAndLockReservedInventory(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount());

            inventoryRepository.confirmBooking(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount());

            log.info("Successfully confirmed the booking for Booking ID: {}", booking.getId());
        } else {
            log.warn("Unhandled event type: {}", event.getType());
        }
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId) {
        BookingEntity booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        UserEntity user = getCurrentUser();

        if (!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: " + user.getId());
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            inventoryRepository.findAndLockReservedInventory(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount());

            inventoryRepository.cancelBooking(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount());

            try {
                Session session = Session.retrieve(booking.getPaymentSessionId());
                RefundCreateParams refundParams = RefundCreateParams.builder()
                        .setPaymentIntent(session.getPaymentIntent())
                        .build();
                Refund.create(refundParams);
            } catch (StripeException e) {
                throw new RuntimeException(e);
            }
        } else {
            inventoryRepository.findAndLockAvailableInventory(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount());

            inventoryRepository.cancelBooking(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount());
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("Booking with id: {} cancelled successfully", bookingId);
    }

    @Override
    @Transactional
    public BookingStatus getBookingStatus(Long bookingId) {
        BookingEntity booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        UserEntity user = getCurrentUser();

        if (!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: " + user.getId());
        }

        // Check Stripe directly if payment is pending
        if (booking.getBookingStatus() == BookingStatus.PAYMENTS_PENDING
                && booking.getPaymentSessionId() != null) {
            try {
                Session session = Session.retrieve(booking.getPaymentSessionId());
                if ("complete".equals(session.getStatus())) {
                    booking.setBookingStatus(BookingStatus.CONFIRMED);
                    bookingRepository.save(booking);

                    inventoryRepository.findAndLockReservedInventory(
                            booking.getRoom().getId(),
                            booking.getCheckInDate(),
                            booking.getCheckOutDate(),
                            booking.getRoomsCount());

                    inventoryRepository.confirmBooking(
                            booking.getRoom().getId(),
                            booking.getCheckInDate(),
                            booking.getCheckOutDate(),
                            booking.getRoomsCount());

                    log.info("Booking {} confirmed via status check", bookingId);
                }
            } catch (StripeException e) {
                log.error("Could not verify Stripe session for booking {}: {}", bookingId, e.getMessage());
            }
        }

        return booking.getBookingStatus();
    }

    @Override
    public List<BookingDTO> getAllBookingsByHotelId(Long hotelId) {
        HotelEntity hotel = hotelRepository.findById(hotelId).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        UserEntity user = getCurrentUser();

        if (!user.equals(hotel.getOwner()))
            throw new AccessDeniedException("You are not the owner of hotel with id: " + hotelId);

        log.info("Getting all bookings for hotel with ID: {}", hotelId);

        List<BookingEntity> bookings = bookingRepository.findByHotel(hotel);
        return bookings.stream()
                .map(element -> modelMapper.map(element, BookingDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public HotelReportDTO getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate) {
        HotelEntity hotel = hotelRepository.findById(hotelId).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        UserEntity user = getCurrentUser();

        if (!user.equals(hotel.getOwner()))
            throw new AccessDeniedException("You are not the owner of hotel with id: " + hotelId);

        log.info("Generating report for hotel with ID: {} from {} to {}", hotelId, startDate, endDate);

        List<BookingEntity> allBookings = bookingRepository.findByHotel(hotel);
        log.info("Total bookings found for hotel: {}", allBookings.size());

        List<BookingEntity> confirmedBookingsInRange = allBookings.stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                // ✅ FIXED: use <= endDate instead of < endDate
                .filter(booking -> !booking.getCheckInDate().isBefore(startDate)
                        && !booking.getCheckInDate().isAfter(endDate))
                .collect(Collectors.toList());

        log.info("CONFIRMED bookings in range: {}", confirmedBookingsInRange.size());

        confirmedBookingsInRange.forEach(b ->
                log.info("CONFIRMED booking: ID={}, checkIn={}, checkOut={}, amount={}, status={}",
                        b.getId(), b.getCheckInDate(), b.getCheckOutDate(),
                        b.getAmount(), b.getBookingStatus()));

        Long totalConfirmedBookings = (long) confirmedBookingsInRange.size();

        BigDecimal totalRevenueOfConfirmedBookings = confirmedBookingsInRange.stream()
                .map(BookingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgRevenue = totalConfirmedBookings == 0 ? BigDecimal.ZERO :
                totalRevenueOfConfirmedBookings.divide(
                        BigDecimal.valueOf(totalConfirmedBookings), RoundingMode.HALF_UP);

        log.info("Total revenue: {}, Average revenue: {}", totalRevenueOfConfirmedBookings, avgRevenue);

        return new HotelReportDTO(totalConfirmedBookings, totalRevenueOfConfirmedBookings, avgRevenue);
    }

    @Override
    public List<BookingDTO> getMyBookings() {
        UserEntity user = getCurrentUser();
        return bookingRepository.findByUser(user)
                .stream()
                .map(element -> modelMapper.map(element, BookingDTO.class))
                .collect(Collectors.toList());
    }

    public boolean hasBookingExpired(BookingEntity booking) {
        return booking.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now());
    }
}