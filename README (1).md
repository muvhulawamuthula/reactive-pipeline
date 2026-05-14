# Reactive Data Pipeline

A multi-stage e-commerce order processing pipeline built entirely with Java Streams — no frameworks, no libraries, just the standard library.

Raw order data flows through parse, filter, map, flatMap, and collect stages into a full sales report. Six pipeline variants demonstrate every major Stream concept from lazy evaluation to custom Collector implementation.

---

## What it covers

| Step | Pipeline | Key concept |
|---|---|---|
| 2 | Basic pipeline | Lazy evaluation proven with `peek()` counters |
| 3 | FlatMap pipeline | `flatMap` fan-out, `Optional::stream` unwrapping |
| 4 | Advanced collectors | `teeing`, `partitioningBy`, multi-level `groupingBy` |
| 5 | Custom Collector | Full `Collector<T,A,R>` implementation from scratch |
| 6 | Parallel pipeline | `.parallel()` safety, speedup, when it hurts |

---

## Project structure

```
src/main/java/com/pipeline/
├── Main.java                           # Entry point — runs all pipelines
├── model/
│   ├── Order.java                      # Record: orderId, customer, tier, lineItems, cancelled
│   ├── LineItem.java                   # Record: productId, name, category, quantity, unitPrice
│   └── CustomerTier.java               # Enum: BRONZE, SILVER, GOLD, PLATINUM (with discount)
├── generator/
│   └── OrderGenerator.java             # Produces 10k realistic orders with fixed seed
├── pipeline/
│   ├── BasicPipeline.java              # filter → map → groupingBy + peek counters
│   ├── FlatMapPipeline.java            # flatMap line items, Optional::stream pattern
│   ├── CollectorsPipeline.java         # teeing, partitioningBy, multi-level groupingBy
│   ├── CustomCollectorPipeline.java    # Uses RevenueCollector, correctness check
│   └── ParallelPipeline.java           # Benchmarks, safety checks, custom ForkJoinPool
├── collector/
│   └── RevenueCollector.java           # Full Collector<Order, Container, Result> impl
└── report/
    └── SalesReport.java                # Immutable result record with print()
```

---

## Prerequisites

- Java 21+
- Maven 3.8+

---

## Getting started

```bash
git clone https://github.com/yourusername/reactive-pipeline.git
cd reactive-pipeline
mvn compile exec:java -Dexec.mainClass="com.pipeline.Main"
```

---

## Pipeline deep dives

### Basic pipeline — lazy evaluation

```java
Map<String, Double> revenueByCategory = orders.stream()
    .peek(o -> totalSeen.incrementAndGet())       // runs for all 10,000
    .filter(o -> !o.cancelled())                  // removes 1,198
    .filter(o -> !o.lineItems().isEmpty())
    .peek(o -> afterFilter.incrementAndGet())      // runs for 8,802
    .map(o -> applyLoyaltyBonus(o))
    .peek(o -> afterMap.incrementAndGet())         // runs for 8,802
    .collect(Collectors.groupingBy(
            Order::primaryCategory,
            Collectors.summingDouble(Order::totalValue)
    ));
```

The `peek()` counters prove lazy evaluation: `map()` processes only elements that survived `filter()`. More importantly — nothing runs at all until `collect()` is called. Remove the terminal operation and the entire pipeline is defined but never executes.

---

### FlatMap pipeline — fan-out

`map` transforms one element into one element. `flatMap` transforms one element into zero or more elements and flattens the result:

```java
// map gives Stream<List<LineItem>> — nested, can't aggregate across orders
orders.stream().map(o -> o.lineItems())

// flatMap gives Stream<LineItem> — flat, aggregate freely
orders.stream().flatMap(o -> o.lineItems().stream())
```

Applied to 10,000 orders averaging 2.5 line items each produces ~24,873 `LineItem` elements in one flat stream — then aggregated by product, category, or anything at the item level.

The `Optional::stream` pattern:

```java
// Old — verbose
.filter(Optional::isPresent).map(Optional::get)

// Modern — Java 9+
.flatMap(Optional::stream)
```

`Optional.stream()` returns a stream of 0 elements if empty, 1 if present. `flatMap` flattens it — empty Optionals vanish, present ones unwrap cleanly.

---

### Advanced collectors

**`Collectors.teeing`** — two downstream collectors, one pass, merged at the end:

```java
record Summary(double totalRevenue, long totalOrders) {}

Summary summary = active.stream()
    .collect(Collectors.teeing(
        Collectors.summingDouble(Order::totalValue),  // downstream 1
        Collectors.counting(),                         // downstream 2
        Summary::new                                   // merge function
    ));
```

No iterating twice. No intermediate variables. Revenue and count computed simultaneously.

**Multi-level `groupingBy`** — group by tier, then by category:

```java
Map<CustomerTier, Map<String, Double>> tierCategoryRevenue = active.stream()
    .collect(Collectors.groupingBy(
        Order::customerTier,
        Collectors.groupingBy(
            Order::primaryCategory,
            Collectors.summingDouble(Order::totalValue)
        )
    ));
```

**`groupingBy` + `teeing` downstream** — per-group multi-aggregation in one pass:

```java
Map<String, CategorySummary> stats = active.stream()
    .flatMap(o -> o.lineItems().stream())
    .collect(Collectors.groupingBy(
        LineItem::category,
        Collectors.teeing(
            Collectors.summingDouble(LineItem::totalPrice),
            Collectors.counting(),
            CategorySummary::new
        )
    ));
```

**`toUnmodifiableMap` with merge function** — handles duplicate keys:

