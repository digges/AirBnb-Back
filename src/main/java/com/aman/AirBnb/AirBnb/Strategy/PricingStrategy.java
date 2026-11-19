package com.aman.AirBnb.AirBnb.Strategy;

import com.aman.AirBnb.AirBnb.Entities.InventoryEntity;
import java.math.BigDecimal;

/**
 * ✅ Base interface for Pricing Strategy Pattern
 * Allows multiple pricing strategies to be combined using decorator pattern
 */
public interface PricingStrategy {
    BigDecimal calculatePrice(InventoryEntity inventory);
}