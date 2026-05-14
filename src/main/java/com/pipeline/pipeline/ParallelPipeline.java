package com.pipeline.pipeline;

import com.pipeline.collector.RevenueCollector;
import com.pipeline.model.Order;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class ParallelPipeline {

    public void run(List<Order> orders) {
        System.out.println("=== Parallel Pipeline Benchmark ===");
        System.out.println("Cores available : " +
                Runtime.getRuntime().availableProcessors());
        System.out.println();


        for (int i = 0; i < 3; i++) {
            runSequential(orders);
            runParallel(orders);
        }


        int runs = 5;
        long seqTotal = 0;
        long parTotal = 0;

        for (int i = 0; i < runs; i++) {
            seqTotal += runSequential(orders);
            parTotal += runParallel(orders);
        }

        long seqAvg = seqTotal / runs;
        long parAvg = parTotal / runs;
        double speedup = (double) seqAvg / Math.max(parAvg, 1);

        System.out.println("--- Results (averaged over " + runs + " runs) ---");
        System.out.printf("Sequential  : %dms%n", seqAvg);
        System.out.printf("Parallel    : %dms%n", parAvg);
        System.out.printf("Speedup     : %.2f×%n", speedup);
        System.out.println();


        System.out.println("--- Parallel safety checks ---");
        System.out.println();


        System.out.println("✓ filter + map + collect  — stateless, safe");
        long safeCount = orders.parallelStream()
                .filter(o -> !o.cancelled())
                .map(Order::primaryCategory)
                .collect(Collectors.toSet())
                .size();
        System.out.println("  Distinct categories : " + safeCount);
        System.out.println();


        System.out.println("✓ reduce with associative operator — safe");
        double totalRevenue = orders.parallelStream()
                .filter(o -> !o.cancelled())
                .mapToDouble(Order::totalValue)
                .reduce(0.0, Double::sum);
        System.out.printf("  Total revenue : £%,.2f%n", totalRevenue);
        System.out.println();

        System.out.println("✗ Shared mutable state — UNSAFE (demonstrates the bug)");
        List<String> unsafeList = new java.util.ArrayList<>();
        try {
            orders.parallelStream()
                    .filter(o -> !o.cancelled())
                    .map(Order::orderId)
                    .forEach(unsafeList::add);
            System.out.println("  Collected : " + unsafeList.size() +
                    " (may differ from " + orders.stream()
                    .filter(o -> !o.cancelled()).count() +
                    " on repeated runs — race condition)");
        } catch (Exception e) {
            System.out.println("  Exception thrown: " + e.getClass().getSimpleName() +
                    " — race condition confirmed");
        }
        System.out.println();


        System.out.println("✓ Collect to list safely — use toList() not forEach+add");
        List<String> safeList = orders.parallelStream()
                .filter(o -> !o.cancelled())
                .map(Order::orderId)
                .toList(); // thread-safe terminal operation
        System.out.println("  Collected : " + safeList.size() + " (always correct)");
        System.out.println();


        System.out.println("--- Custom ForkJoinPool (isolated thread count) ---");

        int[] parallelismLevels = {1, 2, 4, 8};
        long baselineMs = seqAvg;

        System.out.println("Parallelism   Time     Speedup");
        System.out.println("─".repeat(35));

        for (int parallelism : parallelismLevels) {
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            long start = System.currentTimeMillis();
            try {
                pool.submit(() ->
                        orders.parallelStream()
                                .filter(o -> !o.cancelled())
                                .collect(new RevenueCollector())
                ).get();
            } catch (Exception e) {
                System.err.println("Pool error: " + e.getMessage());
            } finally {
                pool.shutdown();
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("%-14d %5dms   %.2f×%n",
                    parallelism, elapsed,
                    (double) baselineMs / Math.max(elapsed, 1));
        }
        System.out.println();


        System.out.println("--- When parallel hurts (small data) ---");

        List<Order> smallList = orders.subList(0, 100);

        long smallSeq = 0, smallPar = 0;
        for (int i = 0; i < 10; i++) {
            long s = System.currentTimeMillis();
            smallList.stream()
                    .filter(o -> !o.cancelled())
                    .collect(new RevenueCollector());
            smallSeq += System.currentTimeMillis() - s;

            s = System.currentTimeMillis();
            smallList.parallelStream()
                    .filter(o -> !o.cancelled())
                    .collect(new RevenueCollector());
            smallPar += System.currentTimeMillis() - s;
        }

        System.out.println("100 orders — averaged over 10 runs:");
        System.out.printf("  Sequential : %dms%n", smallSeq / 10);
        System.out.printf("  Parallel   : %dms%n", smallPar / 10);
        System.out.println("  Parallel is slower on small data — thread overhead dominates.");
        System.out.println();
    }

    private long runSequential(List<Order> orders) {
        long start = System.currentTimeMillis();
        orders.stream()
                .filter(o -> !o.cancelled())
                .collect(new RevenueCollector());
        return System.currentTimeMillis() - start;
    }

    private long runParallel(List<Order> orders) {
        long start = System.currentTimeMillis();
        orders.parallelStream()
                .filter(o -> !o.cancelled())
                .collect(new RevenueCollector());
        return System.currentTimeMillis() - start;
    }
}
