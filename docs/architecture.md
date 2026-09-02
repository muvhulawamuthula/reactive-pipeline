# Architecture

How data moves through **reactive-pipeline**, mapped to the actual classes under `com.pipeline`.

---

## High-level flow

```
OrderGenerator(seed=42)
        │  generate(10_000)
        ▼
   List<Order>          ◄── immutable records (Order, LineItem, CustomerTier)
        │
        ├──────────────► BasicPipeline          → SalesReport.print()
        ├──────────────► FlatMapPipeline        → console (product / category stats)
        ├──────────────► CollectorsPipeline     → console (teeing, partitions, groups)
        ├──────────────► CustomCollectorPipeline→ RevenueCollector.Result (+ correctness)
        └──────────────► ParallelPipeline       → benchmarks + safety demos
```

[`Main`](../src/main/java/com/pipeline/Main.java) owns orchestration only: generate once, run each pipeline against the same list.

---

## Packages

| Package | Responsibility |
|---|---|
| `com.pipeline` | Entry point |
| `com.pipeline.model` | Domain records / enum — no I/O, no Stream pipelines |
| `com.pipeline.generator` | Seeded synthetic data (deterministic for demos) |
| `com.pipeline.pipeline` | Five teaching pipelines; side effect is console output (plus `SalesReport` from Basic) |
| `com.pipeline.collector` | Production-style custom `Collector` |
| `com.pipeline.report` | Immutable report DTO used by Basic |

There is **no** Spring, Reactor, RxJava, or database layer. “Reactive” here means Stream pipelines and lazy evaluation, not a reactive framework.

---

## Domain details

### Order value

```text
gross   = Σ lineItem.totalPrice()          // quantity × unitPrice
net     = gross × (1 − customerTier.discount)
```

Discounts: Bronze 0%, Silver 5%, Gold 10%, Platinum 15% (`CustomerTier`).

### Primary category

`Order.primaryCategory()` = category of the line item with the **maximum** `totalPrice`, or `"Unknown"` if empty. Used as the grouping key in Basic and in nested `groupingBy` in Collectors.

### Cancellation

`OrderGenerator` marks ~12% of orders cancelled (`random.nextDouble() < 0.12`). Every pipeline filters `!o.cancelled()` (Basic also drops empty `lineItems`).

### Catalogue

Seven categories (Electronics … Food), five product names each, base prices scaled by a random factor. Order IDs `ORD-000000`…; customer IDs drawn from `CUST-0000`…`CUST-0999`. Dates from 2024-01-01 + 0…364 days.

---

## Pipeline responsibilities

### BasicPipeline

```text
stream
  peek → totalSeen
  filter !cancelled
  filter !lineItems.isEmpty
  peek → afterFilter
  map applyLoyaltyBonus   // currently identity for all tiers (hook for future bonus)
  peek → afterMap
  collect groupingBy(primaryCategory, summingDouble(totalValue))
  sort by revenue desc → LinkedHashMap
→ SalesReport
```

Purpose: prove laziness with stage counters and produce a category revenue map.

### FlatMapPipeline

```text
Orders ──filter──► flatMap(lineItems) ──► Stream<LineItem>
                         │
                         ├─ groupingBy(productName, summingDouble) → top 10
                         ├─ groupingBy(category, collectingAndThen → CategoryStats)
                         └─ groupingBy(category, maxBy(unitPrice))
                              then flatMap(Optional::stream) to print
```

Purpose: item-level aggregates and the `Optional` unwrap idiom after `maxBy`.

### CollectorsPipeline

Works on `active = orders.filter(!cancelled).toList()`, then:

1. **teeing**(`summingDouble`, `counting`, `Summary::new`)  
2. **partitioningBy**(`totalValue > avg`) with revenue share stats  
3. **groupingBy**(tier, **groupingBy**(primaryCategory, summingDouble))  
4. flatMap line items → **groupingBy**(category, **teeing**(sum, count, CategorySummary))  
5. **toUnmodifiableMap**(customerId, totalValue, Double::sum) → top 5  
6. **joining** CSV header + rows  

Purpose: show one-pass multi-aggregation and nested collectors without custom types beyond local records.

### CustomCollectorPipeline + RevenueCollector

```text
                    ┌── sequential stream.collect(new RevenueCollector())
orders ─filter──┤
                    └── parallelStream.collect(new RevenueCollector())
                              │
                              ▼
                    assert |Δ revenue| < 0.01
                           order counts equal
                           topCategory equal
```

`RevenueCollector` shape:

| Method | Implementation |
|---|---|
| `supplier` | `Container::new` |
| `accumulator` | `Container::accumulate` (mutate totals, max, maps) |
| `combiner` | left.combine(right); return left |
| `finisher` | build `Result` (avg, top category, unmodifiable maps) |
| `characteristics` | `UNORDERED` only (not `CONCURRENT` / `IDENTITY_FINISH`) |

Parallel safety comes from **associative combine** of independent containers — not from a concurrent accumulator.

### ParallelPipeline

1. Warmup (3 seq/par pairs) then average 5 timed runs of `RevenueCollector`  
2. Safe patterns: filter+map+`toSet`, associative `reduce`  
3. Unsafe demo: `forEach` into shared `ArrayList`  
4. Safe fix: `.toList()`  
5. Custom `ForkJoinPool` at parallelism 1, 2, 4, 8  
6. Sublist of 100 orders — parallel often slower  

---

## Design choices

| Choice | Rationale |
|---|---|
| Records + enum | Immutable domain; methods stay pure and Stream-friendly |
| Seeded generator | Reproducible demos without fixtures files |
| Console as sink | Keeps focus on Stream APIs; no report framework |
| Custom collector separate from pipelines | Same collector reused in Custom + Parallel |
| `UNORDERED` | Aggregation does not depend on encounter order |
| No external deps | JDK Stream API is the entire stack |

---

## Extension points

- Replace `OrderGenerator` with a `Stream<Order>` from files or a message source — pipelines already accept `List<Order>` / can be adapted to `Stream`.  
- Make `applyLoyaltyBonus` actually adjust prices or attach metadata.  
- Add a `CONCURRENT` collector variant using thread-safe maps.  
- Persist `SalesReport` / `RevenueCollector.Result` instead of printing.