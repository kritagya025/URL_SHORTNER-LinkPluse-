# LinkPulse — Full-Stack URL Shortener & Real-Time Analytics Service

[![CI/CD Pipeline](https://github.com/kritagya025/URL_SHORTNER-LinkPluse-/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/kritagya025/URL_SHORTNER-LinkPluse-/actions/workflows/ci-cd.yml)
![Java 17](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot 3.2.5](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Docker](https://img.shields.io/badge/Docker-197MB%20Optimized-2496ED.svg)
![JaCoCo Coverage](https://img.shields.io/badge/Line%20Coverage-91%25-success.svg)
![Branch Coverage](https://img.shields.io/badge/Branch%20Coverage-90%25-success.svg)
![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)

LinkPulse is a production-ready, interview-grade **Full-Stack URL Shortener & Real-Time Analytics Platform** built using **Spring Boot 3**, **Java 17**, **PostgreSQL 16**, **Docker Compose**, **Nginx**, and an automated **GitHub Actions CI/CD Pipeline**.

**Current release:** `1.3.1` — see [CHANGELOG.md](CHANGELOG.md) for the full history.

---

## Contents

- [Overview](#overview)
- [Technical Performance & Code Quality Benchmarks](#technical-performance--code-quality-benchmarks)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Database Schema & Indexing](#database-schema--indexing)
- [Multi-Stage Docker Optimization (jlink)](#multi-stage-docker-optimization-jlink)
- [GitHub Actions CI/CD Pipeline](#github-actions-cicd-pipeline)
- [Deployment](#deployment)
- [Testing & Code Coverage](#testing--code-coverage)
- [Project Structure](#project-structure)
- [Documentation](#documentation)

---

## Overview

- **Random Base62 Short-Code Generation**: Generates a random 6-character short code from the Base62 character set (a-z, A-Z, 0-9) using `SecureRandom` — roughly 56.8 billion (62^6) combinations — re-rolling up to five times if the candidate code already exists (e.g. `http://localhost/aB72x`).
- **HTTP 302 Redirect Engine**: Fast redirection to original destinations while logging visit timestamps in real time. Click counts are incremented with a single atomic `UPDATE`, so simultaneous visits to the same link cannot overwrite one another.
- **Real-Time Click Analytics & Inspector**: Live click counter auto-updates across dashboard tables and inspector cards every 3 seconds, without page refreshes. Polling pauses while the browser tab is in the background.
- **Expiration Management**: Supports custom date/time expiration thresholds. Expired links automatically return `HTTP 410 Gone`.
- **Dual-Theme Analytics Dashboard**: Light and dark themes with a persisted toggle and a `prefers-color-scheme` default, sortable columns, per-row click bars, relative timestamps, real-time search filtering, copy-to-clipboard, status badges (`Active` / `Expired`), a backend reachability indicator, and link deletion behind an accessible confirmation dialog. Fully responsive, collapsing into stacked cards below 760px.
- **Keyboard & Accessibility Support**: `/` focuses the dashboard filter and `Esc` closes the dialog or clears the filter; sort headers are keyboard-operable with `aria-sort` indicators, focus rings are visible throughout, and a `prefers-reduced-motion` guard disables animations.
- **Input Validation & Error Handling**: Spring Bean Validation enforces URL formats, while `@ControllerAdvice` standardizes JSON error responses. Unmatched paths return `404`, and unexpected failures return a generic message — internal exception text (SQL, driver and path detail) is logged, never returned.
- **Ultra-Lean Multi-Stage Docker Architecture**: Uses `jlink` to build a custom 20-module minimal JRE on Alpine 3.21, shrinking image size from 434MB to **197MB** (55% reduction).
- **Comprehensive Unit & Integration Test Suite**: 45 JUnit 5 tests achieving **91% line coverage** and **90% branch coverage** via JaCoCo.
- **Automated CI/CD Pipeline**: GitHub Actions workflow compiles the code, runs JUnit 5 tests with JaCoCo reports against a throwaway PostgreSQL service container, validates frontend assets, and builds and publishes Docker images.

---

## Technical Performance & Code Quality Benchmarks

| Metric | Measured Value | Benchmark Details |
| :--- | :--- | :--- |
| **Throughput (50 Concurrency)** | **500 req/sec** | Sustained throughput across GET redirection endpoint |
| **Mean Response Time** | **80.1 ms** | Average end-to-end redirection latency under load |
| **Line Coverage (JaCoCo)** | **91%** | 124/137 lines covered across service, controller, and entity layers |
| **Branch Coverage (JaCoCo)** | **90%** | 27/30 conditional branches covered |
| **Total Test Suite Count** | **45 tests** | Unit tests + MockMvc API controller integration tests |
| **Docker Image Footprint** | **197 MB** | 3-stage `jlink` custom JRE + Alpine base (reduced from 434 MB) |

---

## Technology Stack

| Layer | Technology | Description |
| :--- | :--- | :--- |
| **Frontend UI** | HTML5, Vanilla CSS3, JavaScript (ES6+) | Token-based dual-theme dashboard with real-time auto-polling, sorting & search filtering |
| **Web Server** | Nginx 1.25 Alpine | Serves static frontend assets and reverse-proxies `/api/` traffic and short-code redirects |
| **Backend Framework**| Java 17 / Spring Boot 3.2.5 | REST Controllers, Service Layer, Exception Handling, Data JPA |
| **Database** | PostgreSQL 16 | Relational persistence with indexed short-code lookups |
| **Connection Pool** | HikariCP | High-performance database connection management |
| **Testing & Coverage** | JUnit 5, MockMvc & JaCoCo 0.8.12 | 45 unit/integration tests with automated HTML coverage reports |
| **Containerization** | Docker & Docker Compose | 3-Stage `jlink` minimal JRE container builds & network orchestration |
| **CI/CD Pipeline** | GitHub Actions | Automated build, test, Docker image building, and Docker Hub registry publishing |

---

## System Architecture

```text
                                  Browser
                                     |
                                     ↓
                            http://localhost:80
                                     |
                                     ↓
                          Nginx Container (Frontend)
                       [url_shortener_frontend : Port 80]
                                     |
                             Docker Network
                          (urlshortener_net)
                                     |
                                     ↓
                       Spring Boot Container (Backend)
                       [url_shortener_backend : Port 8080]
                       (Custom JRE minimal via jlink)
                                     |
                             Docker Network
                          (urlshortener_net)
                                     |
                                     ↓
                        PostgreSQL Container (Database)
                       [url_shortener_postgres : Port 5432]
                                     |
                                     ↓
                             Persistent Volume
                           (postgres_data)
```

Nginx serves the dashboard directly and proxies two classes of request to the backend: everything under `/api/`, and any single-segment path of 1–10 alphanumeric characters (a short code).

---

## Getting Started

### Prerequisites

| Path | Requirements |
| :--- | :--- |
| **Docker Compose** | Docker Engine 24+ and Docker Compose v2+ |
| **Local (no Docker)** | JDK 17, Maven 3.9+, and a running PostgreSQL 16 instance |

### Option A: Run with Docker Compose (Recommended)

1. Clone the repository:
   ```bash
   git clone https://github.com/kritagya025/URL_SHORTNER-LinkPluse-.git
   cd URL_SHORTNER-LinkPluse-
   ```
2. Build and start all services (`postgres`, `backend`, `frontend`):
   ```bash
   docker compose up --build -d
   ```
3. Open `http://localhost:80` in your web browser. The backend API is also reachable directly on `http://localhost:8080`.
4. Tail the logs or check container health:
   ```bash
   docker compose ps
   docker compose logs -f backend
   ```
5. Stop containers while preserving database data:
   ```bash
   docker compose down
   ```
   Add `-v` to also drop the `postgres_data` volume and start from an empty database.

---

### Option B: Run Locally Without Docker

1. Start local PostgreSQL on port `5432` and create the database:
   ```sql
   CREATE DATABASE url_shortener_db;
   ```
2. Supply your credentials as environment variables (preferred), or edit the defaults in `src/main/resources/application.properties`:
   ```bash
   export DB_USERNAME=postgres
   export DB_PASSWORD=your_password
   ```
   On Windows PowerShell:
   ```powershell
   $env:DB_USERNAME = "postgres"
   $env:DB_PASSWORD = "your_password"
   ```
3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
4. Open `http://localhost:8080` in your web browser — the backend serves a copy of the dashboard from `src/main/resources/static/`.

---

## Configuration

All settings are read from environment variables with local-friendly defaults, so no file needs to be edited to run the app in a new environment.

| Variable | Default | Description |
| :--- | :--- | :--- |
| `PORT` | `8080` | HTTP port the backend listens on (injected automatically by Render) |
| `SPRING_DATASOURCE_URL` | built from `DB_*` | Full JDBC URL; when set, it overrides the individual `DB_*` values |
| `DB_HOST` | `localhost` | PostgreSQL host (`postgres` inside Docker Compose) |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `url_shortener_db` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `kritagya` | Database password — override this outside local development |
| `APP_BASE_URL` | `http://localhost:8080` | Prefix used to build the `shortUrl` returned by the API |

Hibernate runs with `spring.jpa.hibernate.ddl-auto=update`, so the `urls` table and its index are created on first start. HikariCP is capped at 10 connections with 5 kept idle.

---

## REST API

| Method | Endpoint | Description | Request Body | Success Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/urls` | Create short URL | `{ originalUrl, expiresAt? }` | `201 Created` |
| **GET** | `/{shortCode}` | Redirect to original URL | None | `302 Found` |
| **GET** | `/api/urls/{shortCode}/stats` | Get link statistics | None | `200 OK` |
| **GET** | `/api/urls` | List all created URLs (newest first) | None | `200 OK` |
| **DELETE**| `/api/urls/{shortCode}` | Delete short URL | None | `204 No Content` |

### Create a short link

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://www.example.com/very/long/path","expiresAt":"2026-12-31T23:59:59"}'
```

```json
{
  "id": 1,
  "originalUrl": "https://www.example.com/very/long/path",
  "shortCode": "aB72xQ",
  "shortUrl": "http://localhost:8080/aB72xQ",
  "clickCount": 0,
  "createdAt": "2026-09-21T10:30:00",
  "expiresAt": "2026-12-31T23:59:59"
}
```

`expiresAt` is optional, is interpreted as local wall-clock time (no timezone suffix), and must not be in the past. The stats endpoint returns the same fields minus `id`, plus a `status` of `ACTIVE` or `EXPIRED`.

### Error responses

Every failure returns the same JSON shape:

```json
{
  "status": 404,
  "message": "Short URL code 'aB72xQ' not found",
  "timestamp": "2026-09-21T17:04:11.482"
}
```

| Status | Raised when |
| :--- | :--- |
| `400 Bad Request` | Blank `originalUrl`, a scheme other than `http`/`https`, a malformed host, or an expiry in the past |
| `404 Not Found` | Unknown short code, or any path that matches no route and no static asset |
| `410 Gone` | The link exists but is past its `expiresAt` timestamp |
| `500 Internal Server Error` | Unexpected failure — the response carries a generic message while the cause and stack trace stay in the server log |

CORS is open to all origins for `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS` and `HEAD`. The API is stateless and reads no cookies, so credentialed cross-origin requests are deliberately disallowed.

---

## Database Schema & Indexing

### Table: `urls`

```sql
CREATE TABLE urls (
    id           BIGSERIAL PRIMARY KEY,
    original_url TEXT NOT NULL,
    short_code   VARCHAR(10) NOT NULL UNIQUE,
    click_count  BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL,
    expires_at   TIMESTAMP NULL
);

CREATE UNIQUE INDEX idx_urls_short_code ON urls(short_code);
```

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY`, Auto-Increment | Unique primary identifier |
| `original_url` | `TEXT` | `NOT NULL` | Destination long URL |
| `short_code` | `VARCHAR(10)` | `NOT NULL`, `UNIQUE`, `INDEX` | Indexed 6-character Base62 code |
| `click_count` | `BIGINT` | `NOT NULL`, Default `0` | Total redirected visits counter |
| `created_at` | `TIMESTAMP` | `NOT NULL` | Creation timestamp |
| `expires_at` | `TIMESTAMP` | `NULLABLE` | Optional expiration timestamp |

---

## Multi-Stage Docker Optimization (jlink)

LinkPulse utilizes a **3-stage Docker build** pipeline to achieve minimal image footprints and container security:

1. **Stage 1 (Builder)**: Compiles source code with Maven 3.9 & Temurin JDK 17.
2. **Stage 2 (JRE Builder)**: Uses JDK `jlink` to build a minimal custom JRE containing only the 20 required JDK modules (`java.base`, `java.desktop`, `java.management`, `java.sql`, `java.security.jgss`, `jdk.crypto.ec`, `jdk.unsupported`, etc.), stripping debug symbols and manual pages.
3. **Stage 3 (Runner)**: Packages the lightweight application JAR onto Alpine 3.21 with a non-root system user (`appuser:appgroup`), using `COPY --chown` to eliminate layer duplication.

```dockerfile
# Execution with container-aware JVM flags
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

---

## GitHub Actions CI/CD Pipeline

```text
                    Developer (git push / PR)
                               |
                               ↓
                     GitHub Repository (main)
                               |
                               ↓
                    GitHub Actions Runner
                       (ubuntu-latest)
                               |
          ┌────────────────────┴────────────────────┐
          ↓                                         ↓
   Job 1: build-and-test-backend            Job 2: validate-frontend
   - Checkout Repository                    - Checkout Repository
   - Setup Java 17 (Temurin) + Maven cache  - Verify static web assets
   - Start postgres:16-alpine service       - Build frontend Nginx image
   - mvn clean verify -B (45 JUnit tests)
   - Generate JaCoCo Coverage Report
          |                                         |
          └────────────────────┬────────────────────┘
                               |
                               ↓ (both pass, and push to main)
                   Job 3: build-docker-images
                   - Login to Docker Hub via GitHub Secrets
                   - Build Backend & Frontend Docker Images (Buildx)
                   - Tag with `latest` & Git Commit SHA
                   - Push Images to Docker Hub Registry
                   - Trigger Render deploy hook (skipped if unset)
```

Pull requests run jobs 1 and 2 only; images are published exclusively on pushes to `main`.

### GitHub Secrets Setup

To enable automated Docker Hub publishing:
1. Open repository settings: **Settings → Secrets and variables → Actions**.
2. Add secrets:
   - `DOCKERHUB_USERNAME`: Your Docker Hub username.
   - `DOCKERHUB_TOKEN`: Personal Access Token from Docker Hub.
   - `RENDER_DEPLOY_HOOK` *(optional)*: Render deploy hook URL. The pipeline skips this step when the secret is absent.

---

## Deployment

`render.yaml` is a Render Blueprint that provisions the whole stack: the backend as a web service running the published `linkpulse-backend` image, the dashboard as a static site rooted at `frontend/`, and a managed PostgreSQL instance whose host, port, name, user and password are injected into the backend as `DB_*` environment variables.

Step-by-step instructions for Render, plain Docker, and self-hosted setups are in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

---

## Testing & Code Coverage

### Running Tests & JaCoCo Report
Run unit tests, integration tests, and generate HTML code coverage reports:
```bash
mvn clean test jacoco:report
```
View the generated coverage report locally at: `target/site/jacoco/index.html`

The suite covers the service layer (19 tests), the API controllers via MockMvc (12), the entity lifecycle (7), unmatched-path handling (4), and short-code generation (3).

### Postman Collection
Import `URL_Shortener.postman_collection.json` into Postman to test pre-configured API requests:
- Create URL
- Redirect Short Code
- Get Statistics
- List All URLs
- Delete Short URL
- Validation Error Scenarios
- Expired Link Scenarios

---

## Project Structure

```text
.
├── src/main/java/com/urlshortener/
│   ├── config/           # CORS filter configuration
│   ├── controller/       # UrlController (REST API) + RedirectController (302s)
│   ├── dto/              # Request/response payloads
│   ├── entity/           # Url JPA entity
│   ├── exception/        # Domain exceptions + @ControllerAdvice handler
│   ├── repository/       # Spring Data JPA repository (atomic click increment)
│   └── service/          # UrlService business logic + ShortCodeGenerator
├── src/main/resources/
│   ├── application.properties
│   └── static/           # Dashboard served by the backend (mirrors frontend/)
├── src/test/java/        # 45 JUnit 5 + MockMvc tests
├── frontend/             # Nginx-served dashboard (index.html, css/, js/)
├── docs/                 # API reference and deployment guide
├── .github/workflows/    # CI/CD pipeline
├── Dockerfile            # 3-stage jlink backend image
├── docker-compose.yml    # postgres + backend + frontend orchestration
└── render.yaml           # Render Blueprint
```

The dashboard exists in two places: `frontend/` is what Nginx serves in Docker, and `src/main/resources/static/` is the copy the backend serves when run standalone. Keep both in sync when changing the UI.

---

## Documentation

| Document | Contents |
| :--- | :--- |
| [docs/API.md](docs/API.md) | Full endpoint reference with request/response examples |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Docker, Render, and self-hosted deployment guides |
| [CHANGELOG.md](CHANGELOG.md) | Release history in Keep a Changelog format |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branch, commit, and pull request conventions |
| [SECURITY.md](SECURITY.md) | Supported versions and vulnerability reporting |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community expectations |

---

## License

Released under the [MIT License](LICENSE).
