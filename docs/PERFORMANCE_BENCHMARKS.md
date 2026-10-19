# InvestOps — Performance Benchmarks Report (Phase 6A)

> **Investment Operations & Trade Reconciliation Platform**  
> Empirical Benchmark Report · OpenJDK 17 & PostgreSQL 16 · October 2026

---

## 1. Executive Summary

This report documents the performance, scalability characteristics, correctness guarantees, and algorithmic bottlenecks of the **InvestOps Reconciliation Engine** under synthetic trade workloads ranging from **1,000 to 100,000 trades per source** (up to **198,000 total input trades** per execution).

### Key Highlights
- **High-Throughput Matching Engine:** The pure in-memory reconciliation engine achieves **253,000 to 292,000 trades/sec** on 50k–100k trade volumes, completing the reconciliation of 198,000 trades in **780 ms** on standard commodity hardware.
- **Strict Separation of Concerns:** Engine execution time is strictly decoupled from end-to-end database I/O, entity persistence, and audit logging to avoid misleading aggregation metrics.
- **100% Deterministic & Mathematically Precise:** Benchmark results are completely reproducible across repeated iterations via fixed pseudo-random seeding (`133742L`). Precision tests confirm zero double-matching, zero silent trade loss, preservation of ambiguous duplicate keys, and exact `BigDecimal` financial arithmetic.
- **Clear Identification of Bottlenecks:** At 10,000 trades/source, pure matching consumes only **0.4%** of end-to-end reconciliation latency (100 ms). **95.9%** of end-to-end time (23.95 seconds) is dominated by individual JPA entity instantiation and audit trail logging in `ExceptionService.createExceptionsForResults`.

---

## 2. Test Environment & System Specifications

All benchmarks were executed on a dedicated local development workstation under consistent conditions with background services stabilized.

| Component | Specification |
|---|---|
| **Operating System** | Windows 11 Home (64-bit, Build 26100) |
| **Processor** | 13th Gen Intel(R) Core(TM) i5-13420H (8 Cores: 4 Performance + 4 Efficient, 12 Threads, Base 2.10 GHz, Turbo up to 4.60 GHz) |
| **System Memory** | 16.0 GB DDR5 RAM (LPDDR5 / 4800 MHz) |
| **Java Virtual Machine** | Eclipse Temurin OpenJDK 64-Bit Server VM (`build 17.0.13+11`, mixed mode, sharing) |
| **JVM Flags** | Default Spring Boot test profile, garbage collector: G1GC |
| **Relational Database** | PostgreSQL 16.3 (Debian container run via Docker Engine) |
| **Connection Pool** | HikariCP (Maximum pool size: 10 connections) |
| **ORM / Framework** | Spring Boot 3.3.4, Spring Data JPA / Hibernate ORM 6.5.3.Final |

---

## 3. Benchmark Methodology & Dataset Design

### 3.1 Synthetic Dataset Generation (`SyntheticTradeGenerator`)
To reflect production financial operations while remaining 100% reproducible, the synthetic trade generator uses a fixed seed (`133742L`) and creates trades satisfying all domain rules defined in `docs/ARCHITECTURE.md`.

Each dataset size ($N$ trades per source) synthesizes a realistic distribution of trade outcomes:

| Category | Proportion | Generator Logic & Characteristics | Expected Reconciliation Outcome |
|---|---|---|---|
| **Exact Match** | 70% | Identical account, security (ISIN/CUSIP), side, quantity, price, trade date, and settlement date. | `MATCHED` |
| **Quantity Mismatch** | 10% | Identical security and price; System B quantity differs by +5 to +50 units. | `QUANTITY_MISMATCH` |
| **Price Difference** | 10% | Identical security and quantity; System B price differs by +0.05 to +5.00 (> $0.01 tolerance). | `PRICE_DIFFERENCE` |
| **Settlement Date Mismatch**| 5% | Identical security, price, quantity; System B settlement date offset by +1 to +3 business days. | `SETTLEMENT_DATE_MISMATCH` |
| **Missing in System A** | 2% | Trade exists solely in System B (external trade ID prefixed `TRD-MSB-`). | `UNMATCHED_SYSTEM_B` |
| **Missing in System B** | 2% | Trade exists solely in System A (external trade ID prefixed `TRD-MSA-`). | `UNMATCHED_SYSTEM_A` |
| **Duplicate Candidate Key** | 1% | System A contains two trades sharing the same normalized business key (`TRD-DUP-%06d` vs `TRD-DUP-%d`). | `DUPLICATE_TRADE_ID` |

#### Key Normalization Testing
In financial transaction feeds, System A may send padded numeric identifiers (`TRD-EX-000123`) while System B strips leading zeros (`TRD-EX-123`). The generator models this asymmetric formatting to exercise the engine's key normalization logic (`normalizeBusinessKey`) under high volume.

