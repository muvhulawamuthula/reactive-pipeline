package com.pipeline.report;

import java.util.Map;

public record SalesReport(
        String pipelineName,
        Map<String, Double> revenueByCategory,
        int totalSeen,
        int afterFilter,
        int afterMap
) {
    public double totalRevenue() {
        return revenueByCategory.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    public void print() {
        System.out.println("=== " + pipelineName + " ===");
        System.out.println();


        System.out.println("--- Pipeline stage counters (proves lazy evaluation) ---");
        System.out.println("1. Entered pipeline  : " + totalSeen);
        System.out.println("2. Passed filter     : " + afterFilter);
        System.out.println("3. Passed map        : " + afterMap);
        System.out.println();


        System.out.printf("Total revenue        : £%,.2f%n", totalRevenue());
        System.out.println();
        System.out.println("Revenue by category (sorted):");
        revenueByCategory.forEach((cat, rev) ->
                System.out.printf("  %-15s → £%,.2f%n", cat, rev));
        System.out.println();
    }
}
