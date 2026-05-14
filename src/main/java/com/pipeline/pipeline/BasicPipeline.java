package com.pipeline.pipeline;

import com.pipeline.model.CustomerTier;
import com.pipeline.model.Order;
import com.pipeline.report.SalesReport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BasicPipeline {

    public SalesReport run(List<Order> orders) {


        AtomicInteger totalSeen     = new AtomicInteger();
        AtomicInteger afterFilter   = new AtomicInteger();
        AtomicInteger afterMap      = new AtomicInteger();

        Map<String, Double> revenueByCategory = orders.stream()


                .peek(o -> totalSeen.incrementAndGet())


                .filter(o -> !o.cancelled())


                .filter(o -> !o.lineItems().isEmpty())

                .peek(o -> afterFilter.incrementAndGet())


                .map(o -> applyLoyaltyBonus(o))

                .peek(o -> afterMap.incrementAndGet())


                .collect(Collectors.groupingBy(
                        Order::primaryCategory,
                        Collectors.summingDouble(Order::totalValue)
                ));


        Map<String, Double> sorted = revenueByCategory.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        Map.Entry<String, Double>::getValue).reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return new SalesReport(
                "Basic Pipeline",
                sorted,
                totalSeen.get(),
                afterFilter.get(),
                afterMap.get()
        );
    }


    private Order applyLoyaltyBonus(Order order) {
        if (order.customerTier() == CustomerTier.GOLD ||
                order.customerTier() == CustomerTier.PLATINUM) {


            return order;
        }
        return order;
    }
}
