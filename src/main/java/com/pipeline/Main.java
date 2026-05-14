package com.pipeline;

import com.pipeline.generator.OrderGenerator;
import com.pipeline.model.Order;
import com.pipeline.pipeline.BasicPipeline;
import com.pipeline.pipeline.CollectorsPipeline;
import com.pipeline.pipeline.CustomCollectorPipeline;
import com.pipeline.pipeline.FlatMapPipeline;
import com.pipeline.pipeline.ParallelPipeline;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        OrderGenerator generator = new OrderGenerator(42);
        List<Order> orders = generator.generate(10_000);

        System.out.println("Orders generated : " + orders.size());
        System.out.println();

        new BasicPipeline().run(orders).print();
        new FlatMapPipeline().run(orders);
        new CollectorsPipeline().run(orders);
        new CustomCollectorPipeline().run(orders);
        new ParallelPipeline().run(orders);
    }
}
