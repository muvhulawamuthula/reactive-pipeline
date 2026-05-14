package com.pipeline.model;

public record LineItem(
        String productId,
        String productName,
        String category,
        int quantity,
        double unitPrice
) {
    public double totalPrice() {
        return quantity * unitPrice;
    }
}
