# LinkPulse System Architecture & Technical Design

This document details the architectural blueprints, technical design decisions, data model, and operational mechanisms underpinning the **LinkPulse** URL Shortener & Analytics service.

---

## 1. Architectural Overview

LinkPulse follows a clean, decoupled, layered architecture separating static frontend presentation, application services, and persistent relational data storage.

```mermaid
graph TD
    Client["Client (Browser / API Consumer)"]
    
    subgraph "Edge / Ingress Layer"
        Nginx["Nginx Reverse Proxy & Static Asset Server (Port 80)"]
    end
    
    subgraph "Application Layer (Spring Boot 3.2.5 / Java 17)"
        Controllers["REST Controllers (UrlController, RedirectController, HealthController)"]
        ExceptionAdvisor["GlobalExceptionHandler (@ControllerAdvice)"]
        Services["Business Services (UrlService, ShortCodeGenerator)"]
    end
    
    subgraph "Persistence Layer"
        JPA["Spring Data JPA Repository (UrlRepository)"]
        Hikari["HikariCP Connection Pool"]
        Postgres[("PostgreSQL 16 Database")]
    end

    Client -->|Static Assets| Nginx
    Client -->|REST & Redirects| Nginx
    Nginx -->|Proxy API Requests| Controllers
    Controllers --> Services
    Controllers -.-> ExceptionAdvisor
    Services --> JPA
    JPA --> Hikari
    Hikari --> Postgres
```

---

## 2. Layered Responsibilities

| Layer | Component | Primary Responsibility |
| :--- | :--- | :--- |
| **Ingress** | Nginx Reverse Proxy | Serves static assets (HTML/CSS/JS), terminates HTTP connections, proxies `/api/*` and short code redirects to Spring Boot backend. |
| **Security & Rate Limit Filters** | `SecurityHeadersFilter`, `RateLimitFilter`, `WebConfig` | Enforces OWASP headers + Content-Security-Policy, applies per-IP fixed-window throttling on `POST /api/urls`, and configures stateless CORS. |
| **Web / Controller** | `UrlController`, `RedirectController`, `HealthController` | Validates HTTP payloads, binds path variables, emits `Cache-Control: no-store` on 302 redirects, and exposes operational health probes. |
| **Exception Handling** | `GlobalExceptionHandler` | Intercepts validation, malformed JSON, type mismatch, method-not-allowed, and domain errors, mapping them to uniform `ErrorResponse` JSON without leaking internals. |
| **Business Logic** | `UrlService`, `ShortCodeGenerator`, `QrCodeService` | Enforces URL syntax, length bounds (2,048 chars), and SSRF protections; handles expirations, Base62 collision retries, and ZXing PNG QR generation. |
| **Data Access** | `UrlRepository`, `Url` entity | Executes queries against PostgreSQL using Spring Data JPA, handles atomic click incrementation via native JPA `@Modifying` queries. |

---

## 3. Data Model & Database Schema

The core persistence entity is mapped to the `urls` table in PostgreSQL.

```mermaid
erDiagram
    URLS {
        BIGSERIAL id PK "Auto-incrementing surrogate primary key"
        TEXT original_url "Destination target URL"
        VARCHAR(10) short_code UK "Unique Base62 short identifier"
        BIGINT click_count "Accumulated total redirection hits"
        TIMESTAMP created_at "Record creation timestamp"
        TIMESTAMP expires_at "Optional expiration timestamp"
    }
```

### Table Definition & Indexing

```sql
CREATE TABLE urls (
    id BIGSERIAL PRIMARY KEY,
    original_url TEXT NOT NULL,
    short_code VARCHAR(10) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL
);

-- Unique index to guarantee code uniqueness and O(1) B-tree lookup performance
CREATE UNIQUE INDEX idx_urls_short_code ON urls(short_code);
```

---

## 4. Core Workflows