### 3.2 Timing & Measurement Protocol
- **JVM Warmup:** Every benchmark scenario executes **3 unmeasured warmup cycles** to allow HotSpot JIT (C1/C2 compilers) to inline hot methods, unroll loops, and optimize virtual dispatch tables.
- **Measured Iterations:** **5 timed iterations** are recorded per dataset size.
- **Statistical Metric:** Results report the **Median** execution time in milliseconds, along with Minimum and Maximum observed runtimes.
- **Memory Consumption:** Approximate heap memory delta is recorded by forcing Garbage Collection (`System.gc()`) before and after each measured run and querying `Runtime.getRuntime().totalMemory() - freeMemory()`.

---

## 4. Pure Matching Engine Benchmarks

The pure matching engine benchmark evaluates the in-memory execution of `MatchingEngine.reconcile(systemATrades, systemBTrades)` without database network roundtrips or disk serialization.

### 4.1 Empirical Results Table

| Dataset Size (Per Source) | Total Input Trades | Total Results Generated | Matched Trades | Discrepancies (Breaks) | Warmup Runs | Measured Runs | Min Time (ms) | Median Time (ms) | Max Time (ms) | Throughput (Trades / Sec) | Heap Delta (MB) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **1,000** | 1,980 | 1,010 | 710 | 300 | 3 | 5 | 11.00 | **13.00** | 23.00 | **152,308** | ~18 MB |
| **10,000** | 19,800 | 10,100 | 7,100 | 3,000 | 3 | 5 | 105.00 | **108.00** | 126.00 | **183,333** | ~9 MB |
| **50,000** | 99,000 | 50,500 | 35,500 | 15,000 | 3 | 5 | 316.00 | **338.00** | 354.00 | **292,899** | ~132 MB |
| **100,000** | 198,000 | 101,000 | 71,000 | 30,000 | 3 | 5 | 723.00 | **780.00** | 869.00 | **253,846** | ~252 MB |

*Note: For $N$ trades per source, the exact input count is $2 \times N - 20 \times (N / 1,000)$ because 2% are missing from A and 2% are missing from B.*

### 4.2 Scaling & Complexity Analysis
- **Throughput Profile:** Throughput scales linearly and peaks between **50k and 100k trades** (~290k trades/sec), benefiting from JIT branch prediction and localized cache lines.
- **Latency Linearity:** 
  - $1,000 \to 10,000$ trades ($10\times$ volume): Execution time grows from $13\text{ ms} \to 108\text{ ms}$ ($8.3\times$).
  - $10,000 \to 100,000$ trades ($10\times$ volume): Execution time grows from $108\text{ ms} \to 780\text{ ms}$ ($7.2\times$).
- **Memory Footprint:** At 100,000 trades per source (198,000 input trades and 101,000 `ReconciliationResult` records created in memory), heap consumption is ~252 MB, comfortably within standard container heap limits (512 MB to 2 GB).

---

## 5. End-to-End Database Reconciliation Benchmark

To understand real-world operational characteristics, we measured the full database lifecycle on **10,000 trades per source** (19,800 total input trades, 10,100 reconciliation results, 3,000 breaks) using PostgreSQL 16.

### 5.1 Phase-by-Phase Breakdown

```
┌────────────────────────────────────────────────────────────────────────┐
│ TOTAL END-TO-END RECONCILIATION LATENCY: 24,973 ms (Throughput: 793 t/s)│
├──────────────┬──────────────┬──────────────────┬───────────────────────┤
│ Phase A      │ Phase B      │ Phase C          │ Phase D               │
│ DB Fetch     │ Engine Match │ Results DB Save  │ Exceptions & Audit Log│
│ 185 ms (0.7%)│ 100 ms (0.4%)│ 738 ms (3.0%)    │ 23,950 ms (95.9%)     │
└──────────────┴──────────────┴──────────────────┴───────────────────────┘
```

| Execution Phase | Operation Description | Duration (ms) | % of Total Time | Effective Throughput |
|---|---|---|---|---|
| **Phase A: DB Entity Fetch** | Loading 19,800 `TradeRecord` entities via `tradeRecordRepository.findBySourceSystemAndTradeDateBetween` across 2 queries | 185 ms | 0.7% | 107,027 trades/sec |
| **Phase B: In-Memory Engine** | Multi-rule matching, tolerance checks, break categorization via `MatchingEngine.reconcile` | 100 ms | 0.4% | 198,000 trades/sec |
| **Phase C: Results Persistence** | Batch insertion of 10,100 `ReconciliationResult` records via `reconciliationResultRepository.saveAll` | 738 ms | 3.0% | 13,685 results/sec |
| **Phase D: Exceptions & Audit** | Creating 3,000 `ExceptionRecord` entities and logging 3,000 `AuditLog` events via `ExceptionService` | **23,950 ms** | **95.9%** | **125 exceptions/sec** |
| **Total End-to-End Execution** | Full database-backed reconciliation cycle | **24,973 ms** | **100.0%** | **793 trades/sec** |

---

## 6. Verification of Correctness & Invariants

The benchmark suite includes rigorous programmatic verification (`BenchmarkCorrectnessTest.java`) ensuring that high volume does not compromise domain integrity:

