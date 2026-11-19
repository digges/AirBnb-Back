package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * ✅ Urgency Pricing Strategy (Decorator)
 * Increases price as the date gets closer (last-minute booking premium)
 * Used to incentivize early bookings
 */
@Slf4j
public class UrgencyPricingStrategy implements PricingStrategy {

    private final PricingStrategy decoratedStrategy;

    public UrgencyPricingStrategy(PricingStrategy decoratedStrategy) {
        this.decoratedStrategy = decoratedStrategy;
    }

    @Override
    public BigDecimal calculatePrice(InventoryEntity inventory) {
        // Get price from previous strategy
        BigDecimal price = decoratedStrategy.calculatePrice(inventory);

        if (inventory.getDate() == null) {
            return price;
        }

        try {
            // Calculate days until the date
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), inventory.getDate());

            if (daysUntil <= 0) {
                // Date is in the past, no urgency pricing
                return price;
            }

            double urgencyMultiplier = calculateUrgencyMultiplier(daysUntil);

            if (Math.abs(urgencyMultiplier - 1.0) > 0.01) { // Not equal to 1.0
                BigDecimal urgencyPrice = price.multiply(BigDecimal.valueOf(urgencyMultiplier));
                log.debug("UrgencyPricingStrategy - Date: {}, Days Until: {}, Multiplier: {}x, Price: ₹{} → ₹{}",
                        inventory.getDate(), daysUntil, urgencyMultiplier, price, urgencyPrice);
                return urgencyPrice;
            }
        } catch (Exception e) {
            log.error("Error calculating urgency pricing for date: {}", inventory.getDate(), e);
        }

        return price;
    }

    /**
     * Calculate urgency multiplier based on days until date
     * 90+ days: 0.90x (10% discount - early booking incentive)
     * 30-90 days: 1.0x (no adjustment)
     * 14-30 days: 1.10x (10% premium - getting closer)
     * 7-14 days: 1.20x (20% premium - last minute)
     * 0-7 days: 1.40x (40% premium - very last minute)
     */
    private double calculateUrgencyMultiplier(long daysUntil) {
        if (daysUntil >= 90) {
            return 0.90; // Early booking discount
        } else if (daysUntil >= 30) {
            return 1.0; // Normal price
        } else if (daysUntil >= 14) {
            return 1.10; // 10% premium
        } else if (daysUntil >= 7) {
            return 1.20; // 20% premium
        } else {
            return 1.40; // 40% premium - very last minute
        }
    }
}