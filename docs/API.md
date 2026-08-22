# LinkPulse API Reference

Base URL: `http://localhost:80` (Docker) or `http://localhost:8080` (Local)

---

## Create Short URL

```http
POST /api/urls
Content-Type: application/json
```

### Request Body

| Field         | Type     | Required | Description                              |
| :------------ | :------- | :------- | :--------------------------------------- |
| `originalUrl` | `string` | Yes      | The destination URL to shorten           |
| `expiresAt`   | `string` | No       | ISO 8601 expiration timestamp            |

### Example Request

```json
{
  "originalUrl": "https://www.example.com/very/long/path",
  "expiresAt": "2025-12-31T23:59:59"
}
```

### Example Response (201 Created)

```json
{
  "id": 1,
  "originalUrl": "https://www.example.com/very/long/path",
  "shortCode": "aB72xQ",
  "clickCount": 0,
  "createdAt": "2025-08-22T10:30:00",
  "expiresAt": "2025-12-31T23:59:59",
  "status": "ACTIVE"
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

## Delete Short URL

```http
DELETE /api/urls/{shortCode}
```

### Response
- **204 No Content** â€” Successfully deleted.
- **404 Not Found** â€” Short code not found.

---

## Error Response Format

All error responses follow a consistent JSON structure:

```json
{
  "error": "Short URL not found",
  "status": 404
}
```

| Status Code | Description                                  |
| :---------- | :------------------------------------------- |
| 400         | Invalid URL format or validation failure     |
| 404         | Short code not found                         |
| 410         | Short URL has expired                        |
| 500         | Internal server error                        |
