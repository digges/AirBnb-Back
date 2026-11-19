package com.aman.AirBnb.AirBnb.Service;

import com.aman.AirBnb.AirBnb.Dto.HotelDTO;
import com.aman.AirBnb.AirBnb.Dto.HotelInfoDto;
import com.aman.AirBnb.AirBnb.Dto.RoomDTO;
import com.aman.AirBnb.AirBnb.Entities.HotelEntity;
import com.aman.AirBnb.AirBnb.Entities.RoomEntity;
import com.aman.AirBnb.AirBnb.Entities.UserEntity;
import com.aman.AirBnb.AirBnb.Enums.BookingStatus;
import com.aman.AirBnb.AirBnb.Exceptions.ResourceNotFoundException;
import com.aman.AirBnb.AirBnb.Exceptions.RoomDeletionException;
import com.aman.AirBnb.AirBnb.Exceptions.UnAuthorisedException;
import com.aman.AirBnb.AirBnb.Repositories.BookingRepository;
import com.aman.AirBnb.AirBnb.Repositories.HotelRepository;
import com.aman.AirBnb.AirBnb.Repositories.RoomRepository;
import com.aman.AirBnb.AirBnb.Service.Interfaces.HotelService;
import com.aman.AirBnb.AirBnb.Service.Interfaces.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.aman.AirBnb.AirBnb.Utils.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final ModelMapper modelMapper;
    private final InventoryService inventoryService;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository; // ✅ NEW

    @Override
    public HotelDTO createNewHotel(HotelDTO hotelDto) {
        log.info("Creating a new hotel with name: {}", hotelDto.getName());
        HotelEntity hotel = modelMapper.map(hotelDto, HotelEntity.class);
        hotel.setActive(false);

        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        hotel.setOwner(user);

        if (hotelDto.getContactInfo() != null) hotel.setContactInfo(hotelDto.getContactInfo());

        hotel = hotelRepository.save(hotel);
        log.info("Created a new hotel with ID: {}", hotel.getId());
        return modelMapper.map(hotel, HotelDTO.class);
    }

    @Override
    public HotelDTO getHotelById(Long id) {
        log.info("Getting the hotel with ID: {}", id);
        HotelEntity hotel = hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + id));

        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!user.equals(hotel.getOwner())) {
            throw new UnAuthorisedException("This user does not own this hotel with id: " + id);
        }

        return modelMapper.map(hotel, HotelDTO.class);
    }

    @Override
    public HotelDTO updateHotelById(Long id, HotelDTO hotelDto) {
        log.info("Updating the hotel with ID: {}", id);
        HotelEntity hotel = hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + id));

        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!user.equals(hotel.getOwner())) {
            throw new UnAuthorisedException("This user does not own this hotel with id: " + id);
        }

        hotel.setName(hotelDto.getName());
        hotel.setCity(hotelDto.getCity());
        if (hotelDto.getAmenities() != null) hotel.setAmenities(hotelDto.getAmenities());
        if (hotelDto.getPhotos() != null) hotel.setPhotos(hotelDto.getPhotos());
        if (hotelDto.getContactInfo() != null) hotel.setContactInfo(hotelDto.getContactInfo());

        hotel.setId(id);
        hotel = hotelRepository.save(hotel);
        return modelMapper.map(hotel, HotelDTO.class);
    }

    @Override
    @Transactional
    public void deleteHotelById(Long id) {
        log.info("Soft deleting the hotel with ID: {}", id);
        HotelEntity hotel = hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + id));

        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!user.equals(hotel.getOwner())) {
            throw new UnAuthorisedException("This user does not own this hotel with id: " + id);
        }

        if (hotel.getActive()) {
            throw new RoomDeletionException(
                    "Cannot delete an active hotel. Please deactivate it first."
            );
        }

        // Block delete if any room has active bookings
        for (RoomEntity room : hotel.getRooms()) {
            boolean hasActiveBookings = bookingRepository.existsByRoomAndBookingStatusNotIn(
                    room,
                    List.of(BookingStatus.CANCELLED, BookingStatus.EXPIRED)
            );
            if (hasActiveBookings) {
                throw new RoomDeletionException(
                        "Cannot delete hotel — room #" + room.getId() +
                                " still has active bookings. Cancel all bookings first."
                );
            }
        }

        // Soft delete all rooms, then soft delete hotel
        // Hibernate fires UPDATE SET is_deleted=true for both — FK stays intact ✅
        for (RoomEntity room : hotel.getRooms()) {
            inventoryService.deleteAllInventories(room);
            roomRepository.delete(room);          // soft delete via @SQLDelete
        }
        hotelRepository.delete(hotel);            // soft delete via @SQLDelete
    }

    @Override
    @Transactional
    public void activateHotel(Long hotelId) {
        log.info("Activating the hotel with ID: {}", hotelId);
        HotelEntity hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!user.equals(hotel.getOwner())) {
            throw new UnAuthorisedException("This user does not own this hotel with id: " + hotelId);
        }

        hotel.setActive(true);
        for (RoomEntity room : hotel.getRooms()) {
            inventoryService.initializeRoomForAYear(room);
        }
    }
    @Override
    @Transactional
    public void deactivateHotel(Long hotelId) {
        log.info("Deactivating the hotel with ID: {}", hotelId);
        HotelEntity hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!user.equals(hotel.getOwner())) {
            throw new UnAuthorisedException("This user does not own this hotel with id: " + hotelId);
        }

        hotel.setActive(false);
        hotelRepository.save(hotel);
    }
    @Override
    public HotelInfoDto getHotelInfoById(Long hotelId) {
        HotelEntity hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        List<RoomDTO> rooms = hotel.getRooms()
                .stream()
                .map((element) -> modelMapper.map(element, RoomDTO.class))
                .toList();

        return new HotelInfoDto(modelMapper.map(hotel, HotelDTO.class), rooms);
    }

    @Override
    public List<HotelDTO> getAllHotels() {
        UserEntity user = getCurrentUser();
        log.info("Getting all hotels for the admin user with ID: {}", user.getId());
        List<HotelEntity> hotels = hotelRepository.findByOwner(user);

        return hotels
                .stream()
                .map((element) -> modelMapper.map(element, HotelDTO.class))
                .collect(Collectors.toList());
    }
}