package com.pipeline.pipeline;

import com.pipeline.model.LineItem;
import com.pipeline.model.Order;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FlatMapPipeline {

    public void run(List<Order> orders) {

        AtomicInteger ordersProcessed   = new AtomicInteger();
        AtomicInteger lineItemsProduced = new AtomicInteger();

        System.out.println("=== FlatMap Pipeline ===");
        System.out.println();


        Map<String, Double> revenueByProduct = orders.stream()

                .peek(o -> ordersProcessed.incrementAndGet())


                .filter(o -> !o.cancelled())


                .flatMap(o -> o.lineItems().stream()
                        .peek(item -> lineItemsProduced.incrementAndGet()))


                .collect(Collectors.groupingBy(
                        LineItem::productName,
                        Collectors.summingDouble(LineItem::totalPrice)
                ));


        Map<String, Double> top10 = revenueByProduct.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        Map.Entry<String, Double>::getValue).reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        System.out.println("--- Stage counters ---");
        System.out.println("Orders into pipeline    : " + ordersProcessed.get());
        System.out.println("Line items produced     : " + lineItemsProduced.get());
        System.out.printf("Avg items per order     : %.2f%n",
                (double) lineItemsProduced.get() / ordersProcessed.get());
        System.out.println();

        System.out.println("Top 10 products by revenue:");
        top10.forEach((product, revenue) ->
                System.out.printf("  %-20s → £%,.2f%n", product, revenue));
        System.out.println();


        System.out.println("--- Revenue + quantity by category ---");

        record CategoryStats(double revenue, long quantity) {}

        Map<String, CategoryStats> statsByCategory = orders.stream()
                .filter(o -> !o.cancelled())
                .flatMap(o -> o.lineItems().stream())
                .collect(Collectors.groupingBy(
                        LineItem::category,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                items -> new CategoryStats(
                                        items.stream()
                                                .mapToDouble(LineItem::totalPrice)
                                                .sum(),
                                        items.stream()
                                                .mapToLong(LineItem::quantity)
                                                .sum()
                                )
                        )
                ));

        statsByCategory.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        e -> -e.getValue().revenue()))
                .forEach(e -> System.out.printf(
                        "  %-15s → £%,.2f revenue  |  %,d units sold%n",
                        e.getKey(),
                        e.getValue().revenue(),
                        e.getValue().quantity()));
        System.out.println();


        System.out.println("--- Most expensive single item per category ---");

        Map<String, java.util.Optional<LineItem>> mostExpensive = orders.stream()
                .filter(o -> !o.cancelled())
                .flatMap(o -> o.lineItems().stream())
                .collect(Collectors.groupingBy(
                        LineItem::category,
                        Collectors.maxBy(
                                Comparator.comparingDouble(LineItem::unitPrice))
                ));


        mostExpensive.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(item -> Map.entry(e.getKey(), item)))
                .sorted(Comparator.comparingDouble(
                        e -> -e.getValue().unitPrice()))
                .forEach(e -> System.out.printf(
                        "  %-15s → %-20s £%.2f each%n",
                        e.getKey(),
                        e.getValue().productName(),
                        e.getValue().unitPrice()));
        System.out.println();
    }
}
