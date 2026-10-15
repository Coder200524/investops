# InvestOps — Investment Operations & Trade Reconciliation Platform

> **Portfolio Project for Software Engineering Internship — Apollo Global Management**  
> Architected as an institutional-grade, modular monolith for multi-source trade reconciliation, zero-duplicate ingestion, and optimistic-locking discrepancy triage.

---

## Architecture & Technology Stack

| Layer | Technologies | Rationale |
|---|---|---|
| **Backend** | Java 17, Spring Boot 3.3.4, Spring Data JPA, Spring Security, Actuator | Modular monolith with in-process interfaces and domain events (Kafka-ready) |
| **Database** | PostgreSQL 16, Flyway | Local ACID transactions, composite constraints, `NUMERIC(18,8)` financial precision |
| **Frontend** | React 19, TypeScript, Vite | Independent client shell, high-density operations dashboard |
| **Testing** | JUnit 5, Mockito, Spring Boot Test, H2 (test profile) | Fast automated unit and context-load testing |
| **DevOps** | Docker, Docker Compose | Containerized local PostgreSQL with persistent volume and `pg_isready` health check |

For detailed system designs, ER diagrams, REST contracts, and interview defensibility rationale, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Prerequisites

Before starting, ensure the following tools are installed on your machine:
- **Java**: OpenJDK 17 or higher (`java -version`)
- **Docker & Docker Compose**: Docker 20.10+ / Docker Desktop (`docker -v`, `docker compose version`)
- **Node.js**: v18+ or v20+ / v22+ (`node -v`, `npm -v`)

---

## 1. Setup Instructions

### Step 1: Configure Environment Variables
Copy the template configuration file `.env.example` to `.env`:

**Windows (PowerShell):**
```powershell
Copy-Item .env.example .env
```

**macOS / Linux:**
```bash
cp .env.example .env
```

> **Note:** By default, PostgreSQL is mapped to host port `5434` in `.env.example` and `docker-compose.yml` to prevent port collisions with existing PostgreSQL services on host port `5432`.

### Step 2: Start the PostgreSQL Database
Start PostgreSQL 16 in the background using Docker Compose:

```bash
docker compose up -d
```

Verify that the database container is healthy:
```bash
docker compose ps
```
*(Status should indicate `Up (healthy)`)*

---

## 2. Database Migrations

InvestOps uses **Flyway** for automated database schema migrations.

To apply database migrations to the running PostgreSQL container:

**Windows (PowerShell / Command Prompt):**
```powershell
cd backend
.\mvnw.cmd flyway:migrate
```

**macOS / Linux:**
```bash
cd backend
./mvnw flyway:migrate
```

To inspect migration status:
```powershell
.\mvnw.cmd flyway:info
```

---

## 3. Running the Applications

### Start the Spring Boot Backend

From the `backend/` directory:

**Windows:**
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**macOS / Linux:**
```bash
cd backend
./mvnw spring-boot:run
```

- **API Base URL**: `http://localhost:8081/api/v1`
- **Health Endpoint**: `http://localhost:8081/actuator/health`

### Import Sample Trades (Milestone 2)

```bash
# Import System A Trades
curl -X POST -F "file=@data/sample/system_a_trades.csv" -F "sourceSystem=SYSTEM_A" http://localhost:8081/api/v1/trades/import

# Import System B Trades
curl -X POST -F "file=@data/sample/system_b_trades.csv" -F "sourceSystem=SYSTEM_B" http://localhost:8081/api/v1/trades/import

# Query Trades (with pagination and filtering)
curl "http://localhost:8081/api/v1/trades?page=0&size=25&sourceSystem=SYSTEM_A"
```

### Start the React + TypeScript Frontend

From the `frontend/` directory in a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

- **Frontend Application URL**: `http://localhost:5173`

---

## 4. Running Tests & Verifying Builds

### Run Backend Unit & Context Tests
```powershell
cd backend
.\mvnw.cmd clean test
```
*(Executes context loads, Actuator health endpoint tests, and configuration tests).*

### Verify Frontend TypeScript & Production Build
```bash
cd frontend
npm run build
```
*(Compiles TypeScript and creates optimized Vite bundle in `frontend/dist/`).*

### Validate Docker Compose Configuration
```bash
docker compose config
```

---

## 5. Shutdown & Teardown

To stop the PostgreSQL container:
```bash
docker compose down
```

To stop the container and delete persistent volume data:
```bash
docker compose down -v
```

---

## Project Structure

```
investops/
├── backend/                                    # Spring Boot 3.3.4 (Java 17)
│   ├── .mvn/wrapper/                           # Maven 3.9.9 Wrapper binaries & config
│   ├── mvnw & mvnw.cmd                         # Maven execution scripts
│   ├── pom.xml                                 # Backend dependencies & plugins
│   └── src/
│       ├── main/
│       │   ├── java/com/investops/
│       │   │   ├── InvestOpsApplication.java   # Spring Boot entry point
│       │   │   ├── common/                     # Cross-cutting configs, DTOs, handlers
│       │   │   ├── auth/                       # RBAC & authentication module
│       │   │   ├── trade/                      # Trade ingestion module
│       │   │   ├── reconciliation/             # Matching engine module
│       │   │   ├── exceptionmgmt/              # Discrepancy lifecycle module
│       │   │   └── dashboard/                  # Aggregations & reporting module
│       │   └── resources/
│       │       ├── application.yml             # Base configuration
│       │       ├── application-dev.yml         # Dev configuration
│       │       ├── application-test.yml        # Test configuration (H2)
│       │       └── db/migration/
│       │           └── V1__create_users_table.sql # Initial Flyway schema
│       └── test/java/com/investops/            # Context & health endpoint tests
│
├── frontend/                                   # React 19 + TypeScript + Vite
│   ├── src/
│   │   ├── App.tsx                             # InvestOps operations console shell
│   │   ├── App.css                             # Dark financial dashboard styles
│   │   ├── index.css                           # Global tokens & CSS variables
│   │   └── main.tsx                            # React entry point
│   ├── index.html                              # Shell HTML with Google Fonts
│   ├── package.json                            # Frontend dependencies & scripts
│   └── vite.config.ts                          # Vite bundler configuration
│
├── docker-compose.yml                          # PostgreSQL 16 service definition
├── .env.example                                # Documented environment template
├── .gitignore                                  # Git exclusion rules
├── docs/
│   └── ARCHITECTURE.md                         # Comprehensive architecture blueprint
└── README.md                                   # Getting started & operation guide
```
