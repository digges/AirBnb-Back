package com.aman.AirBnb.AirBnb.Service;

import com.aman.AirBnb.AirBnb.Entities.HotelEntity;
import com.aman.AirBnb.AirBnb.Entities.HotelMinPriceEntity;
import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import com.aman.AirBnb.AirBnb.Repositories.HotelMinPriceRepository;
import com.aman.AirBnb.AirBnb.Repositories.HotelRepository;
import com.aman.AirBnb.AirBnb.Repositories.InventoryRepository;
import com.aman.AirBnb.AirBnb.Strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PricingUpdateService {

    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final PricingService pricingService;

    /**
     * ✅ Scheduled task to update prices every hour
     * Updates dynamic pricing for all hotels and inventory
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void updatePrices() {
        log.info("Starting price update scheduler");
        int page = 0;
        int batchSize = 100;

        while (true) {
            Page<HotelEntity> hotelPage = hotelRepository.findAll(PageRequest.of(page, batchSize));
            if (hotelPage.isEmpty()) {
                log.info("Price update completed for all hotels");
                break;
            }
            hotelPage.getContent().forEach(this::updateHotelPrices);
            page++;
        }
    }

    /**
     * ✅ Update prices for a single hotel
     */
    private void updateHotelPrices(HotelEntity hotel) {
        try {
            log.info("Updating hotel prices for hotel ID: {}", hotel.getId());
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = LocalDate.now().plusYears(1);

            List<InventoryEntity> inventoryList = inventoryRepository.findByHotelAndDateBetween(hotel, startDate, endDate);

            if (inventoryList.isEmpty()) {
                log.warn("No inventory found for hotel ID: {}", hotel.getId());
                return;
            }

            updateInventoryPrices(inventoryList);
            updateHotelMinPrice(hotel, inventoryList);

            log.info("Successfully updated {} inventory items for hotel ID: {}", inventoryList.size(), hotel.getId());
        } catch (Exception e) {
            log.error("Error updating prices for hotel ID: {}", hotel.getId(), e);
        }
    }

    /**
     * ✅ Update HotelMinPrice table with minimum price per day
     */
    private void updateHotelMinPrice(HotelEntity hotel, List<InventoryEntity> inventoryList) {
        try {
            // Compute minimum price per day for the hotel
            Map<LocalDate, BigDecimal> dailyMinPrices = inventoryList.stream()
                    .collect(Collectors.groupingBy(
                            InventoryEntity::getDate,
                            Collectors.mapping(InventoryEntity::getPrice, Collectors.minBy(Comparator.naturalOrder()))
                    ))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElse(BigDecimal.ZERO)));

            // Prepare HotelMinPrice entities in bulk
            List<HotelMinPriceEntity> hotelPrices = new ArrayList<>();
            dailyMinPrices.forEach((date, price) -> {
                HotelMinPriceEntity hotelPrice = hotelMinPriceRepository.findByHotelAndDate(hotel, date)
                        .orElse(new HotelMinPriceEntity(hotel, date));
                hotelPrice.setPrice(price);
                hotelPrices.add(hotelPrice);
            });

            // Save all HotelMinPrice entities in bulk
            hotelMinPriceRepository.saveAll(hotelPrices);
            log.info("Updated {} hotel min prices for hotel ID: {}", hotelPrices.size(), hotel.getId());
        } catch (Exception e) {
            log.error("Error updating hotel min prices for hotel ID: {}", hotel.getId(), e);
        }
    }

    /**
     * ✅ Update inventory prices with dynamic pricing
     * Applies all pricing strategies (surge, occupancy, urgency, holiday)
     */
    private void updateInventoryPrices(List<InventoryEntity> inventoryList) {
        try {
            inventoryList.forEach(inventory -> {
                try {
                    BigDecimal dynamicPrice = pricingService.calculateDynamicPricing(inventory);
                    inventory.setPrice(dynamicPrice);
                    log.debug("Updated price for inventory date: {}, new price: ₹{}",
                            inventory.getDate(), dynamicPrice);
                } catch (Exception e) {
                    log.error("Error calculating price for inventory date: {}", inventory.getDate(), e);
                }
            });
            inventoryRepository.saveAll(inventoryList);
            log.info("Updated prices for {} inventory items", inventoryList.size());
        } catch (Exception e) {
            log.error("Error updating inventory prices", e);
        }
    }
}