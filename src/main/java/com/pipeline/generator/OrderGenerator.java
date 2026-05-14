package com.pipeline.generator;

import com.pipeline.model.CustomerTier;
import com.pipeline.model.LineItem;
import com.pipeline.model.Order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class OrderGenerator {

    private static final String[] CATEGORIES = {
            "Electronics", "Clothing", "Books", "Home", "Sports", "Beauty", "Food"
    };

    private static final String[][] PRODUCTS = {
            {"MacBook Pro", "iPhone 15", "AirPods", "iPad", "Apple Watch"},
            {"T-Shirt", "Jeans", "Jacket", "Sneakers", "Hoodie"},
            {"Clean Code", "Effective Java", "DDIA", "Java Concurrency", "Refactoring"},
            {"Coffee Maker", "Desk Lamp", "Chair", "Keyboard", "Monitor"},
            {"Yoga Mat", "Dumbbells", "Running Shoes", "Protein Powder", "Resistance Bands"},
            {"Moisturiser", "Sunscreen", "Shampoo", "Perfume", "Lip Balm"},
            {"Coffee Beans", "Protein Bar", "Green Tea", "Olive Oil", "Dark Chocolate"}
    };

    private static final double[] BASE_PRICES = {
            1299.0, 49.0, 29.0, 89.0, 149.0, 39.0, 19.0
    };

    private final Random random;

    public OrderGenerator(long seed) {
        this.random = new Random(seed);
    }

    public List<Order> generate(int count) {
        List<Order> orders = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < count; i++) {
            orders.add(generateOrder(i, baseDate));
        }
        return orders;
    }

    private Order generateOrder(int index, LocalDate baseDate) {
        String orderId    = "ORD-" + String.format("%06d", index);
        String customerId = "CUST-" + String.format("%04d", random.nextInt(1000));

        CustomerTier tier = CustomerTier.values()[
                random.nextInt(CustomerTier.values().length)];

        LocalDate date = baseDate.plusDays(random.nextInt(365));


        int lineItemCount = 1 + random.nextInt(4);
        List<LineItem> lineItems = new ArrayList<>();

        for (int j = 0; j < lineItemCount; j++) {
            int categoryIndex = random.nextInt(CATEGORIES.length);
            String category   = CATEGORIES[categoryIndex];
            String[] products = PRODUCTS[categoryIndex];
            String productName = products[random.nextInt(products.length)];
            String productId  = category.substring(0, 3).toUpperCase()
                    + "-" + random.nextInt(999);
            int quantity  = 1 + random.nextInt(5);
            double price  = BASE_PRICES[categoryIndex]
                    * (0.5 + random.nextDouble());

            lineItems.add(new LineItem(
                    productId, productName, category,
                    quantity, Math.round(price * 100.0) / 100.0));
        }


        boolean cancelled = random.nextDouble() < 0.12;

        return new Order(orderId, customerId, tier, date, lineItems, cancelled);
    }
}