```java
// Without merge fn — throws IllegalStateException on duplicate key
// With merge fn — sums values for the same customerId
Map<String, Double> spendByCustomer = active.stream()
    .collect(Collectors.toUnmodifiableMap(
        Order::customerId,
        Order::totalValue,
        Double::sum          // called when customerId appears more than once
    ));
```

---

### Custom Collector — `Collector<T, A, R>`

The five methods every Collector must implement:

```
supplier()      → A        creates empty mutable container
accumulator()   → (A, T)   folds one element into container
combiner()      → (A, A)   merges two containers (parallel only)
finisher()      → A → R    transforms container into final result
characteristics()          hints: UNORDERED, CONCURRENT, IDENTITY_FINISH
```

Sequential execution:
```
supplier() → Container
  accumulate(order1)
  accumulate(order2)   ← one container, one thread
  accumulate(order3)
finisher() → Result
```

Parallel execution:
```
supplier() → C1        supplier() → C2
accumulate C1          accumulate C2   ← two threads, two containers
      └──── combiner(C1, C2) → merged C
                   └── finisher(C) → Result
```

The combiner must be associative — `combine(A, B)` must produce the same result as `combine(B, A)`. A broken combiner produces different results in parallel vs sequential, caught by the correctness check.

---

### Parallel pipeline — rules and limits

**Safe operations** — stateless, no shared mutable state:
```java
orders.parallelStream()
      .filter(o -> !o.cancelled())   // stateless predicate ✓
      .map(Order::primaryCategory)   // stateless function ✓
      .collect(Collectors.toSet())   // thread-safe collector ✓
```

**Unsafe — shared mutable ArrayList:**
```java
List<String> result = new ArrayList<>();
orders.parallelStream()
      .forEach(result::add);  // ✗ ArrayList not thread-safe — data loss or exception
```

**Safe fix:**
```java
List<String> result = orders.parallelStream()
      .map(Order::orderId)
      .toList();  // ✓ terminal operation handles thread safety
```

**Custom ForkJoinPool** — isolate parallelism level without affecting common pool:
```java
ForkJoinPool pool = new ForkJoinPool(4);
pool.submit(() ->
    orders.parallelStream()
          .collect(new RevenueCollector())
).get();
pool.shutdown();
```

**When parallel hurts** — 100 orders, parallel is slower than sequential. Thread creation and task splitting overhead dominates when data is small. Only parallelize when the data volume justifies it.

---

## Key concepts

### Intermediate vs terminal operations

```
Intermediate (lazy — nothing executes):
  filter, map, flatMap, peek, sorted, distinct, limit, skip

Terminal (eager — triggers the pipeline):
  collect, forEach, count, reduce, findFirst, anyMatch, toList
```

A stream with only intermediate operations is just a description of work. Nothing executes until a terminal operation is called. This is why `limit(5)` on an infinite stream works.

### Short-circuit operations

`anyMatch`, `findFirst`, `limit` stop the pipeline as soon as they have enough information:

```java
// Processes elements until it finds one match — not all 10,000
boolean hasHighValue = orders.stream()
    .anyMatch(o -> o.totalValue() > 1000.0);
```

### Characteristics

`IDENTITY_FINISH` — finisher is `Function.identity()`, skipped as optimisation. Use when container type equals result type.

`CONCURRENT` — accumulator is thread-safe, combiner not needed in parallel. Requires a thread-safe container (e.g. `ConcurrentHashMap`).

`UNORDERED` — result doesn't depend on encounter order. Allows parallel streams to skip re-ordering work.

---

## Sample output

```
Orders generated : 10,000

=== Basic Pipeline ===
1. Entered pipeline  : 10,000
2. Passed filter     : 8,802
3. Passed map        : 8,802
Total revenue        : £2,847,234.80

Revenue by category (sorted):
  Electronics     → £987,432.10
  Clothing        → £541,234.50
  Home            → £387,654.20
  ...

=== FlatMap Pipeline ===
Orders into pipeline    : 10,000
Line items produced     : 24,873
Avg items per order     : 2.83

=== Custom Collector Result ===
Total revenue        : £2,847,234.80
Total orders         : 8,802
Average order value  : £323.48
Top category         : Electronics (£987,432.10)
Highest order        : ORD-004821 (£3,287.43)

=== Parallel Pipeline Benchmark ===
Sequential  : 43ms
Parallel    : 14ms
Speedup     : 3.07×
```

---

## Why this matters

Most Java developers know `filter`, `map`, and `collect`. The gap shows at:

- **`flatMap`** — most struggle to explain when to use it without an example
- **`Collectors.teeing`** — many senior developers haven't used it
- **Custom `Collector`** — almost nobody has implemented `Collector<T,A,R>` from scratch
- **Parallel correctness** — most assume `.parallel()` is always safe
- **Lazy evaluation** — most think streams execute eagerly

Building these pipelines end to end means you can discuss every concept with code to point to.

---

## Stretch goals

- [ ] Feed the pipeline from `Files.lines()` — prove lazy I/O, lines read only as stream pulls them
- [ ] Add `peek()` nanosecond timers at each stage — profile without breaking the chain
- [ ] Implement `Collector` with `CONCURRENT` characteristic using `ConcurrentHashMap`
- [ ] Replace `groupingBy` with a hand-rolled `reduce()` — understand what collectors do underneath
- [ ] Add `Optional` throughout — replace all null returns with monadic chaining
- [ ] Generate 10M orders and measure sequential vs parallel throughput at scale
- [ ] Chain pipelines — output `SalesReport` feeds a second pipeline that produces recommendations

---

## References

- [Java SE 21 — Stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html)
- [Java SE 21 — Collectors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)
- [Java SE 21 — Collector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html)
- [JEP 441 — Pattern Matching for switch](https://openjdk.org/jeps/441)

---

## License

MIT
