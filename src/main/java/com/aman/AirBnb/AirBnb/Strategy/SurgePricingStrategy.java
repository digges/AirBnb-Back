package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

/**
 * ✅ Surge Pricing Strategy (Decorator)
 * Applies surge factor if inventory has high occupancy
 * Uses the surgeFactor from inventory entity
 */
@Slf4j
public class SurgePricingStrategy implements PricingStrategy {

    private final PricingStrategy decoratedStrategy;

    public SurgePricingStrategy(PricingStrategy decoratedStrategy) {
        this.decoratedStrategy = decoratedStrategy;
    }

    @Override
    public BigDecimal calculatePrice(InventoryEntity inventory) {
        // Get price from previous strategy
        BigDecimal price = decoratedStrategy.calculatePrice(inventory);

        // Apply surge pricing if surge factor exists and is greater than 1
        if (inventory.getSurgeFactor() != null &&
                inventory.getSurgeFactor().compareTo(BigDecimal.ONE) > 0) {
            BigDecimal surgeFactor = inventory.getSurgeFactor();
            BigDecimal surgePrice = price.multiply(surgeFactor);

            log.info("Surge pricing applied. Factor: {}, Original: {}, Surged: {}",
                    surgeFactor, price, surgePrice);

            return surgePrice; // ✅ was incorrectly returning `price`
        }

        return price; // ✅ missing return for the non-surge path
    }
}