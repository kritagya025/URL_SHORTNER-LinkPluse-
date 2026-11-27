# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in LinkPulse, please report it responsibly.

### How to Report

1. **Do NOT** open a public GitHub Issue for security vulnerabilities.
2. Email us at **kritagyay2006@gmail.com** with:
   - A description of the vulnerability
   - Steps to reproduce
   - Potential impact assessment
   - Any suggested fixes (optional)

### Response Timeline

- **Acknowledgment**: Within 48 hours of receiving the report.
- **Initial Assessment**: Within 5 business days.
- **Resolution**: We aim to release patches within 14 days for critical vulnerabilities.

### Disclosure Policy

- We follow a **coordinated disclosure** process.
- We will credit reporters in the changelog (unless anonymity is requested).
- Please allow us reasonable time to address the issue before public disclosure.

## Security Controls Implemented

LinkPulse enforces defense-in-depth protections across the application and deployment layers:

- **SSRF Protection**: `UrlService` validates destination URLs against private, loopback (`localhost`, `127.0.0.0/8`, `::1`), link-local (`169.254.0.0/16`, `fe80::/10`), CGNAT (`100.64.0.0/10`), IPv6 ULA (`fc00::/7`), `.local`/`.internal` hostnames, and obfuscated numeric/hex/octal IPv4 forms across all resolved DNS records.
- **OWASP Headers & CSP**: `SecurityHeadersFilter` attaches `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy`, and `Permissions-Policy` to every HTTP response.
- **Rate Limiting**: `RateLimitFilter` enforces per-IP fixed-window throttling on `POST /api/urls` (default 30 requests/minute) with `X-RateLimit-*` and `Retry-After` headers and bounded memory eviction.
- **Credential Hygiene**: Database passwords are externalized via environment variables / `.env` files (see `.env.example`), with no hardcoded production credentials in configuration or Compose manifests.
- **Error Sanitization**: `GlobalExceptionHandler` returns structured JSON error responses (`400`, `404`, `405`, `409`, `410`, `500`) without exposing stack traces, SQL text, or internal paths.

## Security Best Practices for Deployment

- Always use HTTPS in production environments.
- Rotate database credentials regularly.
- Use Docker Secrets or environment variables for sensitive configuration.
- Keep all dependencies updated to their latest stable versions.
- Enable rate limiting on the URL creation endpoint to prevent abuse.
