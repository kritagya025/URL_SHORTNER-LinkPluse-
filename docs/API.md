# LinkPulse API Reference

Base URL: `http://localhost:80` (Docker) or `http://localhost:8080` (Local)

---

## Create Short URL

```http
POST /api/urls
Content-Type: application/json
```

### Request Body

| Field         | Type     | Required | Description                                                                                 |
| :------------ | :------- | :------- | :------------------------------------------------------------------------------------------ |
| `originalUrl` | `string` | Yes      | Destination URL to shorten (`http://` or `https://`, max 2,048 chars, public hosts only)    |
| `expiresAt`   | `string` | No       | ISO 8601 expiration timestamp (must be in the future)                                       |
| `customAlias` | `string` | No       | Optional custom short code (3-10 alphanumeric chars, non-reserved)                          |

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

### Rate Limiting Headers

`POST /api/urls` is rate-limited per client IP (default: `30` requests per `60` seconds). Every response includes:
- `X-RateLimit-Limit`: Maximum requests allowed in the current window
- `X-RateLimit-Remaining`: Remaining requests before throttling
- `X-RateLimit-Reset`: Epoch seconds when the current window resets

When exceeded, the API responds with `429 Too Many Requests` and a `Retry-After` header (in seconds).

---

## Redirect Short URL

```http
GET /{shortCode}
```

### Response
- **302 Found** - Redirects to the original URL with `Cache-Control: no-store, no-cache, must-revalidate` and `Pragma: no-cache` headers so expired or deleted links stop redirecting immediately and click counts remain accurate.
- **410 Gone** - The short URL has expired.
- **404 Not Found** - The short code does not exist.

---

## Get Link Statistics

```http
GET /api/urls/{shortCode}/stats
```

### Example Response (200 OK)

```json
{
  "originalUrl": "https://www.example.com/very/long/path",
  "shortCode": "aB72xQ",
  "shortUrl": "http://localhost:8080/aB72xQ",
  "clickCount": 42,
  "createdAt": "2026-10-19T10:30:00",
  "expiresAt": "2026-12-31T23:59:59",
  "status": "ACTIVE"
}
```

---

## List & Search URLs

```http
GET /api/urls
GET /api/urls?query={searchTerm}&status={ALL|ACTIVE|EXPIRED}
```

### Query Parameters

| Parameter | Type     | Required | Description                                                         |
| :-------- | :------- | :------- | :------------------------------------------------------------------ |
| `query`   | `string` | No       | Case-insensitive filter matching short code or destination URL      |
| `status`  | `string` | No       | Lifecycle status filter: `ALL` (default), `ACTIVE`, or `EXPIRED`    |

### Response (200 OK)

Returns an array of matching URL objects sorted by creation date descending.

---

## Generate QR Code

```http
GET /api/urls/{shortCode}/qr
GET /api/urls/{shortCode}/qr?size=300
```

Generates a PNG image of a square QR code encoding the complete shortened link URL.

### Query Parameters

| Parameter | Type      | Required | Default | Description                                                     |
| :-------- | :-------- | :------- | :------ | :-------------------------------------------------------------- |
| `size`    | `integer` | No       | `300`   | Dimension in pixels (width and height clamped between 64–1024)  |

### Response
- **200 OK** (`image/png`) - Binary PNG QR code image data.
- **404 Not Found** - Short code does not exist.
- **410 Gone** - The short link has expired.

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

## Purge Expired URLs

```http
DELETE /api/urls/expired
```

Bulk deletes all short URL records whose expiration timestamp has elapsed.

### Example Response (200 OK)

```json
{
  "deletedCount": 4,
  "message": "Successfully purged 4 expired URLs"
}
```

### Response Codes
- **200 OK** - Expired links successfully purged (or 0 purged if none expired).

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
- **200 OK** - Service is operational and database is reachable.
- **503 Service Unavailable** - Service is degraded or database connection failed.

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

| Status Code | Description                                                                          |
| :---------- | :----------------------------------------------------------------------------------- |
| 400         | Invalid URL format, SSRF host rejection, length > 2048 chars, malformed JSON, or invalid parameter type |
| 404         | Short code not found, or no such route/resource                                      |
| 405         | Unsupported HTTP method for the requested endpoint                                   |
| 409         | Custom alias already in use (Conflict)                                               |
| 410         | Short URL has expired                                                                |
| 429         | Rate limit exceeded on `POST /api/urls` (includes `Retry-After` header)              |
| 500         | Unexpected server error (details are logged, not returned)                           |
