# InvestOps — Architecture Document

> **Investment Operations & Trade Reconciliation Platform**
> Version 1.0 · October 2026

---

## Table of Contents

1. [System Architecture Overview](#1-system-architecture-overview)
2. [Database Design](#2-database-design)
3. [REST API Contract](#3-rest-api-contract)
4. [Reconciliation Engine](#4-reconciliation-engine)
5. [Folder Structure](#5-folder-structure)
6. [Implementation Plan](#6-implementation-plan)
7. [Assumptions & Technical Risks](#7-assumptions--technical-risks)

---

## 1. System Architecture Overview

### 1.1 Architectural Style — Modular Monolith

InvestOps is a **modular monolith**: a single deployable unit whose internals are organized
into cohesive, loosely-coupled modules with well-defined boundaries. This is a deliberate
choice over microservices for several reasons:

| Concern | Modular Monolith | Microservices |
|---|---|---|
| Operational complexity | Single process, single DB | Multiple processes, service discovery, distributed tracing |
| Data consistency | Local ACID transactions | Sagas / eventual consistency |
| Latency | In-process method calls | Network hops between services |
| Team size fit | Solo / small team | Large, autonomous teams |
| Interview defensibility | Clear reasoning against premature distribution | Hard to justify without scale evidence |

**Key design principle:** Modules communicate through *Java interfaces*, not HTTP or message
queues. Each module owns its own slice of the database schema. When the system genuinely
needs to scale, we can extract a module into a service by replacing the in-process interface
with a Kafka consumer — the module's internal code doesn't change.

### 1.2 Module Decomposition

```
┌─────────────────────────────────────────────────────────┐
│                    API Gateway Layer                     │
│         (Spring Security + REST Controllers)            │
├──────────┬──────────┬──────────┬────────────┬───────────┤
│  Trade   │  Recon   │Exception │   Auth &   │ Dashboard │
│  Import  │  Engine  │ Manager  │   RBAC     │  & Stats  │
│  Module  │  Module  │  Module  │  Module    │  Module   │
├──────────┴──────────┴──────────┴────────────┴───────────┤
│              Shared Kernel (DTOs, Events, Utils)         │
├─────────────────────────────────────────────────────────┤
│                Spring Data JPA / Flyway                  │
├─────────────────────────────────────────────────────────┤
│                      PostgreSQL                         │
└─────────────────────────────────────────────────────────┘
```

### 1.3 Module Responsibilities

#### Trade Import Module
- Parses CSV files from two simulated financial systems (System A and System B).
- Validates each row against schema rules (required fields, numeric formats, valid dates).
- Persists valid records and rejects invalid rows with detailed error messages.
- Guarantees **idempotent imports** using a unique constraint on `(source_system, external_trade_id)`.
  If a CSV is uploaded twice, duplicates are skipped — not rejected — so the import is safe
  to retry after a network failure.

#### Reconciliation Engine Module
- Accepts a reconciliation request specifying which two sources and trade-date range to compare.
- Executes deterministic, rule-based matching (see §4).
- Produces a `ReconciliationRun` record with timestamped results.
- Creates `Exception` records for every discrepancy found.
- Is **stateless and re-runnable**: running reconciliation twice on the same data produces
  identical results (the previous run's exceptions are archived, not duplicated).

#### Exception Manager Module
- Provides CRUD operations on exceptions (discrepancies).
- Tracks lifecycle: `OPEN → ACKNOWLEDGED → IN_REVIEW → RESOLVED → CLOSED`.
- Supports assignment to users, severity levels, and threaded comments.
- Maintains an immutable **audit log** for every state change (who, when, what changed).

#### Auth & RBAC Module
- Two roles: `ANALYST` (view, comment, update exceptions) and `ADMIN` (all permissions +
  user management + trigger reconciliation + import trades).
- Uses Spring Security with JWT tokens stored in `HttpOnly` cookies.
- Passwords hashed with bcrypt (cost factor 12).
- Token refresh mechanism with short-lived access tokens (15 min) and longer refresh
  tokens (7 days).

#### Dashboard & Statistics Module
- Aggregation queries for dashboard cards: total trades, match rate, open exceptions by
  severity, recent reconciliation runs.
- Time-series data for reconciliation trend charts.
- Read-only; no write operations.

### 1.4 Cross-Cutting Concerns

| Concern | Implementation |
|---|---|
| **Logging** | SLF4J + Logback; structured JSON in production; correlation IDs via MDC |
| **Error handling** | Global `@ControllerAdvice` returning RFC 7807 Problem Detail responses |
| **Validation** | Jakarta Bean Validation (`@Valid`) at the controller boundary; business validation in services |
| **Pagination** | Spring Data `Pageable`; cursor-based pagination available for large result sets |
| **API versioning** | URI prefix `/api/v1/`; version in path, not headers, for simplicity |
| **CORS** | Configured via Spring Security for the React dev server origin |
| **OpenAPI docs** | springdoc-openapi auto-generates Swagger UI at `/swagger-ui.html` |
| **Concurrency** | Optimistic locking via `@Version` on `TradeException` to prevent lost updates when multiple analysts edit the same exception |

### 1.5 Kafka-Ready Design (Without Kafka)

The architecture is explicitly designed so Kafka can be introduced later without refactoring
business logic:

1. **Domain events are plain Java objects.** When reconciliation finds a discrepancy, it
   creates an `ExceptionCreatedEvent` POJO. Today, this is published via Spring's
   `ApplicationEventPublisher` (in-process). Tomorrow, a Kafka producer adapter can serialize
   it to a topic — the reconciliation engine doesn't change.

2. **CSV import could become a Kafka consumer.** The import service accepts a `TradeRecord`
   DTO. Today that DTO comes from CSV parsing. A Kafka consumer could produce the same DTO
   from a message payload.

3. **No distributed transactions are needed today.** Everything runs in a single PostgreSQL
   transaction. This is intentional — it means we don't have to solve distributed consistency
   until we actually have distributed components.

### 1.6 Data Flow

```
  ┌──────────┐     CSV Upload       ┌──────────────────┐
  │  User /  │ ────────────────────► │  Trade Import    │
  │  Client  │                       │  Service         │
  └────┬─────┘                       └───────┬──────────┘
       │                                     │ validate & persist
       │                                     ▼
       │                              ┌──────────────┐
       │                              │  PostgreSQL  │
       │                              │  trade_record│
       │                              └──────┬───────┘
       │  POST /reconciliation/runs          │
       │ ──────────────────────────────►┌─────┴──────────┐
       │                                │  Recon Engine  │
       │                                │  (2-pass algo) │
       │                                └─────┬──────────┘
       │                                      │ results + exceptions
       │                                      ▼
       │                               ┌──────────────┐
       │  GET /exceptions              │  PostgreSQL  │
       │ ◄─────────────────────────────│  exception   │
       │                               │  recon_result│
       │  PATCH /exceptions/{id}       └──────────────┘
       │ ──────────────────────────────►      │
       │                                     │ audit_log entry
       │  GET /dashboard/summary             ▼
       │ ◄────────────────────────── aggregation queries
```

---

## 2. Database Design

### 2.1 Entity-Relationship Diagram

```
┌──────────────────┐       ┌──────────────────────┐
│    app_user       │       │    trade_record       │
├──────────────────┤       ├──────────────────────┤
│ id (PK, UUID)    │       │ id (PK, UUID)        │
│ username (UQ)    │       │ external_trade_id     │
│ email (UQ)       │       │ source_system (ENUM)  │
│ password_hash    │       │ trade_date            │
│ role (ENUM)      │       │ settlement_date       │
│ active           │       │ symbol                │
│ created_at       │       │ side (ENUM)           │
│ updated_at       │       │ quantity  NUMERIC(18,8)│
│                  │       │ price     NUMERIC(18,8)│
└──────────────────┘       │ counterparty          │
        │                  │ status (ENUM)         │
        │                  │ import_batch_id (FK)  │
        │                  │ created_at            │
        │                  │ UQ(source_system,     │
        │                  │    external_trade_id) │
        │                  └──────────┬───────────┘
        │                             │
        │    ┌────────────────────────┘
        │    │
        │    │     ┌──────────────────────────┐
        │    │     │   reconciliation_run      │
        │    │     ├──────────────────────────┤
        │    │     │ id (PK, UUID)            │
        │    │     │ run_date                 │
        │    │     │ trade_date_from          │
        │    │     │ trade_date_to            │
        │    │     │ source_a                 │
        │    │     │ source_b                 │
        │    │     │ status (ENUM)            │
        │    │     │ total_compared           │
        │    │     │ matched_count            │
        │    │     │ break_count              │
        │    │     │ price_tolerance          │  ← recorded for reproducibility
        │    │     │ initiated_by (FK→user)   │
        │    │     │ started_at               │
        │    │     │ completed_at             │
        │    │     └─────────┬────────────────┘
        │    │               │
        │    │     ┌─────────┴────────────────┐
        │    │     │  reconciliation_result    │
        │    │     ├──────────────────────────┤
        │    │     │ id (PK, UUID)            │
        │    │     │ run_id (FK→recon_run)    │
        │    │     │ trade_a_id (FK→trade)    │  ← nullable (missing in A)
        │    │     │ trade_b_id (FK→trade)    │  ← nullable (missing in B)
        │    │     │ match_status (ENUM)      │
        │    │     │ created_at               │
        │    │     └─────────┬────────────────┘
        │    │               │
        │    │     ┌─────────┴────────────────┐
        │    │     │   trade_exception         │
        │    │     ├──────────────────────────┤
        │    │     │ id (PK, UUID)            │
        │    │     │ result_id (FK→result)    │
        │    │     │ run_id (FK→recon_run)    │
        │    │     │ exception_type (ENUM)    │
        │    │     │ severity (ENUM)          │
        │    │     │ status (ENUM)            │
        │    │     │ field_name               │  ← which field differs
        │    │     │ expected_value           │
        │    │     │ actual_value             │
        │    │     │ assigned_to (FK→user)    │  ← nullable
        │    │     │ version (int)            │  ← optimistic locking
        │    │     │ created_at               │
        │    │     │ updated_at               │
        │    │     └─────────┬────────────────┘
        │                    │
        │    ┌───────────────┼───────────────┐
        │    │               │               │
        │    ▼               ▼               │
┌────────────────┐  ┌──────────────────┐     │
│exception_comment│  │exception_audit_log│    │
├────────────────┤  ├──────────────────┤     │
│ id (PK, UUID)  │  │ id (PK, UUID)    │     │
│ exception_id   │  │ exception_id     │     │
│ author_id (FK) │  │ actor_id (FK)    │     │
│ content        │  │ action           │     │
│ created_at     │  │ field_changed    │     │
│                │  │ old_value        │     │
│                │  │ new_value        │     │
│                │  │ created_at       │     │
└────────────────┘  └──────────────────┘     │
                                             │
                    ┌────────────────────┐    │
                    │   import_batch     │    │
                    ├────────────────────┤    │
                    │ id (PK, UUID)     │    │
                    │ filename          │    │
                    │ source_system     │    │
                    │ uploaded_by (FK)  │    │
                    │ total_rows        │    │
                    │ valid_rows        │    │
                    │ skipped_rows      │    │
                    │ failed_rows       │    │
                    │ status (ENUM)     │    │
                    │ error_details (JSONB)│ │
                    │ file_hash (UQ)    │    │
                    │ created_at        │    │
                    └────────────────────┘    │
```

### 2.2 Enum Definitions

```sql
-- Source systems
CREATE TYPE source_system AS ENUM ('SYSTEM_A', 'SYSTEM_B');

-- Trade side
CREATE TYPE trade_side AS ENUM ('BUY', 'SELL');

-- Trade record status (within import lifecycle)
CREATE TYPE trade_status AS ENUM ('PENDING', 'VALIDATED', 'REJECTED');

-- Reconciliation run status
CREATE TYPE run_status AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED');

-- Match result for a pair
CREATE TYPE match_status AS ENUM ('MATCHED', 'UNMATCHED', 'MISMATCHED');

-- Exception classification
CREATE TYPE exception_type AS ENUM (
  'MISSING_IN_SOURCE_A',
  'MISSING_IN_SOURCE_B',
  'DUPLICATE_TRADE',
  'QUANTITY_MISMATCH',
  'PRICE_DIFFERENCE',
  'SETTLEMENT_DATE_MISMATCH',
  'MULTI_FIELD_MISMATCH'
);

-- Severity (drives SLA and dashboard ordering)
CREATE TYPE severity_level AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- Exception lifecycle
CREATE TYPE exception_status AS ENUM (
  'OPEN',
  'ACKNOWLEDGED',
  'IN_REVIEW',
  'RESOLVED',
  'CLOSED'
);

-- User role
CREATE TYPE user_role AS ENUM ('ANALYST', 'ADMIN');

-- Import batch status
CREATE TYPE batch_status AS ENUM ('PROCESSING', 'COMPLETED', 'FAILED');
```

### 2.3 Critical Constraints

| Constraint | Purpose | Interview Talking Point |
|---|---|---|
| `UNIQUE (source_system, external_trade_id)` on `trade_record` | Guarantees idempotent CSV imports. Uploading the same file twice silently skips duplicates (`ON CONFLICT DO NOTHING`). | Idempotency is essential in financial systems — retries must be safe. |
| `UNIQUE (file_hash)` on `import_batch` | Prevents the exact same file from being registered as a new batch. Uses SHA-256 of file contents. | Defense-in-depth: even if row-level dedup catches duplicates, we detect identical files at the batch level too. |
| `CHECK (quantity > 0)` on `trade_record` | Domain invariant — a trade must have a positive quantity. | Pushing validation to the database means bugs in application code can't corrupt data. |
| `CHECK (price > 0)` on `trade_record` | Domain invariant — price must be positive. | Same principle as quantity. |
| `NOT NULL` on `trade_record.external_trade_id` | Every trade must have an ID from its source system. | Without this, we can't match trades across systems. |
| Foreign keys with `ON DELETE RESTRICT` | Prevents orphaned records. You can't delete a trade that has reconciliation results. | Referential integrity matters more than convenience in financial applications. |
| `trade_record.settlement_date >= trade_record.trade_date` | Settlement can't precede execution. | Simple domain rule enforced at the database level. |
| `version` column on `trade_exception` | Optimistic locking. Two analysts editing the same exception get a concurrency error instead of a silent overwrite. | Standard pattern in multi-user financial systems. |

### 2.4 Indexing Strategy

```sql
-- Most queries filter by source system and date range
CREATE INDEX idx_trade_source_date ON trade_record (source_system, trade_date);

-- Reconciliation matches by external_trade_id
CREATE INDEX idx_trade_external_id ON trade_record (external_trade_id);

-- Exception dashboard filters by status and severity
CREATE INDEX idx_exception_status_severity ON trade_exception (status, severity);

-- Exception queries filtered by reconciliation run
CREATE INDEX idx_exception_run ON trade_exception (run_id);

-- Audit log lookups by exception
CREATE INDEX idx_audit_exception ON exception_audit_log (exception_id, created_at);

-- Import batch lookup by file hash (for duplicate detection)
CREATE INDEX idx_batch_file_hash ON import_batch (file_hash);
```

### 2.5 Why `NUMERIC` for Price, Not `DOUBLE`

`trade_record.price` and `trade_record.quantity` use `NUMERIC(18,8)`, **not** `DOUBLE
PRECISION`. Floating-point arithmetic produces rounding errors (e.g., `0.1 + 0.2 ≠ 0.3`),
which would cause false reconciliation breaks. In Java, these map to `BigDecimal`.

> **Why `BigDecimal.compareTo()`, not `equals()`?** In Java, `new BigDecimal("1.0").equals(new BigDecimal("1.00"))`
> returns `false` because `equals()` compares scale. `compareTo()` compares mathematical
> value, which is what we want in reconciliation. This is a common source of bugs that must
> be enforced via code review and unit tests.

---

## 3. REST API Contract

All endpoints are prefixed with `/api/v1`. Responses follow a consistent envelope:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "2026-10-16T00:00:00Z"
}
```

Error responses use RFC 7807 Problem Detail:

```json
{
  "type": "https://investops.example.com/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "3 fields failed validation",
  "errors": [
    { "field": "quantity", "message": "must be positive" }
  ]
}
```

### 3.1 Authentication

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Authenticate, returns JWT in `HttpOnly` cookie |
| `POST` | `/auth/refresh` | Authenticated | Refresh access token |
| `POST` | `/auth/logout` | Authenticated | Invalidate refresh token |

**Request — POST /auth/login**
```json
{ "username": "analyst1", "password": "••••••" }
```

**Response — 200 OK**
```json
{
  "userId": "uuid",
  "username": "analyst1",
  "role": "ANALYST",
  "accessToken": "eyJ..."
}
```
The `accessToken` is also set as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie.

### 3.2 Trade Import

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/trades/import` | ADMIN | Upload CSV file for a given source system |
| `GET` | `/trades` | ANALYST, ADMIN | List trades with filtering and pagination |
| `GET` | `/trades/{id}` | ANALYST, ADMIN | Get single trade detail |
| `GET` | `/trades/import/batches` | ADMIN | List import batch history |
| `GET` | `/trades/import/batches/{id}` | ADMIN | Get batch detail with row-level errors |

**Request — POST /trades/import**
```
Content-Type: multipart/form-data
file: trades_system_a_20261015.csv
sourceSystem: SYSTEM_A
```

**Response — 201 Created**
```json
{
  "batchId": "uuid",
  "filename": "trades_system_a_20261015.csv",
  "totalRows": 1500,
  "validRows": 1487,
  "skippedRows": 8,
  "failedRows": 5,
  "errors": [
    { "row": 42, "field": "quantity", "message": "not a valid number", "rawValue": "abc" },
    { "row": 99, "field": "settlement_date", "message": "precedes trade_date" }
  ]
}
```

**Query Parameters — GET /trades**
```
?sourceSystem=SYSTEM_A
&tradeDateFrom=2026-10-01
&tradeDateTo=2026-10-15
&symbol=AAPL
&side=BUY
&page=0
&size=25
&sort=tradeDate,desc
```

### 3.3 Reconciliation

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/reconciliation/runs` | ADMIN | Trigger a new reconciliation run |
| `GET` | `/reconciliation/runs` | ANALYST, ADMIN | List past runs |
| `GET` | `/reconciliation/runs/{id}` | ANALYST, ADMIN | Run detail with summary stats |
| `GET` | `/reconciliation/runs/{id}/results` | ANALYST, ADMIN | Paginated match/break results |

**Request — POST /reconciliation/runs**
```json
{
  "tradeDateFrom": "2026-10-01",
  "tradeDateTo": "2026-10-15",
  "sourceA": "SYSTEM_A",
  "sourceB": "SYSTEM_B"
}
```

**Response — 202 Accepted**
```json
{
  "runId": "uuid",
  "status": "RUNNING",
  "message": "Reconciliation initiated for 2026-10-01 to 2026-10-15"
}
```

> **Why 202?** Reconciliation may take seconds to minutes depending on volume. Returning
> 202 signals that processing has begun. The client polls `GET /runs/{id}` for completion.
> This also means adding Kafka later doesn't change the API contract.

### 3.4 Exception Management

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/exceptions` | ANALYST, ADMIN | List exceptions with filtering |
| `GET` | `/exceptions/{id}` | ANALYST, ADMIN | Exception detail with audit trail |
| `PATCH` | `/exceptions/{id}` | ANALYST, ADMIN | Update status, severity, or assignee |
| `POST` | `/exceptions/{id}/comments` | ANALYST, ADMIN | Add a comment |
| `GET` | `/exceptions/{id}/audit-log` | ANALYST, ADMIN | View full audit history |

**Query Parameters — GET /exceptions**
```
?status=OPEN,ACKNOWLEDGED
&severity=HIGH,CRITICAL
&type=QUANTITY_MISMATCH
&assignedTo=uuid
&runId=uuid
&page=0
&size=25
&sort=severity,desc
```

**Request — PATCH /exceptions/{id}**
```json
{
  "status": "IN_REVIEW",
  "assignedTo": "uuid-of-analyst",
  "severity": "HIGH"
}
```
Every field is optional. Only provided fields are updated. Every change creates an
`exception_audit_log` entry automatically. The `version` field from the GET response must
be included for optimistic locking — if someone else updated the exception since you last
read it, the server returns `409 Conflict`.

### 3.5 Dashboard & Statistics

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/dashboard/summary` | ANALYST, ADMIN | Aggregate stats for dashboard cards |
| `GET` | `/dashboard/trends` | ANALYST, ADMIN | Time-series data for charts |

**Response — GET /dashboard/summary**
```json
{
  "totalTrades": 15230,
  "totalReconciled": 14800,
  "matchRate": 96.7,
  "openExceptions": 127,
  "exceptionsBySeverity": {
    "CRITICAL": 3,
    "HIGH": 24,
    "MEDIUM": 58,
    "LOW": 42
  },
  "exceptionsByType": {
    "QUANTITY_MISMATCH": 45,
    "PRICE_DIFFERENCE": 38,
    "MISSING_IN_SOURCE_A": 22,
    "MISSING_IN_SOURCE_B": 15,
    "SETTLEMENT_DATE_MISMATCH": 7
  },
  "lastRunAt": "2026-10-15T18:30:00Z",
  "lastRunMatchRate": 97.2
}
```

### 3.6 User Management

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/users` | ADMIN | List all users |
| `POST` | `/users` | ADMIN | Create a new user |
| `PATCH` | `/users/{id}` | ADMIN | Update user role or active status |
| `GET` | `/users/me` | Authenticated | Get current user profile |

---

## 4. Reconciliation Engine

### 4.1 Matching Algorithm

The reconciliation engine uses a **two-pass deterministic algorithm**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        RECONCILIATION FLOW                          │
│                                                                     │
│  Input: Trades from Source A and Source B for a given date range     │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────┐      │
│  │ PASS 1 — BUILD MATCH INDEX                                │      │
│  │                                                           │      │
│  │  For each trade in Source A:                               │      │
│  │    key = normalize(external_trade_id)                     │      │
│  │    index_A[key].add(trade)                                │      │
│  │                                                           │      │
│  │  For each trade in Source B:                               │      │
│  │    key = normalize(external_trade_id)                     │      │
│  │    index_B[key].add(trade)                                │      │
│  └───────────────────────────────────────────────────────────┘      │
│                              │                                      │
│                              ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐      │
│  │ PASS 2 — COMPARE AND CLASSIFY                             │      │
│  │                                                           │      │
│  │  For each key in (keys_A ∪ keys_B):                       │      │
│  │                                                           │      │
│  │    CASE: key in A only        → MISSING_IN_SOURCE_B       │      │
│  │    CASE: key in B only        → MISSING_IN_SOURCE_A       │      │
│  │    CASE: count(A) > 1         → DUPLICATE_TRADE (in A)    │      │
│  │    CASE: count(B) > 1         → DUPLICATE_TRADE (in B)    │      │
│  │    CASE: key in both (1:1)    → COMPARE FIELDS            │      │
│  │      ├── quantity differs     → QUANTITY_MISMATCH         │      │
│  │      ├── price differs > tol  → PRICE_DIFFERENCE          │      │
│  │      ├── settl. date differs  → SETTLEMENT_DATE_MISMATCH  │      │
│  │      ├── multiple differ      → MULTI_FIELD_MISMATCH      │      │
│  │      └── all match            → MATCHED                   │      │
│  └───────────────────────────────────────────────────────────┘      │
│                              │                                      │
│                              ▼                                      │
│  Output: ReconciliationResults + Exceptions                         │
└─────────────────────────────────────────────────────────────────────┘
```

**Time complexity:** O(n + m) where n = |Source A|, m = |Source B|. Pass 1 builds HashMap
indexes in O(n + m). Pass 2 iterates the union of keys in O(n + m). No nested loops.

### 4.2 Matching Key

The **matching key** is `external_trade_id` — the trade identifier assigned by the source
system. This is the natural join key because both systems refer to the same underlying trade
by this ID.

**Normalization:** The key is trimmed, uppercased, and stripped of leading zeros before
comparison. This prevents false breaks caused by formatting differences (`"TRD-00042"` vs
`"TRD-42"`).

### 4.3 Field Comparison Rules

Each comparison is implemented as a separate class implementing a `ComparisonRule` interface.
This follows the **Strategy Pattern** and the **Open/Closed Principle**.

```java
public interface ComparisonRule {
    /**
     * Compare a specific field between two matched trades.
     * @return Optional.empty() if fields match, or a ComparisonBreak describing the mismatch.
     */
    Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB);
}
```

| Field | Comparison Rule | Tolerance | Severity if Broken |
|---|---|---|---|
| `quantity` | Exact match (`BigDecimal.compareTo`) | None — must be identical | **HIGH** |
| `price` | Difference ≤ configurable tolerance | Default: 0.01 (1 cent) | **MEDIUM** if ≤ 0.05, **HIGH** if > 0.05 |
| `settlement_date` | Exact date match | None | **MEDIUM** if ≤ 1 day, **HIGH** if > 1 day |
| `side` | Exact match | None | **CRITICAL** — a BUY/SELL mismatch is a serious error |
| `symbol` | Case-insensitive exact match | None | **CRITICAL** — different instruments |

> **Why is price tolerance configurable?** Financial systems sometimes represent prices at
> different precision levels (e.g., 4 vs 8 decimal places). A tolerance prevents false
> positives from rounding differences while still catching genuine errors. The tolerance is
> stored as a system configuration, not hardcoded. The tolerance used is also recorded on
> each `ReconciliationRun` for reproducibility.

### 4.4 Severity Assignment Logic

Severity is assigned programmatically, not manually, based on the exception type and the
magnitude of the discrepancy:

```java
// Pseudocode
if (type == MISSING_IN_SOURCE_A || type == MISSING_IN_SOURCE_B) {
    severity = HIGH;  // A missing trade could mean failed settlement
}
if (type == QUANTITY_MISMATCH) {
    if (abs(qtyA - qtyB) / max(qtyA, qtyB) > 0.10) {
        severity = CRITICAL;  // >10% quantity difference
    } else {
        severity = HIGH;
    }
}
if (type == PRICE_DIFFERENCE) {
    if (abs(priceA - priceB) > 0.05) severity = HIGH;
    else severity = MEDIUM;
}
if (type == SETTLEMENT_DATE_MISMATCH) {
    daysDiff = abs(ChronoUnit.DAYS.between(dateA, dateB));
    if (daysDiff > 1) severity = HIGH;
    else severity = MEDIUM;
}
if (type == DUPLICATE_TRADE) {
    severity = HIGH;  // Duplicates can cause double settlement
}
```

### 4.5 Edge Cases and How They're Handled

| Edge Case | Handling | Rationale |
|---|---|---|
| **Same trade ID appears 3× in System A** | One `DUPLICATE_TRADE` exception with `expected_value: "1"`, `actual_value: "3"`. All 3 are linked via the result. | We flag the duplication; we don't guess which is "correct". |
| **Trade exists in both A and B, but with different symbols** | `MULTI_FIELD_MISMATCH` at `CRITICAL` severity. | Symbol mismatch likely means the matching key collision is coincidental — this needs human review. |
| **Same trade ID, one BUY one SELL** | `MULTI_FIELD_MISMATCH` at `CRITICAL` severity including the side mismatch. | A side flip is never a rounding error — it's either a data entry mistake or a broken integration. |
| **Empty CSV uploaded** | Import succeeds with 0 valid rows. No error. | An empty file is a valid file; forcing an error would break automation. |
| **Reconciliation with 0 trades in range** | Run completes with `matched_count: 0`, `break_count: 0`. | No trades is not an error condition — the date range might genuinely have no activity. |
| **Re-running reconciliation on same date range** | Creates a new `ReconciliationRun`. Old run and its results are preserved for audit. New exceptions reference the new run. | Immutable history is important for auditing. Old exceptions remain with their status. |
| **Price = 0.00** | Rejected at import. `CHECK (price > 0)` constraint. | Zero-price trades are invalid in our domain. |
| **Future settlement date** | Allowed. Settlement dates are commonly T+1 or T+2. | This is normal in equities. |
| **Null/empty fields in CSV** | Row rejected during import with specific error message. | We validate strictly at the boundary and only persist clean data. |

### 4.6 Determinism Guarantee

The reconciliation engine is deterministic: given the same set of trades in the database,
it always produces the same results. This is achieved by:

1. **No randomness** — no sampling, no probabilistic matching.
2. **Sorted processing** — trades are sorted by `external_trade_id` before comparison.
3. **Tolerance is configurable but fixed per run** — the tolerance used is recorded on the
   `ReconciliationRun` record so results can be reproduced.
4. **No external dependencies** — the engine reads from the database and writes to the
   database. It doesn't call external APIs or depend on wall-clock time.

---

## 5. Folder Structure

### 5.1 Backend (Spring Boot)

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/investops/
│   │   │   ├── InvestOpsApplication.java        ← Spring Boot entry point
│   │   │   │
│   │   │   ├── common/                          ← Shared Kernel
│   │   │   │   ├── config/
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── CorsConfig.java
│   │   │   │   │   ├── OpenApiConfig.java
│   │   │   │   │   └── JacksonConfig.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ApiResponse.java         ← Generic response wrapper
│   │   │   │   │   ├── PagedResponse.java
│   │   │   │   │   └── ErrorResponse.java       ← RFC 7807
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   ├── DuplicateResourceException.java
│   │   │   │   │   └── BusinessValidationException.java
│   │   │   │   ├── event/                       ← Domain events (Kafka-ready)
│   │   │   │   │   ├── DomainEvent.java
│   │   │   │   │   ├── ExceptionCreatedEvent.java
│   │   │   │   │   ├── ReconciliationCompletedEvent.java
│   │   │   │   │   └── TradeImportCompletedEvent.java
│   │   │   │   └── util/
│   │   │   │       ├── CsvParser.java
│   │   │   │       └── HashUtil.java
│   │   │   │
│   │   │   ├── trade/                           ← Trade Import Module
│   │   │   │   ├── controller/
│   │   │   │   │   └── TradeController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── TradeImportService.java
│   │   │   │   │   └── TradeQueryService.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── TradeRecordRepository.java
│   │   │   │   │   └── ImportBatchRepository.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── TradeRecord.java
│   │   │   │   │   └── ImportBatch.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── TradeRecordDto.java
│   │   │   │   │   ├── TradeImportRequest.java
│   │   │   │   │   ├── TradeImportResponse.java
│   │   │   │   │   └── TradeFilterRequest.java
│   │   │   │   ├── mapper/
│   │   │   │   │   └── TradeMapper.java
│   │   │   │   └── validation/
│   │   │   │       └── CsvRowValidator.java
│   │   │   │
│   │   │   ├── reconciliation/                  ← Reconciliation Engine Module
│   │   │   │   ├── controller/
│   │   │   │   │   └── ReconciliationController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── ReconciliationService.java
│   │   │   │   │   └── MatchingEngine.java      ← Core algorithm
│   │   │   │   ├── repository/
│   │   │   │   │   ├── ReconciliationRunRepository.java
│   │   │   │   │   └── ReconciliationResultRepository.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── ReconciliationRun.java
│   │   │   │   │   └── ReconciliationResult.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ReconciliationRunDto.java
│   │   │   │   │   ├── ReconciliationResultDto.java
│   │   │   │   │   └── ReconciliationRequest.java
│   │   │   │   └── rules/                       ← Pluggable comparison rules
│   │   │   │       ├── ComparisonRule.java       ← Interface
│   │   │   │       ├── ComparisonBreak.java      ← Value object for mismatch details
│   │   │   │       ├── QuantityRule.java
│   │   │   │       ├── PriceRule.java
│   │   │   │       ├── SettlementDateRule.java
│   │   │   │       ├── SideRule.java
│   │   │   │       └── SymbolRule.java
│   │   │   │
│   │   │   ├── exceptionmgmt/                   ← Exception Manager Module
│   │   │   │   ├── controller/
│   │   │   │   │   └── ExceptionController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── ExceptionService.java
│   │   │   │   │   └── AuditService.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── ExceptionRepository.java
│   │   │   │   │   ├── ExceptionCommentRepository.java
│   │   │   │   │   └── ExceptionAuditLogRepository.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── TradeException.java      ← Named to avoid java.lang.Exception clash
│   │   │   │   │   ├── ExceptionComment.java
│   │   │   │   │   └── ExceptionAuditLog.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ExceptionDto.java
│   │   │   │   │   ├── ExceptionUpdateRequest.java
│   │   │   │   │   ├── CommentRequest.java
│   │   │   │   │   └── AuditLogDto.java
│   │   │   │   └── mapper/
│   │   │   │       └── ExceptionMapper.java
│   │   │   │
│   │   │   ├── auth/                            ← Auth & RBAC Module
│   │   │   │   ├── controller/
│   │   │   │   │   └── AuthController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthService.java
│   │   │   │   │   ├── JwtService.java
│   │   │   │   │   └── UserService.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── UserRepository.java
│   │   │   │   ├── entity/
│   │   │   │   │   └── AppUser.java             ← Named to avoid Spring's User clash
│   │   │   │   ├── dto/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── LoginResponse.java
│   │   │   │   │   ├── UserDto.java
│   │   │   │   │   └── CreateUserRequest.java
│   │   │   │   └── security/
│   │   │   │       ├── JwtAuthFilter.java
│   │   │   │       └── UserDetailsImpl.java
│   │   │   │
│   │   │   └── dashboard/                       ← Dashboard Module
│   │   │       ├── controller/
│   │   │       │   └── DashboardController.java
│   │   │       ├── service/
│   │   │       │   └── DashboardService.java
│   │   │       └── dto/
│   │   │           ├── DashboardSummaryDto.java
│   │   │           └── TrendDataDto.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       └── db/migration/
│   │           ├── V1__create_users_table.sql
│   │           ├── V2__create_trade_tables.sql
│   │           ├── V3__create_reconciliation_tables.sql
│   │           ├── V4__create_exception_tables.sql
│   │           └── V5__seed_demo_data.sql
│   │
│   └── test/
│       └── java/com/investops/
│           ├── trade/
│           │   ├── service/
│           │   │   └── TradeImportServiceTest.java
│           │   └── controller/
│           │       └── TradeControllerIntegrationTest.java
│           ├── reconciliation/
│           │   ├── service/
│           │   │   └── MatchingEngineTest.java         ← Most critical tests
│           │   └── rules/
│           │       ├── QuantityRuleTest.java
│           │       ├── PriceRuleTest.java
│           │       └── SymbolRuleTest.java
│           ├── exceptionmgmt/
│           │   └── service/
│           │       └── ExceptionServiceTest.java
│           └── auth/
│               └── security/
│                   └── JwtServiceTest.java
│
├── Dockerfile
├── pom.xml
└── .env.example
```

### 5.2 Frontend (React + TypeScript + Vite)

```
frontend/
├── public/
│   └── favicon.svg
├── src/
│   ├── main.tsx                                ← Entry point
│   ├── App.tsx                                 ← Root component, routing
│   ├── vite-env.d.ts
│   │
│   ├── api/                                    ← API client layer
│   │   ├── client.ts                           ← Axios instance with interceptors
│   │   ├── auth.api.ts
│   │   ├── trades.api.ts
│   │   ├── reconciliation.api.ts
│   │   ├── exceptions.api.ts
│   │   └── dashboard.api.ts
│   │
│   ├── components/                             ← Reusable UI components
│   │   ├── layout/
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   └── MainLayout.tsx
│   │   ├── common/
│   │   │   ├── DataTable.tsx
│   │   │   ├── StatusBadge.tsx
│   │   │   ├── SeverityBadge.tsx
│   │   │   ├── FileUpload.tsx
│   │   │   ├── ConfirmDialog.tsx
│   │   │   ├── LoadingSpinner.tsx
│   │   │   └── EmptyState.tsx
│   │   └── charts/
│   │       ├── MatchRateChart.tsx
│   │       ├── ExceptionsByTypeChart.tsx
│   │       └── TrendLineChart.tsx
│   │
│   ├── pages/                                  ← Route-level components
│   │   ├── Dashboard.tsx
│   │   ├── TradeList.tsx
│   │   ├── TradeImport.tsx
│   │   ├── ReconciliationRuns.tsx
│   │   ├── ReconciliationDetail.tsx
│   │   ├── ExceptionList.tsx
│   │   ├── ExceptionDetail.tsx
│   │   ├── UserManagement.tsx
│   │   └── Login.tsx
│   │
│   ├── hooks/                                  ← Custom React hooks
│   │   ├── useAuth.ts
│   │   ├── useTrades.ts
│   │   ├── useExceptions.ts
│   │   └── useDashboard.ts
│   │
│   ├── context/                                ← React context providers
│   │   └── AuthContext.tsx
│   │
│   ├── types/                                  ← TypeScript type definitions
│   │   ├── trade.types.ts
│   │   ├── reconciliation.types.ts
│   │   ├── exception.types.ts
│   │   ├── auth.types.ts
│   │   └── api.types.ts
│   │
│   ├── utils/                                  ← Utilities
│   │   ├── formatters.ts                       ← Date, currency, number formatters
│   │   └── constants.ts
│   │
│   └── styles/
│       ├── index.css                           ← Global styles, design tokens
│       └── variables.css                       ← CSS custom properties
│
├── index.html
├── tsconfig.json
├── vite.config.ts
├── package.json
└── Dockerfile
```

### 5.3 Infrastructure

```
investops/                                      ← Project root
├── backend/                                    ← (structure above)
├── frontend/                                   ← (structure above)
├── docker-compose.yml                          ← PostgreSQL + backend + frontend
├── docker-compose.dev.yml                      ← Dev overrides (hot reload, etc.)
├── .github/
│   └── workflows/
│       ├── ci.yml                              ← Build, test, lint on PR
│       └── cd.yml                              ← Build Docker images on merge to main
├── data/
│   └── sample/
│       ├── system_a_trades.csv                 ← Synthetic demo data
│       └── system_b_trades.csv
├── docs/
│   └── ARCHITECTURE.md                         ← This document
├── .env.example
├── .gitignore
└── README.md
```

---

## 6. Implementation Plan

Each milestone is designed to be **independently testable and demo-able**. Complete one
before starting the next.

### Milestone 1 — Project Scaffolding & Database (Days 1–2)

**Goal:** Running Spring Boot app connected to PostgreSQL with Flyway migrations.

| Task | Deliverable | Test |
|---|---|---|
| Initialize Spring Boot project with dependencies | `pom.xml`, main class | App starts without errors |
| Configure `application.yml` for PostgreSQL | DB connection config | Spring Boot connects to Postgres |
| Write Flyway migrations V1–V4 | SQL migration files | `flyway:migrate` runs cleanly; tables exist |
| Create JPA entities for all tables | Entity classes with annotations | Integration test: entities can be persisted and retrieved |
| Set up Docker Compose for PostgreSQL | `docker-compose.yml` | `docker compose up db` starts Postgres |
| Set up global exception handler | `GlobalExceptionHandler` | Error responses follow RFC 7807 format |

**Demo:** Run the app, hit `/actuator/health`, show tables in pgAdmin.

### Milestone 2 — Trade Import (Days 3–5)

**Goal:** Upload a CSV file and see validated trades in the database.

| Task | Deliverable | Test |
|---|---|---|
| Build CSV parser with row validation | `CsvParser`, `CsvRowValidator` | Unit tests: valid rows parse correctly; invalid rows produce error messages |
| Implement `TradeImportService` with idempotent upsert | Service class | Unit test: importing same CSV twice doesn't create duplicates |
| Implement `ImportBatch` tracking with file hash | Batch recording | Unit test: same file hash is detected and skipped |
| Create `POST /trades/import` endpoint | Controller | Integration test: upload CSV, verify 201 response with correct counts |
| Create `GET /trades` with filtering and pagination | Controller + query | Integration test: filter by source, date range, symbol |
| Generate synthetic CSV demo data | 2 CSV files (~1000 rows each) | Files are well-formed and contain deliberate discrepancies |

**Demo:** Upload CSV via Swagger UI, query trades via API.

### Milestone 3 — Reconciliation Engine (Days 6–8)

**Goal:** Trigger reconciliation and see matched/unmatched results.

| Task | Deliverable | Test |
|---|---|---|
| Implement `MatchingEngine` with two-pass algorithm | Core algorithm class | Unit tests: matched pairs, missing trades, duplicates all detected correctly |
| Implement comparison rules as pluggable `ComparisonRule` classes | Rule classes | Unit tests per rule: quantity, price (with tolerance), settlement date, side, symbol |
| Implement severity assignment logic | Part of engine | Unit test: severity is assigned correctly for each exception type |
| Create `ReconciliationService` orchestrating the flow | Service class | Integration test: full flow from trigger to results in DB |
| Create reconciliation API endpoints | Controller | Integration test: POST run, GET results |
| Publish domain events on completion | `ApplicationEventPublisher` | Event listener test |

**Demo:** Import 2 CSVs, run reconciliation, show results via API.

### Milestone 4 — Exception Management (Days 9–10)

**Goal:** View, update, and comment on exceptions with full audit trail.

| Task | Deliverable | Test |
|---|---|---|
| Implement `ExceptionService` with status transitions | Service class | Unit test: valid transitions succeed; invalid transitions throw |
| Implement audit logging (automatic on every update) | `AuditService` | Unit test: every PATCH creates an audit entry |
| Implement exception comments | Comment CRUD | Integration test: add comment, retrieve comments |
| Create exception API endpoints | Controller | Integration test: filter, update, comment via API |
| Add assignment to user with optimistic locking | Part of PATCH | Integration test: assign, verify assignee; concurrent update returns 409 |

**Demo:** Show exception lifecycle: open → acknowledged → resolved → closed, with audit trail.

### Milestone 5 — Authentication & RBAC (Days 11–13)

**Goal:** Login, role-based endpoint protection, JWT refresh.

| Task | Deliverable | Test |
|---|---|---|
| Implement `JwtService` (sign, verify, refresh) | Service class | Unit test: token generation, validation, expiry |
| Implement `AuthService` and login endpoint | Service + controller | Integration test: login with valid/invalid credentials |
| Configure Spring Security filter chain | `SecurityConfig`, `JwtAuthFilter` | Integration test: unauthenticated requests get 401 |
| Implement role-based authorization | `@PreAuthorize` annotations | Integration test: ANALYST can't trigger reconciliation; ADMIN can |
| Seed demo users | Migration V5 | Admin and analyst users exist after migration |
| Implement `UserService` and management endpoints | Service + controller | Integration test: ADMIN can create users |

**Demo:** Login as analyst (limited access) vs. admin (full access).

### Milestone 6 — React Frontend (Days 14–19)

**Goal:** Functional dashboard with all pages.

| Task | Deliverable | Test |
|---|---|---|
| Scaffold Vite + React + TypeScript project | Project skeleton | `npm run dev` starts without errors |
| Build design system (CSS variables, global styles) | `index.css`, `variables.css` | Visual review |
| Build layout components (sidebar, header) | Layout components | Visual review |
| Build Login page with auth context | Login page, `AuthContext` | Manual test: login/logout flow |
| Build Dashboard page with stat cards and charts | Dashboard page | Visual review with mock data |
| Build Trade List page with data table | Trade list + filters | Manual test: filtering works |
| Build Trade Import page with file upload | Import page | Manual test: upload CSV, see results |
| Build Reconciliation Runs page | Runs list + detail view | Manual test: view run results |
| Build Exception List page | Exception list + filters | Manual test: filter, paginate |
| Build Exception Detail page with comments and audit | Detail page | Manual test: update status, add comment |
| Build User Management page (admin only) | User management | Manual test: create user, change role |
| API integration with Axios client | API layer | All pages show real data |

**Demo:** Full walkthrough: login → import → reconcile → review exceptions → resolve.

### Milestone 7 — Docker, CI/CD, Polish (Days 20–22)

**Goal:** One-command deployment, automated test pipeline.

| Task | Deliverable | Test |
|---|---|---|
| Write backend Dockerfile (multi-stage build) | Dockerfile | `docker build` succeeds |
| Write frontend Dockerfile (Nginx serving built assets) | Dockerfile | `docker build` succeeds |
| Write `docker-compose.yml` for full stack | Compose file | `docker compose up` starts everything |
| Write GitHub Actions CI workflow | `.github/workflows/ci.yml` | PR triggers build + test |
| Write comprehensive README | `README.md` | A new developer can set up the project in < 10 minutes |
| Add OpenAPI annotations for Swagger docs | Controller annotations | Swagger UI is complete and accurate |

**Demo:** `docker compose up`, access app at `localhost:3000`.

---

## 7. Assumptions & Technical Risks

### 7.1 Assumptions

| # | Assumption | Impact if Wrong |
|---|---|---|
| A1 | Each trade has a globally unique `external_trade_id` within its source system. | If IDs aren't unique, our matching key breaks. We'd need a composite key (e.g., ID + trade date + symbol). |
| A2 | CSV files are well-structured with consistent column ordering and encoding (UTF-8). | We'd need to add column-mapping configuration and encoding detection. |
| A3 | Trade volumes are modest (<100K records per import, <500K total). | At higher volumes, we'd need batch processing, streaming CSV parsing, and possibly async reconciliation with progress tracking. |
| A4 | Two source systems are sufficient for the demo. | Adding a third system changes reconciliation from pairwise to N-way comparison — significantly more complex. |
| A5 | Price tolerance of ±0.01 is reasonable for demonstration. | In production, tolerance would vary by asset class, currency, and instrument type. |
| A6 | Settlement date is a single date, not a datetime. | If systems record settlement timestamps, we'd need time-zone–aware comparison. |
| A7 | The application runs in a single timezone context. | Multi-timezone support would require storing all timestamps as UTC and converting for display. |
| A8 | JWT is sufficient for authentication in a portfolio project. | Production systems at firms like Apollo would use SSO/SAML/OIDC with an identity provider. |

### 7.2 Technical Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | **Reconciliation performance degrades with large datasets** | Medium | High | Index on `external_trade_id`; batch processing; profile with realistic data volumes early. |
| R2 | **CSV parsing edge cases** (embedded commas, quoted fields, BOM characters) | High | Medium | Use a battle-tested CSV library (Apache Commons CSV); write fuzz-like tests with edge-case CSVs. |
| R3 | **BigDecimal comparison subtleties** | Medium | High | Always use `compareTo()`, never `equals()` (because `1.0` ≠ `1.00` with `equals`). Enforce this via code review and unit tests. |
| R4 | **JWT secret rotation without invalidating active sessions** | Low | Medium | Use asymmetric keys (RS256) in production; for the demo, symmetric HS256 is acceptable. |
| R5 | **Flyway migration ordering conflicts during parallel development** | Low | Low | Solo developer; use sequential version numbers; test migrations against a clean DB in CI. |
| R6 | **React state management complexity** | Medium | Medium | Start with React Context + hooks; add a state management library only if prop drilling becomes unmanageable. |
| R7 | **Docker networking issues between backend and database** | Medium | Low | Use Docker Compose service names as hostnames; health checks with `depends_on` conditions. |
| R8 | **Overengineering** | High | Medium | Resist adding features not in the requirements (e.g., WebSockets, real-time updates, caching). Keep YAGNI discipline. |

### 7.3 Deliberate Non-Goals (and Why)

| Feature | Why It's Excluded |
|---|---|
| **Real-time WebSocket updates** | Adds complexity without demonstrating core competence. Polling is sufficient for a portfolio project. |
| **Kafka integration** | The architecture is designed for it, but adding it prematurely introduces infrastructure complexity that distracts from business logic. |
| **Multi-tenancy** | Out of scope; single-tenant is appropriate for a demo. |
| **Real financial data** | Legal and ethical risk. Synthetic data is explicitly required. |
| **OAuth2 / SSO** | JWT with username/password is sufficient to demonstrate RBAC concepts. |
| **Horizontal scaling** | The modular monolith is designed to be extractable, but we don't need to prove it scales horizontally. |
| **Email / Slack notifications** | Nice-to-have but not core to the reconciliation domain. |

---

## Appendix A — CSV File Format

### System A — `system_a_trades.csv`

```csv
trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status
TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED
TRD-002,2026-10-15,2026-10-17,MSFT,SELL,250,412.30,JPMORGAN,CONFIRMED
```

### System B — `system_b_trades.csv`

```csv
trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status
TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED
TRD-002,2026-10-15,2026-10-17,MSFT,SELL,200,412.30,JPMORGAN,CONFIRMED
```

> Note: `TRD-002` has a quantity mismatch (250 vs 200) — this is an intentional discrepancy
> for testing the reconciliation engine.

---

## Appendix B — Key Design Decisions for Interview Reference

### B.1 "Why a modular monolith instead of microservices?"

> "At the current scale — solo developer, single database, no independent deployment needs —
> microservices would add operational complexity (service discovery, distributed tracing,
> network failure handling) without any benefit. The modular structure means each module has
> clear boundaries and communicates through interfaces, so if we needed to extract the
> reconciliation engine into a separate service later, we'd replace the in-process interface
> with a Kafka consumer. The business logic wouldn't change."

### B.2 "Why `ON CONFLICT DO NOTHING` for idempotent imports?"

> "In financial systems, operations must be safe to retry. If a CSV upload fails halfway
> due to a network error, the operator should be able to re-upload without worrying about
> creating duplicate trades. The unique constraint on `(source_system, external_trade_id)`
> guarantees this at the database level — even if there's a bug in our application code,
> PostgreSQL won't allow a duplicate."

### B.3 "Why `BigDecimal` instead of `double` for prices?"

> "IEEE 754 floating-point can't represent `0.1` exactly. In reconciliation, we're comparing
> prices from two systems — if one stores `100.10` and the other stores the same value but
> with a floating-point representation, `equals()` might return `false` even though the
> prices are identical. `BigDecimal` with `NUMERIC` in PostgreSQL gives us exact decimal
> arithmetic. And we always use `compareTo()` instead of `equals()` because `1.0` and `1.00`
> have different scales but the same mathematical value."

### B.4 "Why are reconciliation rules pluggable?"

> "Each comparison rule (quantity, price, settlement date) is a separate class implementing
> a `ComparisonRule` interface. This makes each rule independently testable, and we can add
> new rules (e.g., compare counterparty names, compare trade status) without modifying the
> matching engine. It's the Open/Closed Principle — open for extension, closed for
> modification."

### B.5 "Why entity `TradeException` and not `Exception`?"

> "Java already has `java.lang.Exception`. Naming our entity `Exception` would cause constant
> import conflicts and confusion. Similarly, `AppUser` avoids collisions with Spring
> Security's `User` class. These are pragmatic naming choices to reduce friction."

### B.6 "Why `202 Accepted` for reconciliation instead of `200 OK`?"

> "Reconciliation might take seconds to minutes depending on the volume of trades. Returning
> `202` signals that the server has accepted the request but hasn't finished processing.
> The client polls for completion. This pattern also means if we later move reconciliation
> to an async worker (or Kafka consumer), the API contract doesn't change — the client
> already expects to wait."

### B.7 "Why optimistic locking on exceptions?"

> "In a real operations team, multiple analysts might be looking at the same exception
> simultaneously. Without concurrency control, the last writer wins silently — an analyst
> could overwrite another's status change without knowing. Optimistic locking via a `version`
> column means the second writer gets a `409 Conflict` and can reload before retrying.
> It's the right trade-off because exception contention is low (optimistic) but data
> integrity matters (locking)."

### B.8 "Why record price_tolerance on each reconciliation run?"

> "Reproducibility. If someone asks 'why did this run flag 47 price breaks?' we can look
> at the run record and see the tolerance was 0.01. If we later change the tolerance to
> 0.05, old runs still make sense because each run records the tolerance it used. This is
> an audit requirement in financial systems."

---

*This document will be updated as implementation progresses and decisions evolve.*
