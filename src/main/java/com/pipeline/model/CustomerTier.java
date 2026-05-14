package com.pipeline.model;

public enum CustomerTier {
    BRONZE(0.0),
    SILVER(0.05),
    GOLD(0.10),
    PLATINUM(0.15);


    public final double discount;

    CustomerTier(double discount) {
        this.discount = discount;
    }
}
