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

## Security Best Practices for Deployment

- Always use HTTPS in production environments.
- Rotate database credentials regularly.
- Use Docker Secrets or environment variables for sensitive configuration.
- Keep all dependencies updated to their latest stable versions.
- Enable rate limiting on the URL creation endpoint to prevent abuse.
