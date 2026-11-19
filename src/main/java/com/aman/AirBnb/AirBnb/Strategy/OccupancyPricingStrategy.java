package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

/**
 * ✅ Occupancy Pricing Strategy (Decorator)
 * Adjusts price based on current occupancy rate
 * Counts both booked AND reserved rooms for accurate occupancy
 */
@Slf4j
public class OccupancyPricingStrategy implements PricingStrategy {

    private final PricingStrategy decoratedStrategy;

    public OccupancyPricingStrategy(PricingStrategy decoratedStrategy) {
        this.decoratedStrategy = decoratedStrategy;
    }

    @Override
    public BigDecimal calculatePrice(InventoryEntity inventory) {
        BigDecimal price = decoratedStrategy.calculatePrice(inventory);

        Integer totalRooms = inventory.getTotalCount();

        if (totalRooms == null || totalRooms <= 0) {
            return price;
        }

        // ✅ FIXED: Count both booked and reserved rooms
        int bookedRooms = (inventory.getBookCount() == null ? 0 : inventory.getBookCount())
                + (inventory.getReservedCount() == null ? 0 : inventory.getReservedCount());

        double occupancyRate = (double) bookedRooms / totalRooms;
        double occupancyMultiplier = calculateOccupancyMultiplier(occupancyRate);

        if (Math.abs(occupancyMultiplier - 1.0) > 0.01) {
            BigDecimal adjustedPrice = price.multiply(BigDecimal.valueOf(occupancyMultiplier));
            log.debug("OccupancyPricingStrategy - Date: {}, Occupancy: {}/{} ({}%), Multiplier: {}x, Price: ₹{} → ₹{}",
                    inventory.getDate(), bookedRooms, totalRooms,
                    (int)(occupancyRate * 100), occupancyMultiplier, price, adjustedPrice);
            return adjustedPrice;
        }

        return price;
    }

    /**
     * 0-50%:   1.0x  (no change)
     * 50-75%:  1.15x (15% increase)
     * 75-90%:  1.30x (30% increase)
     * 90-100%: 1.50x (50% increase)
     */
    private double calculateOccupancyMultiplier(double occupancyRate) {
        if (occupancyRate < 0.5) {
            return 1.0;
        } else if (occupancyRate < 0.75) {
            return 1.15;
        } else if (occupancyRate < 0.9) {
            return 1.30;
        } else {
            return 1.50;
        }
    }
}