# LinkPulse API Reference

Base URL: `http://localhost:80` (Docker) or `http://localhost:8080` (Local)

---

## Create Short URL

```http
POST /api/urls
Content-Type: application/json
```

### Request Body

| Field         | Type     | Required | Description                                                         |
| :------------ | :------- | :------- | :------------------------------------------------------------------ |
| `originalUrl` | `string` | Yes      | The destination URL to shorten (must include http:// or https://)   |
| `expiresAt`   | `string` | No       | ISO 8601 expiration timestamp (must be in the future)               |
| `customAlias` | `string` | No       | Optional custom short code (3–10 alphanumeric chars, non-reserved)  |

### Example Request (with Custom Alias)

```json
{
  "originalUrl": "https://www.example.com/very/long/path",
  "expiresAt": "2026-12-31T23:59:59",
  "customAlias": "mybrand"
}
```

### Example Response (201 Created)

```json
{
  "id": 1,
  "originalUrl": "https://www.example.com/very/long/path",
  "shortCode": "mybrand",
  "shortUrl": "http://localhost:8080/mybrand",
  "clickCount": 0,
  "createdAt": "2026-10-19T10:30:00",
  "expiresAt": "2026-12-31T23:59:59"
}
```

---

## Redirect Short URL

```http
GET /{shortCode}
```

### Response
- **302 Found** â€” Redirects to the original URL.
- **410 Gone** â€” The short URL has expired.
- **404 Not Found** â€” The short code does not exist.

---

## Get Link Statistics

```http
GET /api/urls/{shortCode}/stats
```

### Example Response (200 OK)

```json
{
  "id": 1,
  "originalUrl": "https://www.example.com/very/long/path",
  "shortCode": "aB72xQ",
  "clickCount": 42,
  "createdAt": "2025-08-22T10:30:00",
  "expiresAt": "2025-12-31T23:59:59",
  "status": "ACTIVE"
}
```

---

## List All URLs

```http
GET /api/urls
```

### Response (200 OK)

Returns an array of all created URL objects.

---

## Get Analytics Summary

```http
GET /api/urls/analytics/summary
```

Returns aggregate platform metrics across all shortened links.

### Example Response (200 OK)

```json
{
  "totalUrls": 42,
  "totalClicks": 1280,
  "activeUrls": 38,
  "expiredUrls": 4,
  "averageClicksPerUrl": 30.48,
  "mostVisitedUrl": {
    "id": 1,
    "originalUrl": "https://www.example.com/popular",
    "shortCode": "mybrand",
    "shortUrl": "http://localhost:8080/mybrand",
    "clickCount": 350,
    "createdAt": "2026-10-19T10:30:00",
    "expiresAt": null
  }
}
```

---

## Delete Short URL

```http
DELETE /api/urls/{shortCode}
```

### Response
- **204 No Content** — Successfully deleted.
- **404 Not Found** — Short code not found.

---

## Service Health Check

```http
GET /api/health
```

Operational health and readiness probe endpoint. Intended for Docker healthchecks, Kubernetes liveness probes, and external uptime monitors.

### Example Response (200 OK)

```json
{
  "service": "LinkPulse URL Shortener",
  "timestamp": "2026-10-14T20:30:00.123456",
  "status": "UP",
  "database": "CONNECTED"
}
```

### Response Codes
- **200 OK** — Service is operational and database is reachable.
- **503 Service Unavailable** — Service is degraded or database connection failed.

---

## Error Response Format

All error responses follow a consistent JSON structure:

```json
{
  "status": 404,
  "message": "Short URL code 'aB72x' not found",
  "timestamp": "2026-09-21T17:20:58.0501234"
}
```

| Status Code | Description                                                |
| :---------- | :--------------------------------------------------------- |
| 400         | Invalid URL format or validation failure                   |
| 404         | Short code not found, or no such route/resource            |
| 409         | Custom alias already in use (Conflict)                     |
| 410         | Short URL has expired                                      |
| 500         | Unexpected server error (details are logged, not returned) |
