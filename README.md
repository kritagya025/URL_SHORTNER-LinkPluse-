# LinkPulse — URL Shortener & Real-Time Analytics Service

A clean, production-ready, interview-grade **Full-Stack URL Shortener & Analytics Platform** built with **Spring Boot 3**, **Java 17**, **PostgreSQL**, and **Vanilla HTML5/CSS3/JavaScript**.

---

## 🌟 Features

- **URL Shortening**: Generates unique, 6-character Base62 short links for long URLs.
- **HTTP 302 Redirects**: Fast redirection to original destinations while automatically logging clicks.
- **Click Tracking & Real-Time Analytics**: Tracks visit counts and timestamps in PostgreSQL.
- **Optional URL Expiration**: Supports setting explicit date/time expiration thresholds. Expired links return HTTP 410 Gone.
- **URL Management Table**: Simple frontend dashboard displaying all created links, click counters, active/expired status badges, copy-to-clipboard, and link deletion.
- **Centralized Exception Handling**: Standardized REST error payloads with appropriate HTTP status codes (`400`, `404`, `410`, `500`).
- **Input Validation**: Spring Bean Validation ensuring valid URL formats and expiration constraints.

---

## 🛠️ Technology Stack

### Frontend
- **HTML5** & **Vanilla CSS3** (Sleek dark mode glassmorphism design system)
- **Vanilla JavaScript (ES6+)** with **Fetch API**

### Backend
- **Java 17** / **Spring Boot 3.2.5**
- **Spring Web (REST APIs)**
- **Spring Data JPA** & **PostgreSQL**
- **HikariCP** Connection Pooling
- **Lombok** & **Bean Validation**
- **JUnit 5** & **Spring Boot Test / MockMvc**

---

## 🏛️ System Architecture

```text
               USER (Web Browser)
                       |
                       ↓
                   FRONTEND
             HTML / CSS / JavaScript
                       |
                   HTTP / REST
                       |
                       ↓
               SPRING BOOT API (8080)
            ┌──────────┴──────────┐
            ↓                     ↓
    RedirectController      UrlController
            │                     │
            └──────────┬──────────┘
                       ↓
                   UrlService
                       |
                       ↓
                 UrlRepository
                       |
                       ↓
             PostgreSQL Database (5432)
```

---

## 🗄️ Database Schema

### Table: `urls`

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY`, Auto-Increment | Unique primary identifier |
| `original_url` | `TEXT` | `NOT NULL` | Destination URL |
| `short_code` | `VARCHAR(10)` | `NOT NULL`, `UNIQUE`, `INDEX` | Indexed short alphanumeric code |
| `click_count` | `BIGINT` | `NOT NULL`, Default `0` | Total redirected visits counter |
| `created_at` | `TIMESTAMP` | `NOT NULL` | Creation timestamp |
| `expires_at` | `TIMESTAMP` | `NULLABLE` | Optional expiration timestamp |

---

## 🔌 REST API Endpoints

| Method | Endpoint | Description | Request Body | Response Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/urls` | Create short URL | `{ originalUrl, expiresAt }` | `201 Created` |
| **GET** | `/{shortCode}` | Redirect to original URL | None | `302 Found` |
| **GET** | `/api/urls/{shortCode}/stats` | Get link statistics | None | `200 OK` |
| **GET** | `/api/urls` | List all created URLs | None | `200 OK` |
| **DELETE**| `/api/urls/{shortCode}` | Delete short URL | None | `204 No Content` |

---

## 🚀 How to Run Locally

### 1. PostgreSQL Database Setup
1. Ensure PostgreSQL is installed and running on `localhost:5432`.
2. Connect to PostgreSQL using `psql` or pgAdmin:
   ```sql
   CREATE DATABASE url_shortener_db;
   ```
3. Update `src/main/resources/application.properties` with your PostgreSQL password if different:
   ```properties
   spring.datasource.username=postgres
   spring.datasource.password=kritagya
   ```

### 2. Start Backend (Spring Boot)
Open a terminal in the root project directory:
```bash
mvn spring-boot:run
```
The backend API will start at `http://localhost:8080`.

### 3. Open Frontend
You can launch the frontend using VS Code Live Server or python HTTP server:
```bash
cd frontend
python -m http.server 5500
```
Open `http://localhost:5500` in your web browser!

---

## 🧪 Running Automated Tests

To execute the test suite (16 Unit & Controller Integration tests):
```bash
mvn test
```

---

## 📬 Postman Testing

Import `URL_Shortener.postman_collection.json` into Postman to test pre-configured endpoints:
- Create URL
- Get All URLs
- Redirect Short URL
- Get URL Statistics
- Delete Short URL
- Validation Error Handling
- Expired URL Handling

---

## 🔮 Future Improvements (Part 2 & Part 3)

The following deployment & containerization features will be added in upcoming project parts:
- [ ] **Part 2**: Dockerization (`Dockerfile` for Spring Boot & Frontend, `docker-compose.yml` orchestrating PostgreSQL, Backend, and Frontend containers).
- [ ] **Part 3**: GitHub Actions CI/CD pipeline (automated build, test, and containerized deployment).
