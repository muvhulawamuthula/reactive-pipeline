package com.pipeline.pipeline;

import com.pipeline.collector.RevenueCollector;
import com.pipeline.model.Order;

import java.util.List;

public class CustomCollectorPipeline {

    public void run(List<Order> orders) {

        // ── Sequential run ───────────────────────────────────────────────
        System.out.println("--- Running custom collector (sequential) ---");

        long seqStart = System.currentTimeMillis();

        RevenueCollector.Result seqResult = orders.stream()
                .filter(o -> !o.cancelled())
                .collect(new RevenueCollector());

        long seqTime = System.currentTimeMillis() - seqStart;

        seqResult.print();
        System.out.println("Sequential time : " + seqTime + "ms");
        System.out.println();

        // ── Parallel run ─────────────────────────────────────────────────
        // The combiner() makes this safe — parallel streams split the list,
        // each thread gets its own Container, combiner merges them at the end
        System.out.println("--- Running custom collector (parallel) ---");

        long parStart = System.currentTimeMillis();

        RevenueCollector.Result parResult = orders.parallelStream()
                .filter(o -> !o.cancelled())
                .collect(new RevenueCollector());

        long parTime = System.currentTimeMillis() - parStart;

        System.out.println("Parallel time   : " + parTime + "ms");
        System.out.println();

        // ── Correctness check ────────────────────────────────────────────
        // Both runs must produce identical results
        // If combiner() is wrong, numbers will differ in parallel
        System.out.println("--- Correctness check (sequential vs parallel) ---");

        boolean revenueMatch = Math.abs(
                seqResult.totalRevenue() - parResult.totalRevenue()) < 0.01;
        boolean ordersMatch  = seqResult.totalOrders() == parResult.totalOrders();
        boolean topCatMatch  = seqResult.topCategory().equals(parResult.topCategory());

        System.out.println("Total revenue match : " + (revenueMatch ? "✓" : "✗ MISMATCH"));
        System.out.println("Total orders match  : " + (ordersMatch  ? "✓" : "✗ MISMATCH"));
        System.out.println("Top category match  : " + (topCatMatch  ? "✓" : "✗ MISMATCH"));

        if (revenueMatch && ordersMatch && topCatMatch) {
            System.out.println("\n✓ Custom collector is parallel-safe.");
            System.out.printf("  Speedup: %.2f×%n",
                    (double) seqTime / Math.max(parTime, 1));
        }
        System.out.println();
    }
}