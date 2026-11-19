package com.aman.AirBnb.AirBnb.Dto;

import com.aman.AirBnb.AirBnb.Entities.HotelEntity;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class HotelPriceDTO {
    private HotelDTO hotel;      // ✅ DTO for API response
    private Double price;

    // ✅ Constructor for JPQL "SELECT new HotelPriceDTO(i.hotel, AVG(i.price))"
    public HotelPriceDTO(HotelEntity hotelEntity, Double price) {
        this.hotel = mapToDto(hotelEntity);
        this.price = price;
    }

    private HotelDTO mapToDto(HotelEntity h) {
        HotelDTO dto = new HotelDTO();
        dto.setId(h.getId());
        dto.setName(h.getName());
        dto.setCity(h.getCity());
        dto.setActive(h.getActive());
        dto.setAmenities(h.getAmenities());
        dto.setContactInfo(h.getContactInfo());
        return dto;
    }
}