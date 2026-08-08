# LinkPulse — Full-Stack URL Shortener & Real-Time Analytics Service

[![CI/CD Pipeline](https://github.com/kritagya025/URL_SHORTNER-LinkPluse-/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/kritagya025/URL_SHORTNER-LinkPluse-/actions/workflows/ci-cd.yml)
![Java 17](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot 3.2.5](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED.svg)
![Nginx](https://img.shields.io/badge/Nginx-1.25%20Alpine-009639.svg)

LinkPulse is a production-ready, interview-grade **Full-Stack URL Shortener & Real-Time Analytics Platform** built using **Spring Boot 3**, **Java 17**, **PostgreSQL 16**, **Docker Compose**, **Nginx**, and an automated **GitHub Actions CI/CD Pipeline**.

---

## Overview

- **Base62 URL Shortening**: Encodes long URLs into compact 6-character short codes (e.g. `http://localhost/aB72x`).
- **HTTP 302 Redirect Engine**: Fast redirection to original destinations while logging visit timestamps in real time.
- **Real-Time Click Analytics & Inspector**: Live click counter auto-updates across dashboard tables and inspector cards without page refreshes.
- **Expiration Management**: Supports custom date/time expiration thresholds. Expired links automatically return `HTTP 410 Gone`.
- **Developer Dark Dashboard**: Clean developer aesthetic featuring real-time search filtering, copy-to-clipboard, status badges (`Active` / `Expired`), and link deletion.
- **Input Validation & Error Handling**: Spring Bean Validation enforces URL formats, while `@RestControllerAdvice` standardizes JSON error responses.
- **Multi-Container Docker Architecture**: Containerized setup featuring multi-stage Java builds, Nginx reverse proxying, and persistent PostgreSQL storage volumes.
- **Automated CI/CD Pipeline**: GitHub Actions workflow automatically compiles code, runs JUnit 5 tests, validates frontend assets, and builds Docker images.

---

## Technology Stack

| Layer | Technology | Description |
| :--- | :--- | :--- |
| **Frontend UI** | HTML5, Vanilla CSS3, JavaScript (ES6+) | Dark theme dashboard with real-time auto-polling & search filtering |
| **Web Server** | Nginx 1.25 Alpine | Serves static frontend assets and reverse-proxies `/api/` traffic |
| **Backend Framework**| Java 17 / Spring Boot 3.2.5 | REST Controllers, Service Layer, Exception Handling, Data JPA |
| **Database** | PostgreSQL 16 | Relational persistence with indexed short-code lookups |
| **Connection Pool** | HikariCP | High-performance database connection management |
| **Testing** | JUnit 5 & MockMvc | Unit testing and controller integration testing suite |
| **Containerization** | Docker & Docker Compose | Multi-stage container builds & network orchestration |
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

## REST API Endpoints

| Method | Endpoint | Description | Request Body | Response Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/urls` | Create short URL | `{ originalUrl, expiresAt }` | `201 Created` |
| **GET** | `/{shortCode}` | Redirect to original URL | None | `302 Found` (or `410 Gone`) |
| **GET** | `/api/urls/{shortCode}/stats` | Get link statistics | None | `200 OK` (or `404 Not Found`) |
| **GET** | `/api/urls` | List all created URLs | None | `200 OK` |
| **DELETE**| `/api/urls/{shortCode}` | Delete short URL | None | `204 No Content` |

---

## Getting Started

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
3. Open `http://localhost:80` in your web browser.
4. Stop containers while preserving database data:
   ```bash
   docker compose down
   ```

---

### Option B: Run Locally Without Docker

1. Start local PostgreSQL on port `5432` and create the database:
   ```sql
   CREATE DATABASE url_shortener_db;
   ```
2. Configure credentials in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.username=postgres
   spring.datasource.password=kritagya
   ```
3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
4. Open `http://localhost:8080` in your web browser.

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
   Job 1: ci-backend                        Job 2: ci-frontend
   - Checkout Repository                    - Checkout Repository
   - Setup Java 17 & Maven                  - Verify static web assets
   - Compile & Run JUnit 5 Tests            - Validate Nginx config
          |                                         |
          └────────────────────┬────────────────────┘
                               |
                               ↓ (Only if Job 1 & 2 PASS)
                     Job 3: cd-docker-hub
                     - Login to Docker Hub via GitHub Secrets
                     - Build Backend & Frontend Docker Images
                     - Tag with `latest` & Git Commit SHA (`${{ github.sha }}`)
                     - Push Images to Docker Hub Registry
```

### GitHub Secrets Setup

To enable automated Docker Hub publishing:
1. Open repository settings: **Settings → Secrets and variables → Actions**.
2. Add secrets:
   - `DOCKERHUB_USERNAME`: Your Docker Hub username.
   - `DOCKERHUB_TOKEN`: Personal Access Token from Docker Hub.

---

## Testing & Verification

### Automated Tests
Run unit tests and controller integration tests using Maven:
```bash
mvn test
```

### Postman Collection
Import `URL_Shortener.postman_collection.json` into Postman to test pre-configured API requests:
- Create URL
- Redirect Short Code
- Get Statistics
- List All URLs
- Delete Short URL
- Validation Error Scenarios
- Expired Link Scenarios
