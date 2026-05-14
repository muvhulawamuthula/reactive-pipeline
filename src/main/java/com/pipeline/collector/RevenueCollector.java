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

    // ── The mutable accumulation container ──────────────────────────────
    // This is what gets built up as elements flow through the stream.
    // It must be mutable — that's the whole point.
    // ────────────────────────────────────────────────────────────────────
    public static class Container {
        double totalRevenue     = 0.0;
        long   totalOrders      = 0;
        double maxOrderValue    = Double.MIN_VALUE;
        String maxOrderId       = null;
        Map<String, Double>       revenueByCategory = new HashMap<>();
        Map<CustomerTier, Double> revenueByTier     = new HashMap<>();
        Map<CustomerTier, Long>   countByTier       = new HashMap<>();

        // Fold one order into this container
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

        // Merge another container into this one (used by parallel streams)
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

    // ── The final immutable result ───────────────────────────────────────
    // Produced by finisher() from the container.
    // Immutable — safe to share across threads.
    // ────────────────────────────────────────────────────────────────────
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

    // ── The five Collector methods ───────────────────────────────────────

    // 1. supplier — called once to create the empty container
    @Override
    public Supplier<Container> supplier() {
        return Container::new;
    }

    // 2. accumulator — called once per element to fold it into the container
    @Override
    public BiConsumer<Container, Order> accumulator() {
        return Container::accumulate;
    }

    // 3. combiner — called to merge two containers in parallel streams
    // Must be associative: combine(A,B) must equal combine(B,A) in effect
    @Override
    public BinaryOperator<Container> combiner() {
        return (left, right) -> {
            left.combine(right);
            return left;
        };
    }

    // 4. finisher — transforms the mutable container into the final result
    // This is where we do any final computation (averages, sorting, etc.)
    @Override
    public Function<Container, Result> finisher() {
        return container -> {
            // Find top category
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

    // 5. characteristics — hints to the stream engine
    // UNORDERED  → result doesn't depend on encounter order (safe for parallel)
    // No CONCURRENT → we don't support concurrent access to the container
    // No IDENTITY_FINISH → finisher is not the identity function (we transform)
    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.UNORDERED);
    }
}
