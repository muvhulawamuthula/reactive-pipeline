package com.pipeline.model;

public enum CustomerTier {
    BRONZE(0.0),
    SILVER(0.05),
    GOLD(0.10),
    PLATINUM(0.15);

    // Discount applied to all orders from this tier
    public final double discount;

    CustomerTier(double discount) {
        this.discount = discount;
    }
}
