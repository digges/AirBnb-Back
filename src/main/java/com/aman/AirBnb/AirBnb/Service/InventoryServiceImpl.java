package com.aman.AirBnb.AirBnb.Service;

import com.aman.AirBnb.AirBnb.Dto.*;
import com.aman.AirBnb.AirBnb.Entities.HotelEntity;
import com.aman.AirBnb.AirBnb.Entities.HotelMinPriceEntity;
import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import com.aman.AirBnb.AirBnb.Entities.RoomEntity;
import com.aman.AirBnb.AirBnb.Entities.UserEntity;
import com.aman.AirBnb.AirBnb.Exceptions.ResourceNotFoundException;
import com.aman.AirBnb.AirBnb.Repositories.HotelMinPriceRepository;
import com.aman.AirBnb.AirBnb.Repositories.InventoryRepository;
import com.aman.AirBnb.AirBnb.Repositories.RoomRepository;
import com.aman.AirBnb.AirBnb.Service.Interfaces.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aman.AirBnb.AirBnb.Utils.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ModelMapper modelMapper;
    private final HotelMinPriceRepository hotelMinPriceRepository; // ✅ NEW
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public void initializeRoomForAYear(RoomEntity room) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusYears(1);

        for (LocalDate date = today; !date.isAfter(endDate); date = date.plusDays(1)) {

            // ✅ Fix: copy to effectively final variable for use in lambda
            final LocalDate finalDate = date;

            InventoryEntity inventory = InventoryEntity.builder()
                    .hotel(room.getHotel())
                    .room(room)
                    .bookCount(0)
                    .reservedCount(0)
                    .city(room.getHotel().getCity())
                    .date(finalDate)
                    .price(room.getBasePrice())
                    .surgeFactor(BigDecimal.ONE)
                    .totalCount(room.getTotalCount())
                    .closed(false)
                    .build();
            inventoryRepository.save(inventory);

            HotelMinPriceEntity minPrice = hotelMinPriceRepository
                    .findByHotelAndDate(room.getHotel(), finalDate)
                    .orElseGet(() -> {
                        HotelMinPriceEntity newEntry = new HotelMinPriceEntity(room.getHotel(), finalDate);
                        newEntry.setPrice(room.getBasePrice());
                        return newEntry;
                    });

            if (room.getBasePrice().compareTo(minPrice.getPrice()) < 0) {
                minPrice.setPrice(room.getBasePrice());
            }

            hotelMinPriceRepository.save(minPrice);
        }
    }

    @Override
    public void deleteAllInventories(RoomEntity room) {
        log.info("Deleting the inventories of room with id: {}", room.getId());
        inventoryRepository.deleteByRoom(room);
    }

    @Override
    public Page<HotelPriceDTO> searchHotels(HotelSearchRequest hotelSearchRequest) {
        log.info("Searching hotels for {} city, from {} to {}",
                hotelSearchRequest.getCity(),
                hotelSearchRequest.getStartDate(),
                hotelSearchRequest.getEndDate());

        Pageable pageable = PageRequest.of(hotelSearchRequest.getPage(), hotelSearchRequest.getSize());
        long dateCount = ChronoUnit.DAYS.between(
                hotelSearchRequest.getStartDate(),
                hotelSearchRequest.getEndDate()) + 1;

        Page<HotelPriceDTO> hotelPage = hotelMinPriceRepository.findHotelsWithAvailableInventory(
                hotelSearchRequest.getCity(),
                hotelSearchRequest.getStartDate(),
                hotelSearchRequest.getEndDate(),
                hotelSearchRequest.getRoomsCount(),
                dateCount,
                pageable);

        return hotelPage.map((element) -> modelMapper.map(element, HotelPriceDTO.class));
    }

    @Override
    public List<InventoryDTO> getAllInventoryByRoom(Long roomId) {
        log.info("Getting All inventory by room for room with id: {}", roomId);
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

        UserEntity user = getCurrentUser();
        if (!user.equals(room.getHotel().getOwner()))
            throw new AccessDeniedException("You are not the owner of room with id: " + roomId);

        return inventoryRepository.findByRoomOrderByDate(room).stream()
                .map((element) -> modelMapper.map(element, InventoryDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateInventory(Long roomId, UpdateInventoryRequestDTO updateInventoryRequestDto) {
        log.info("Updating inventory for room with id: {} between: {} - {}",
                roomId,
                updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate());

        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

        UserEntity user = getCurrentUser();
        if (!user.equals(room.getHotel().getOwner()))
            throw new AccessDeniedException("You are not the owner of room with id: " + roomId);

        LocalDate startDate = updateInventoryRequestDto.getStartDate();
        LocalDate endDate = updateInventoryRequestDto.getEndDate();

        // ✅ Fix: if editing a single day, endDate == startDate, so bump endDate by 1
        // because query uses date < endDate (exclusive)
        if (!endDate.isAfter(startDate)) {
            endDate = startDate.plusDays(1);
        }

        // ✅ Fix: null-safe defaults
        boolean closed = updateInventoryRequestDto.getClosed() != null
                ? updateInventoryRequestDto.getClosed()
                : false;

        BigDecimal surgeFactor = updateInventoryRequestDto.getSurgeFactor() != null
                ? updateInventoryRequestDto.getSurgeFactor()
                : BigDecimal.ONE;

        inventoryRepository.getInventoryAndLockBeforeUpdate(roomId, startDate, endDate);
        inventoryRepository.updateInventory(roomId, startDate, endDate, closed, surgeFactor);
    }
    @Override
    public Map<Long, BigDecimal> getRoomPricing(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        List<Object[]> results = inventoryRepository.findRoomPricingForHotel(hotelId, checkIn, checkOut);
        Map<Long, BigDecimal> pricing = new HashMap<>();
        for (Object[] row : results) {
            Long roomId = ((Number) row[0]).longValue();
            // ✅ FIXED: AVG returns Double, convert to BigDecimal
            BigDecimal avgPrice = BigDecimal.valueOf(((Number) row[1]).doubleValue());
            pricing.put(roomId, avgPrice);
        }
        return pricing;
    }
    @Override
    @Transactional
    public void updateRoomPrice(RoomEntity room) {
        log.info("Updating inventory prices for room: {}", room.getId());

        // Update all future inventory prices for this room
        inventoryRepository.updateBasePrice(room.getId(),
                LocalDate.now(), room.getBasePrice());

        // Update hotel_min_price table
        hotelMinPriceRepository.findByHotel(room.getHotel())
                .forEach(minPrice -> {
                    minPrice.setPrice(room.getBasePrice());
                    hotelMinPriceRepository.save(minPrice);
                });
    }
}