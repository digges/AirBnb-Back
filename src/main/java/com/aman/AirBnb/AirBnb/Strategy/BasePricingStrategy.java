package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

/**
 * ✅ Base Pricing Strategy
 * Returns the base price from inventory (room price for the date)
 */
@Slf4j
public class BasePricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculatePrice(InventoryEntity inventory) {
        if (inventory == null || inventory.getPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal basePrice = inventory.getPrice();
        log.debug("BasePricingStrategy - Date: {}, Base Price: ₹{}",
                inventory.getDate(), basePrice);

        return basePrice;
    }
}