1. **Deterministic Reproducibility:** Repeated reconciliation runs on the generated dataset yield identical counts:
   - Total Results: $10,100$
   - Matches: $7,100$
   - Breaks: $3,000$ (Quantity: $1,000$, Price: $1,000$, Settlement Date: $500$, Missing in A: $200$, Missing in B: $200$, Duplicates: $100$)
2. **Complete Trade Accounting:** Every input trade is accounted for. The sum of matched pairs, single-sided missing trades, duplicate records, and attribute breaks strictly matches the input set with zero dropped records.
3. **Duplicate Ambiguity Preservation:** Multiple trades sharing the same normalized external trade ID in one source are flagged with break type `DUPLICATE_TRADE_ID` and marked unresolved, preventing arbitrary or non-deterministic pairing.
4. **Financial Arithmetic Precision:** Calculations preserve `BigDecimal` scales (e.g. quantity scaled to 4 decimals, price scaled to 4 decimals). Price tolerance comparison ($|P_A - P_B| \le 0.01$) functions without IEEE-754 binary floating-point drift.
5. **Existing Regression Suite:** All existing backend tests (`AuthControllerIntegrationTest`, `TradeImportControllerIntegrationTest`, `ReconciliationControllerIntegrationTest`, `ExceptionControllerIntegrationTest`, `AuditControllerIntegrationTest`, `TradeParserTest`) pass with zero regressions.

---

## 7. Bottleneck Identification & Architectural Analysis

Empirical profiling revealed three distinct architectural bottlenecks:

### 1. Primary Bottleneck: Exception Creation & Audit Logging (95.9% of E2E Time)
- **Observed Behavior:** In `ExceptionService.createExceptionsForResults`, each discrepancy result (3,000 in the 10k benchmark) triggers:
  1. Instantiation of an `ExceptionRecord`.
  2. Persistence via `exceptionRecordRepository.save(exception)`.
  3. Immediate synchronous invocation of `auditService.logEvent("EXCEPTION", ...)` which executes another `auditLogRepository.save(auditLog)`.
- **Impact:** For 3,000 breaks, this performs **6,000 sequential JPA entity lifecycle operations** and insert statements inside an open Hibernate persistence context.
- **Architectural Recommendation for Future Phases:**
  - Transition `ExceptionService` to bulk persistence: accumulate `ExceptionRecord` and `AuditLog` instances into batches and persist using `saveAll(...)` or Spring Data JDBC batch inserts with configured `hibernate.jdbc.batch_size=500`.
  - Decouple audit logging to an asynchronous event listener (`@Async` or `@TransactionalEventListener(phase = AFTER_COMMIT)`).

### 2. Secondary Bottleneck: TreeSet Node Allocations in Matching Engine
- **Observed Behavior:** `MatchingEngine` creates a `TreeSet<String>` of unique business keys across both sources to guarantee deterministic ordering:
  ```java
  Set<String> allKeys = new TreeSet<>();
  allKeys.addAll(mapA.keySet());
  allKeys.addAll(mapB.keySet());
  ```
- **Impact:** Inserting $N$ keys into a Red-Black tree has $O(N \log N)$ complexity and allocates an internal `TreeMap.Entry` node for each unique key (~100,000 tree nodes at 100k scale).
- **Architectural Recommendation:** Retain a `LinkedHashSet` initialized with key set of Map A followed by Map B, or sort keys in an array/list once after collection.

### 3. Key Normalization Regex Compilation
- **Observed Behavior:** `normalizeBusinessKey` strips leading zeros using `s.replaceAll("^(0+)(?!$)", "")`.
- **Impact:** `replaceAll` compiles a new regex `Pattern` on every invocation (200,000 compiles at 100k scale).
- **Architectural Recommendation:** Cache a precompiled `static final Pattern LEADING_ZEROS = Pattern.compile("^(0+)(?!$)");` or perform character-level index slicing.

---

## 8. Reproducing the Benchmarks

All benchmark utilities and tests are fully committed to the repository and can be run via Maven without any special configuration.

### 8.1 Run the Pure Matching Engine Benchmark (CLI)
Executes JVM warmups, runs 1k, 10k, 50k, and 100k trade benchmarks, and prints throughput and memory metrics:

```bash
cd backend
.\mvnw.cmd test-compile exec:java -Dexec.mainClass="com.investops.benchmark.MatchingEngineBenchmarkRunner" -Dexec.classpathScope="test"
```

### 8.2 Run Correctness Verification Tests
Validates determinism, trade accounting, duplicate ambiguity handling, and decimal precision:

```bash
cd backend
.\mvnw.cmd test -Dtest=BenchmarkCorrectnessTest
```

### 8.3 Run End-to-End Database Reconciliation Benchmark
Runs the multi-phase database benchmark (DB Fetch $\to$ Engine Match $\to$ Results Persist $\to$ Exceptions/Audit):

```bash
cd backend
.\mvnw.cmd test -Dtest=EndToEndReconciliationBenchmarkTest
```

### 8.4 Run the Complete Backend Test Suite
Ensures that all domain, integration, and benchmark tests pass cleanly:

```bash
cd backend
.\mvnw.cmd test
```