### 4.1 URL Creation & Collision Resolution Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as UrlController
    participant Service as UrlService
    participant Generator as ShortCodeGenerator
    participant Repo as UrlRepository
    participant DB as PostgreSQL

    User->>Controller: POST /api/urls { originalUrl, expiresAt }
    Controller->>Service: createShortUrl(request)
    Service->>Service: validateUrlFormat() & validateExpiration()
    loop Up to 5 retries on collision
        Service->>Generator: generateShortCode()
        Generator-->>Service: candidate (e.g. "aB72x")
        Service->>Repo: existsByShortCode(candidate)
        Repo->>DB: SELECT COUNT(*) ...
        DB-->>Repo: false (code is available)
    end
    Service->>Repo: save(Url entity)
    Repo->>DB: INSERT INTO urls ...
    DB-->>Repo: Saved entity with ID
    Repo-->>Service: Url
    Service-->>Controller: UrlResponse
    Controller-->>User: 201 Created { shortCode, shortUrl, ... }
```

### 4.2 Redirection & Atomic Click Counting

To avoid race conditions under concurrent access, click increments do **not** perform an in-memory read-modify-write cycle. Instead, an atomic database UPDATE query is executed:

$$\text{UPDATE urls SET click\_count} = \text{click\_count} + 1 \text{ WHERE id} = :id$$

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant Redirect as RedirectController
    participant Service as UrlService
    participant Repo as UrlRepository
    participant DB as PostgreSQL

    Browser->>Redirect: GET /{shortCode}
    Redirect->>Service: getOriginalUrlAndLogClick(shortCode)
    Service->>Repo: findByShortCode(shortCode)
    Repo->>DB: SELECT * FROM urls WHERE short_code = ?
    alt Code not found
        DB-->>Repo: Optional.empty()
        Service-->>Redirect: throw ShortUrlNotFoundException
        Redirect-->>Browser: 404 Not Found
    else Code found but expired (now > expiresAt)
        Service-->>Redirect: throw ExpiredUrlException
        Redirect-->>Browser: 410 Gone
    else Code found & active
        Service->>Repo: incrementClickCount(url.getId())
        Repo->>DB: UPDATE urls SET click_count = click_count + 1 WHERE id = ?
        Service-->>Redirect: originalUrl
        Redirect-->>Browser: 302 Found (Location: originalUrl)
    end
```

---

## 5. Security & Reliability

1. **Information Leakage Prevention**: `GlobalExceptionHandler` intercepts generic `Exception.class` and logs the full stack trace server-side while returning a sanitized `{"status": 500, "message": "An unexpected error occurred"}` response to clients. Invalid JSON payloads (`HttpMessageNotReadableException`), invalid query parameter types (`MethodArgumentTypeMismatchException`), and unsupported methods (`HttpRequestMethodNotSupportedException`) map to clean 400/405 responses.
2. **SSRF & Input Bounds Validation**: `UrlService` validates `http`/`https` schemes, caps destination URLs at 2,048 characters, verifies valid port ranges (`<= 65535`), and blocks private, loopback, link-local, multicast, CGNAT (`100.64.0.0/10`), IPv6 ULA (`fc00::/7`), `.local`/`.internal` hostnames, and obfuscated numeric/hex/octal IPv4 targets across all resolved DNS records.
3. **OWASP Security Headers & CSP**: `SecurityHeadersFilter` attaches `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection`, `Referrer-Policy`, and `Permissions-Policy` to every HTTP response.
4. **Per-IP Rate Limiting**: `RateLimitFilter` enforces a configurable fixed-window quota on `POST /api/urls` (default 30 requests/minute per client IP via `X-Forwarded-For` / remote address) with `X-RateLimit-*` and `Retry-After` headers and bounded client bucket eviction.
5. **CORS Security**: Cross-Origin Resource Sharing is configured via `WebConfig` using Spring's `CorsFilter`. `allowCredentials(false)` prevents cross-site credential leakage while allowing open API access for decoupled frontends.
6. **Container Health Probing**: The `/api/health` endpoint verifies both web layer responsiveness and active database connectivity, returning `HTTP 200 (UP)` or `HTTP 503 (DEGRADED)`.

---

## 6. High-Throughput Scaling Roadmap

For production workloads exceeding thousands of requests per second:

- **Redis Cache Layer**: Read-through cache for `short_code -> originalUrl` mappings with TTL matching link expiration to achieve sub-millisecond redirection without hitting PostgreSQL.
- **Asynchronous Click Analytics**: Offload click counter increments from the critical redirection path to an event queue (e.g., Redis Streams or Apache Kafka) with batched database writes.
- **Read/Write DB Splitting**: Direct analytics and listing queries (`GET /api/urls`) to read replicas while routing creations and redirects to the primary PostgreSQL instance.
