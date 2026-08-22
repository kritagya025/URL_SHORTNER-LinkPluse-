# Changelog

All notable changes to the LinkPulse project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-08-22

### Added
- Base62 URL shortening engine with 6-character short codes
- HTTP 302 redirect engine with real-time click tracking
- Real-time click analytics with auto-polling dashboard
- Custom expiration date/time support with HTTP 410 Gone for expired links
- Developer dark theme dashboard with search filtering and copy-to-clipboard
- Spring Bean Validation for URL input validation
- Global exception handling with standardized JSON error responses
- PostgreSQL 16 persistence with indexed short-code lookups
- Multi-stage Docker build with jlink custom JRE (197MB image size)
- Nginx reverse proxy for frontend serving and API routing
- Docker Compose orchestration for full-stack deployment
- JUnit 5 test suite with 39 tests (91% line coverage, 90% branch coverage)
- GitHub Actions CI/CD pipeline with automated testing and Docker Hub publishing
- Postman collection for API endpoint testing

### Performance
- 500 req/sec throughput at 50 concurrent connections
- 80.1ms mean response time for redirections
- 55% Docker image size reduction (434MB to 197MB) via jlink optimization
