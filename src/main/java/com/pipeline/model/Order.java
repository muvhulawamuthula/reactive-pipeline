package com.pipeline.model;

import java.time.LocalDate;
import java.util.List;

public record Order(
        String orderId,
        String customerId,
        CustomerTier customerTier,
        LocalDate orderDate,
        List<LineItem> lineItems,
        boolean cancelled
) {

    public double totalValue() {
        double gross = lineItems.stream()
                .mapToDouble(LineItem::totalPrice)
                .sum();
        return gross * (1 - customerTier.discount);
    }


    public int totalQuantity() {
        return lineItems.stream()
                .mapToInt(LineItem::quantity)
                .sum();
    }


    public String primaryCategory() {
        return lineItems.stream()
                .max(java.util.Comparator.comparingDouble(LineItem::totalPrice))
                .map(LineItem::category)
                .orElse("Unknown");
    }
}