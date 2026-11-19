package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
public class PricingService {

    /**
     * ✅ Calculate dynamic pricing using Strategy Pattern
     * Applies multiple pricing strategies in decorator pattern
     */
    public BigDecimal calculateDynamicPricing(InventoryEntity inventory) {
        if (inventory == null || inventory.getPrice() == null) {
            return BigDecimal.ZERO;
        }

        PricingStrategy pricingStrategy = new BasePricingStrategy();

        // Apply additional strategies in order (decorator pattern)
        pricingStrategy = new SurgePricingStrategy(pricingStrategy);
        pricingStrategy = new OccupancyPricingStrategy(pricingStrategy);
        pricingStrategy = new UrgencyPricingStrategy(pricingStrategy);
        pricingStrategy = new HolidayPricingStrategy(pricingStrategy);

        BigDecimal finalPrice = pricingStrategy.calculatePrice(inventory);
        log.info("Dynamic pricing calculated for date: {}, base: ₹{}, final: ₹{}",
                inventory.getDate(), inventory.getPrice(), finalPrice);

        return finalPrice;
    }

    /**
     * ✅ Calculate total price for a stay
     * ACCOUNTS FOR DATE-DEPENDENT PRICING (each day can have different price)
     */
    public BigDecimal calculateTotalPrice(List<InventoryEntity> inventoryList) {
        if (inventoryList == null || inventoryList.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalPrice = inventoryList.stream()
                .map(this::calculateDynamicPricing)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Total price for {} days: ₹{}", inventoryList.size(), totalPrice);
        return totalPrice;
    }

    /**
     * ✅ Calculate per-night price from total price and number of days
     */
    public BigDecimal calculatePerNightPrice(BigDecimal totalPrice, int numberOfDays) {
        if (numberOfDays <= 0) {
            return BigDecimal.ZERO;
        }
        return totalPrice.divide(BigDecimal.valueOf(numberOfDays), 2, RoundingMode.HALF_UP);
    }

    /**
     * ✅ Calculate number of nights between check-in and check-out
     */
    public long calculateNumberOfNights(LocalDate checkInDate, LocalDate checkOutDate) {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }

    /**
     * ✅ Get base price for a room type in a city
     * Used for search results to show starting price
     */
    public BigDecimal getStartingPrice(List<InventoryEntity> inventoryList) {
        if (inventoryList == null || inventoryList.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return inventoryList.stream()
                .map(InventoryEntity::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
}