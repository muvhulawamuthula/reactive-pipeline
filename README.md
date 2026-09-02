# Reactive Pipeline

E-commerce order processing built entirely with **Java Streams** — no frameworks, no reactive libraries. Raw orders flow through filter, map, flatMap, and collect stages into sales reports. Five pipeline variants cover lazy evaluation, fan-out, advanced collectors, a custom `Collector<T,A,R>`, and parallel safety.

> **Java 21 · Maven · standard library only**

---

## Why this repo

Most tutorials stop at `filter` / `map` / `collect`. This project implements a full domain pipeline so you can point at real code when discussing:

| Concept | Where it lives |
|---|---|
| Lazy evaluation (`peek` stage counters) | [`BasicPipeline`](src/main/java/com/pipeline/pipeline/BasicPipeline.java) |
| `flatMap` fan-out + `Optional::stream` | [`FlatMapPipeline`](src/main/java/com/pipeline/pipeline/FlatMapPipeline.java) |
| `teeing`, `partitioningBy`, nested `groupingBy` | [`CollectorsPipeline`](src/main/java/com/pipeline/pipeline/CollectorsPipeline.java) |
| Custom `Collector<T,A,R>` (supplier → accumulator → combiner → finisher) | [`RevenueCollector`](src/main/java/com/pipeline/collector/RevenueCollector.java) |
| Parallel safety, ForkJoinPool sizing, when parallel hurts | [`ParallelPipeline`](src/main/java/com/pipeline/pipeline/ParallelPipeline.java) |

Deep dives: [Architecture](docs/architecture.md) · [Getting started](docs/getting-started.md)

---

## Quick start

```bash
git clone https://github.com/muvhulawamuthula/reactive-pipeline.git
cd reactive-pipeline
mvn -q compile exec:java -Dexec.mainClass="com.pipeline.Main"
```

**Requires:** JDK 21+, Maven 3.8+. No extra dependencies (`pom.xml` is stdlib-only).

[`Main`](src/main/java/com/pipeline/Main.java) generates **10,000** seeded orders (`OrderGenerator(42)`), then runs all five pipelines in sequence.

---

## Project layout

```
src/main/java/com/pipeline/
├── Main.java
├── model/
│   ├── Order.java              # record + totalValue / primaryCategory
│   ├── LineItem.java           # record + totalPrice
│   └── CustomerTier.java       # BRONZE…PLATINUM with discount rates
├── generator/
│   └── OrderGenerator.java     # seeded synthetic catalogue (7 categories)
├── pipeline/
│   ├── BasicPipeline.java
│   ├── FlatMapPipeline.java
│   ├── CollectorsPipeline.java
│   ├── CustomCollectorPipeline.java
│   └── ParallelPipeline.java
├── collector/
│   └── RevenueCollector.java   # Collector<Order, Container, Result>
└── report/
    └── SalesReport.java        # immutable report + print()
```

---

## Domain model (accurate to code)

| Type | Role |
|---|---|
| `Order` | `orderId`, `customerId`, `customerTier`, `orderDate`, `lineItems`, `cancelled`. `totalValue()` applies tier discount to line-item gross. `primaryCategory()` = category of the highest-priced line item. |
| `LineItem` | `productId`, `productName`, `category`, `quantity`, `unitPrice`; `totalPrice() = quantity × unitPrice`. |
| `CustomerTier` | `BRONZE(0%)`, `SILVER(5%)`, `GOLD(10%)`, `PLATINUM(15%)`. |
| `OrderGenerator` | Fixed seed; ~12% cancelled; 1–4 line items; categories Electronics, Clothing, Books, Home, Sports, Beauty, Food. |

---

## Pipeline map

### 1. Basic — lazy evaluation

Filters cancelled / empty orders, maps through a loyalty hook, groups revenue by `primaryCategory`. `AtomicInteger` counters behind `peek()` show that `map` only sees elements that survived `filter`, and that **nothing runs until** the terminal `collect`.

### 2. FlatMap — order → line items

`flatMap(o -> o.lineItems().stream())` turns `Stream<Order>` into `Stream<LineItem>` for product- and category-level aggregates. Uses `Collectors.maxBy` (returns `Optional`) then **`Optional::stream` via `flatMap`** to unwrap present values without `isPresent`/`get`.

### 3. Collectors — one-pass multi-aggregation

- **`Collectors.teeing`** — revenue + count in a single pass → local `Summary` record  
- **`partitioningBy`** — high-value vs standard vs average order value  
- **Nested `groupingBy`** — tier → category → revenue  
- **`groupingBy` + downstream `teeing`** — per-category revenue and item count  
- **`toUnmodifiableMap(..., Double::sum)`** — top customers by lifetime spend  
- **`Collectors.joining`** — CSV category summary  

### 4. Custom collector — `RevenueCollector`

Implements all five `Collector` methods. Mutable `Container` accumulates totals, max order, revenue/count by tier and category; `finisher` builds an immutable `Result`. Characteristic: **`UNORDERED`**. [`CustomCollectorPipeline`](src/main/java/com/pipeline/pipeline/CustomCollectorPipeline.java) runs sequential vs parallel and asserts revenue, order count, and top category match.

### 5. Parallel — rules and limits

Benchmarks sequential vs `parallelStream` (warmup + averaged runs), demonstrates safe vs unsafe patterns (shared `ArrayList` `forEach` vs `toList()`), sizes an isolated `ForkJoinPool`, and shows **100 orders** where parallel is slower (thread overhead dominates).

---

## Sample console shape

```
Orders generated : 10,000

=== Basic Pipeline ===
1. Entered pipeline  : 10,000
2. Passed filter     : …
3. Passed map        : …
Total revenue        : £…

=== FlatMap Pipeline ===
Orders into pipeline    : 10,000
Line items produced     : …
…

=== Advanced Collectors Pipeline ===
…

=== Custom Collector Result ===
…

=== Parallel Pipeline Benchmark ===
Sequential  : …ms
Parallel    : …ms
Speedup     : …×
```

Exact figures vary by JVM and core count; the seed keeps the **order set** reproducible.

---

## Key Stream rules this code demonstrates

**Intermediate vs terminal**

```
Intermediate (lazy): filter, map, flatMap, peek, sorted, distinct, limit, skip
Terminal (eager):    collect, forEach, count, reduce, findFirst, anyMatch, toList
```

**Custom collector contract**

```
supplier()        → empty Container
accumulator()     → fold one Order into Container
combiner()        → merge two Containers (parallel)
finisher()        → Container → Result
characteristics() → UNORDERED (here)
```

Combiner must be associative — otherwise sequential and parallel results diverge (caught by the correctness check).

---

## Docs

| Doc | Contents |
|---|---|
| [Getting started](docs/getting-started.md) | Prerequisites, run, what each stage prints |
| [Architecture](docs/architecture.md) | Data flow, package responsibilities, collector design |

---

## Stretch ideas

- Feed from `Files.lines()` to show lazy I/O  
- `CONCURRENT` collector on `ConcurrentHashMap`  
- Hand-rolled `reduce` vs `groupingBy`  
- Scale to 10M orders and chart sequential vs parallel throughput  

---

## References

- [Stream (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html)  
- [Collectors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)  
- [Collector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html)  

---

## License

MIT