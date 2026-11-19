package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

/**
 * ✅ Holiday Pricing Strategy (Decorator)
 * Increases price during peak seasons and holidays
 * Peak seasons: May-September (summer), December-January (winter holidays)
 */
@Slf4j
public class HolidayPricingStrategy implements PricingStrategy {

    private final PricingStrategy decoratedStrategy;

    public HolidayPricingStrategy(PricingStrategy decoratedStrategy) {
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
            double holidayMultiplier = calculateHolidayMultiplier(inventory.getDate());

            if (Math.abs(holidayMultiplier - 1.0) > 0.01) { // Not equal to 1.0
                BigDecimal holidayPrice = price.multiply(BigDecimal.valueOf(holidayMultiplier));
                log.debug("HolidayPricingStrategy - Date: {}, Multiplier: {}x, Price: ₹{} → ₹{}",
                        inventory.getDate(), holidayMultiplier, price, holidayPrice);
                return holidayPrice;
            }
        } catch (Exception e) {
            log.error("Error calculating holiday pricing for date: {}", inventory.getDate(), e);
        }

        return price;
    }

    /**
     * Calculate holiday multiplier based on season/month
     * Peak Season (May-September): 1.30x (30% premium) - summer travel
     * Holiday Season (December): 1.40x (40% premium) - year-end holidays
     * New Year (January): 1.35x (35% premium) - new year celebrations
     * Off-Season (October, February-April): 0.95x (5% discount)
     * Normal Season (November): 1.0x (no adjustment)
     */
    private double calculateHolidayMultiplier(LocalDate date) {
        Month month = date.getMonth();

        // Peak summer season
        if (month.getValue() >= 5 && month.getValue() <= 9) { // May-September
            return 1.30;
        }

        // December holidays
        if (month == Month.DECEMBER) {
            return 1.40;
        }

        // January new year
        if (month == Month.JANUARY) {
            return 1.35;
        }

        // October off-season
        if (month == Month.OCTOBER) {
            return 0.95;
        }

        // Feb-April off-season
        if (month.getValue() >= 2 && month.getValue() <= 4) {
            return 0.95;
        }

        // November - normal
        return 1.0;
    }
}