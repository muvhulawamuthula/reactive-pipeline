package com.pipeline.pipeline;

import com.pipeline.model.CustomerTier;
import com.pipeline.model.LineItem;
import com.pipeline.model.Order;

import java.util.*;
import java.util.stream.Collectors;

public class CollectorsPipeline {

    public void run(List<Order> orders) {
        System.out.println("=== Advanced Collectors Pipeline ===");
        System.out.println();

        List<Order> active = orders.stream()
                .filter(o -> !o.cancelled())
                .toList();


        System.out.println("--- Collectors.teeing (two aggregations, one pass) ---");

        record Summary(double totalRevenue, long totalOrders) {
            double avgOrderValue() {
                return totalOrders == 0 ? 0 : totalRevenue / totalOrders;
            }
        }

        Summary summary = active.stream()
                .collect(Collectors.teeing(
                        Collectors.summingDouble(Order::totalValue),
                        Collectors.counting(),
                        Summary::new
                ));

        System.out.printf("Total revenue    : £%,.2f%n", summary.totalRevenue());
        System.out.printf("Total orders     : %,d%n",    summary.totalOrders());
        System.out.printf("Avg order value  : £%,.2f%n", summary.avgOrderValue());
        System.out.println("(computed in a single pass — no double iteration)");
        System.out.println();



        System.out.println("--- Collectors.partitioningBy (high value vs standard) ---");

        double avgValue = summary.avgOrderValue();

        Map<Boolean, List<Order>> partitioned = active.stream()
                .collect(Collectors.partitioningBy(
                        o -> o.totalValue() > avgValue
                ));

        List<Order> highValue = partitioned.get(true);
        List<Order> standard  = partitioned.get(false);

        double highRevenue = highValue.stream()
                .mapToDouble(Order::totalValue).sum();
        double stdRevenue  = standard.stream()
                .mapToDouble(Order::totalValue).sum();

        System.out.printf("High-value orders (> £%.2f avg):%n", avgValue);
        System.out.printf("  Count   : %,d  (%.1f%% of orders)%n",
                highValue.size(),
                100.0 * highValue.size() / active.size());
        System.out.printf("  Revenue : £%,.2f  (%.1f%% of total)%n",
                highRevenue,
                100.0 * highRevenue / summary.totalRevenue());
        System.out.println("Standard orders:");
        System.out.printf("  Count   : %,d  (%.1f%% of orders)%n",
                standard.size(),
                100.0 * standard.size() / active.size());
        System.out.printf("  Revenue : £%,.2f  (%.1f%% of total)%n",
                stdRevenue,
                100.0 * stdRevenue / summary.totalRevenue());
        System.out.println();


        System.out.println("--- Multi-level groupingBy (tier → category → revenue) ---");

        Map<CustomerTier, Map<String, Double>> tierCategoryRevenue = active.stream()
                .collect(Collectors.groupingBy(
                        Order::customerTier,
                        Collectors.groupingBy(
                                Order::primaryCategory,
                                Collectors.summingDouble(Order::totalValue)
                        )
                ));


        Arrays.stream(CustomerTier.values()).forEach(tier -> {
            Map<String, Double> categoryMap = tierCategoryRevenue
                    .getOrDefault(tier, Map.of());

            Optional<Map.Entry<String, Double>> topCategory = categoryMap
                    .entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue));

            System.out.printf("  %-10s → top category: %-15s £%,.2f%n",
                    tier,
                    topCategory.map(Map.Entry::getKey).orElse("none"),
                    topCategory.map(Map.Entry::getValue).orElse(0.0));
        });
        System.out.println();

        System.out.println("--- groupingBy + teeing downstream (revenue + count per category) ---");

        record CategorySummary(double revenue, long itemCount) {
            double avgItemValue() {
                return itemCount == 0 ? 0 : revenue / itemCount;
            }
        }

        Map<String, CategorySummary> categoryStats = active.stream()
                .flatMap(o -> o.lineItems().stream())
                .collect(Collectors.groupingBy(
                        LineItem::category,
                        Collectors.teeing(
                                Collectors.summingDouble(LineItem::totalPrice),
                                Collectors.counting(),
                                CategorySummary::new
                        )
                ));

        categoryStats.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        e -> -e.getValue().revenue()))
                .forEach(e -> {
                    CategorySummary s = e.getValue();
                    System.out.printf(
                            "  %-15s → £%,.2f revenue | %,d items | £%.2f avg%n",
                            e.getKey(), s.revenue(), s.itemCount(), s.avgItemValue());
                });
        System.out.println();


        System.out.println("--- Top 5 customers by lifetime spend ---");

        Map<String, Double> spendByCustomer = active.stream()
                .collect(Collectors.toUnmodifiableMap(
                        Order::customerId,
                        Order::totalValue,
                        Double::sum
                ));

        spendByCustomer.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        Map.Entry<String, Double>::getValue).reversed())
                .limit(5)
                .forEach(e -> System.out.printf(
                        "  %-12s → £%,.2f%n", e.getKey(), e.getValue()));
        System.out.println();


        System.out.println("--- Collectors.joining (CSV category summary) ---");

        String csv = categoryStats.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        e -> -e.getValue().revenue()))
                .map(e -> String.format("%s,%.2f,%d",
                        e.getKey(),
                        e.getValue().revenue(),
                        e.getValue().itemCount()))
                .collect(Collectors.joining("\n", "category,revenue,items\n", ""));

        System.out.println(csv);
    }
}
