package com.pipeline.collector;

import com.pipeline.model.CustomerTier;
import com.pipeline.model.Order;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class RevenueCollector
        implements Collector<Order, RevenueCollector.Container, RevenueCollector.Result> {


    public static class Container {
        double totalRevenue     = 0.0;
        long   totalOrders      = 0;
        double maxOrderValue    = Double.MIN_VALUE;
        String maxOrderId       = null;
        Map<String, Double>       revenueByCategory = new HashMap<>();
        Map<CustomerTier, Double> revenueByTier     = new HashMap<>();
        Map<CustomerTier, Long>   countByTier       = new HashMap<>();


        void accumulate(Order order) {
            double value = order.totalValue();

            totalRevenue += value;
            totalOrders++;

            if (value > maxOrderValue) {
                maxOrderValue = value;
                maxOrderId    = order.orderId();
            }

            revenueByCategory.merge(order.primaryCategory(), value, Double::sum);
            revenueByTier.merge(order.customerTier(), value, Double::sum);
            countByTier.merge(order.customerTier(), 1L, Long::sum);
        }


        void combine(Container other) {
            totalRevenue += other.totalRevenue;
            totalOrders  += other.totalOrders;

            if (other.maxOrderValue > this.maxOrderValue) {
                this.maxOrderValue = other.maxOrderValue;
                this.maxOrderId    = other.maxOrderId;
            }

            other.revenueByCategory.forEach((k, v) ->
                    revenueByCategory.merge(k, v, Double::sum));
            other.revenueByTier.forEach((k, v) ->
                    revenueByTier.merge(k, v, Double::sum));
            other.countByTier.forEach((k, v) ->
                    countByTier.merge(k, v, Long::sum));
        }
    }


    public record Result(
            double totalRevenue,
            long   totalOrders,
            double avgOrderValue,
            String topCategory,
            double topCategoryRevenue,
            String highestValueOrderId,
            double highestOrderValue,
            Map<CustomerTier, Double> revenueByTier,
            Map<CustomerTier, Long>   countByTier,
            Map<String, Double>       revenueByCategory
    ) {
        public void print() {
            System.out.println("=== Custom Collector Result ===");
            System.out.println();
            System.out.printf("Total revenue        : £%,.2f%n", totalRevenue);
            System.out.printf("Total orders         : %,d%n",    totalOrders);
            System.out.printf("Average order value  : £%,.2f%n", avgOrderValue);
            System.out.printf("Top category         : %s (£%,.2f)%n",
                    topCategory, topCategoryRevenue);
            System.out.printf("Highest order        : %s (£%,.2f)%n",
                    highestValueOrderId, highestOrderValue);
            System.out.println();

            System.out.println("Revenue by tier:");
            Arrays.stream(CustomerTier.values()).forEach(tier -> {
                double rev   = revenueByTier.getOrDefault(tier, 0.0);
                long   count = countByTier.getOrDefault(tier, 0L);
                double avg   = count == 0 ? 0 : rev / count;
                System.out.printf("  %-10s → £%,.2f total | %,d orders | £%.2f avg%n",
                        tier, rev, count, avg);
            });
            System.out.println();

            System.out.println("Revenue by category:");
            revenueByCategory.entrySet().stream()
                    .sorted(Comparator.comparingDouble(
                            Map.Entry<String, Double>::getValue).reversed())
                    .forEach(e -> System.out.printf(
                            "  %-15s → £%,.2f%n", e.getKey(), e.getValue()));
            System.out.println();
        }
    }


    @Override
    public Supplier<Container> supplier() {
        return Container::new;
    }


    @Override
    public BiConsumer<Container, Order> accumulator() {
        return Container::accumulate;
    }


    @Override
    public BinaryOperator<Container> combiner() {
        return (left, right) -> {
            left.combine(right);
            return left;
        };
    }


    @Override
    public Function<Container, Result> finisher() {
        return container -> {

            Optional<Map.Entry<String, Double>> topCat = container
                    .revenueByCategory.entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue));

            double avg = container.totalOrders == 0
                    ? 0 : container.totalRevenue / container.totalOrders;

            return new Result(
                    container.totalRevenue,
                    container.totalOrders,
                    avg,
                    topCat.map(Map.Entry::getKey).orElse("none"),
                    topCat.map(Map.Entry::getValue).orElse(0.0),
                    container.maxOrderId,
                    container.maxOrderValue,
                    Collections.unmodifiableMap(container.revenueByTier),
                    Collections.unmodifiableMap(container.countByTier),
                    Collections.unmodifiableMap(container.revenueByCategory)
            );
        };
    }


    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.UNORDERED);
    }
}
