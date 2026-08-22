# LinkPulse Deployment Guide

This guide covers deploying LinkPulse to various environments.

---

## Docker Compose (Recommended)

### Prerequisites
- Docker Engine 24+
- Docker Compose v2+

### Quick Start

```bash
# Clone the repository
git clone https://github.com/kritagya025/URL_SHORTNER-LinkPluse-.git
cd URL_SHORTNER-LinkPluse-

# Build and start all services
docker compose up --build -d

# Verify all containers are running
docker compose ps
```

### Service Ports

| Service    | Container Name              | Port  |
| :--------- | :-------------------------- | :---- |
| Frontend   | url_shortener_frontend      | 80    |
| Backend    | url_shortener_backend       | 8080  |
| PostgreSQL | url_shortener_postgres      | 5432  |

### Stopping Services

```bash
# Stop containers (preserve data)
docker compose down

# Stop containers and remove volumes (reset database)
docker compose down -v
```

---

## Local Development

### Prerequisites
- Java 17 (Temurin JDK)
- Maven 3.9+
- PostgreSQL 16

### Database Setup

```sql
CREATE DATABASE url_shortener_db;
```

### Configuration

Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/url_shortener_db
spring.datasource.username=postgres
spring.datasource.password=your_password
```

### Run

```bash
mvn spring-boot:run
```

Access at: `http://localhost:8080`

---

## Environment Variables

| Variable                     | Default                  | Description              |
| :--------------------------- | :----------------------- | :----------------------- |
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://...`  | Database connection URL  |
| `SPRING_DATASOURCE_USERNAME` | `postgres`               | Database username        |
| `SPRING_DATASOURCE_PASSWORD` | `kritagya`               | Database password        |
| `SERVER_PORT`                | `8080`                   | Backend server port      |

---

## Health Checks

Verify the backend is running:
```bash
curl http://localhost:8080/api/urls
```

Expected response: `200 OK` with a JSON array.

---

## Troubleshooting

### Container won't start
- Check logs: `docker compose logs backend`
- Ensure PostgreSQL is ready before the backend starts.

### Database connection refused
- Verify PostgreSQL container is running: `docker compose ps`
- Check network connectivity: `docker network ls`

### Port conflicts
- Ensure ports 80, 8080, and 5432 are not in use by other services.
