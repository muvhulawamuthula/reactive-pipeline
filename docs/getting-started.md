# Getting started

Run the e-commerce Stream pipelines locally and interpret the console output.

---

## Prerequisites

| Tool | Version |
|---|---|
| JDK | **21** or newer (`maven.compiler.source/target` = 21) |
| Maven | **3.8+** |

Verify:

```bash
java -version
mvn -version
```

No application dependencies are declared in [`pom.xml`](../pom.xml) — only the `exec-maven-plugin` (3.1.0) for running the main class.

---

## Clone and run

```bash
git clone https://github.com/muvhulawamuthula/reactive-pipeline.git
cd reactive-pipeline
mvn compile exec:java -Dexec.mainClass="com.pipeline.Main"
```

Quiet Maven noise:

```bash
mvn -q compile exec:java -Dexec.mainClass="com.pipeline.Main"
```

IDE: open the Maven project, run `com.pipeline.Main`.

---

## What runs

[`Main`](../src/main/java/com/pipeline/Main.java):

1. `new OrderGenerator(42).generate(10_000)` — fixed seed, reproducible dataset  
2. `BasicPipeline` → returns `SalesReport`, calls `print()`  
3. `FlatMapPipeline.run`  
4. `CollectorsPipeline.run`  
5. `CustomCollectorPipeline.run`  
6. `ParallelPipeline.run`  

All pipelines share the **same** in-memory list.

---

## Expected output (shape)

Exact money and timing values depend on the JVM and CPU; structure is stable.

```
Orders generated : 10,000

=== Basic Pipeline ===

--- Pipeline stage counters (proves lazy evaluation) ---
1. Entered pipeline  : 10,000
2. Passed filter     : <active non-empty>
3. Passed map        : <same as after filter>
Total revenue        : £…
Revenue by category (sorted):
  Electronics     → £…
  …

=== FlatMap Pipeline ===
--- Stage counters ---
Orders into pipeline    : 10,000
Line items produced     : <from non-cancelled orders>
Avg items per order     : …
Top 10 products by revenue:
  …
--- Revenue + quantity by category ---
  …
--- Most expensive single item per category ---
  …

=== Advanced Collectors Pipeline ===
--- Collectors.teeing ... ---
--- Collectors.partitioningBy ... ---
--- Multi-level groupingBy ... ---
--- groupingBy + teeing downstream ... ---
--- Top 5 customers by lifetime spend ---
--- Collectors.joining (CSV category summary) ---
category,revenue,items
…

=== Custom Collector Result ===
Total revenue / orders / avg / top category / highest order
Revenue by tier / category
Sequential time / Parallel time
Correctness check (✓ matches)

=== Parallel Pipeline Benchmark ===
Cores available : N
--- Results (averaged over 5 runs) ---
Sequential / Parallel / Speedup
--- Parallel safety checks ---
--- Custom ForkJoinPool ---
--- When parallel hurts (small data) ---
```

### How to read the counters

| Counter | Meaning |
|---|---|
| Entered pipeline = 10,000 | Every generated order hits the first `peek` |
| Passed filter &lt; 10,000 | Cancelled (~12%) and empty carts removed |
| Passed map = passed filter | `map` is not applied to filtered-out elements (laziness) |

FlatMap “line items produced” counts items from **non-cancelled** orders only (filter before `flatMap`). Average uses `lineItems / orders into pipeline` (including cancelled in the denominator as coded).

---

## Common issues

**`invalid target release: 21`**  
Install JDK 21+ and point `JAVA_HOME` at it.

**`exec:java` / main class not found**  
Run `mvn compile` first, or use the full command above so compile runs before exec.

**Parallel section looks “wrong” (list sizes, speedups)**  
Expected: the unsafe `ArrayList` demo may show mismatched sizes or throw; speedups vary by core count. Small-data section often shows parallel **slower** than sequential.

**Want a different volume**  
Change `generator.generate(10_000)` in `Main` (or the seed) and re-run. Pipelines accept any `List<Order>`.

---

## Next reading

- [Architecture](architecture.md) — package boundaries, collector design, data flow  
- Source entry points linked from the [README](../README.md)