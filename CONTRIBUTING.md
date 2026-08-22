# Contributing to LinkPulse

Thank you for your interest in contributing to LinkPulse! This document provides guidelines and instructions for contributing.

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/URL_SHORTNER-LinkPluse-.git
   cd URL_SHORTNER-LinkPluse-
   ```
3. Create a **feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

### Prerequisites
- Java 17 (Temurin recommended)
- Maven 3.9+
- PostgreSQL 16
- Docker & Docker Compose (optional)

### Running Locally
```bash
mvn spring-boot:run
```

### Running Tests
```bash
mvn clean test jacoco:report
```

## Code Style Guidelines

- Follow standard Java naming conventions.
- Use meaningful variable and method names.
- Write Javadoc comments for all public classes and methods.
- Maintain existing code formatting and indentation.

## Pull Request Process

1. Ensure all tests pass locally before submitting.
2. Update documentation if your changes affect the API or user-facing behavior.
3. Write a clear PR description explaining the **what** and **why** of your changes.
4. Link any related issues in the PR description.

## Reporting Bugs

Open a GitHub Issue with:
- A clear, descriptive title.
- Steps to reproduce the bug.
- Expected vs. actual behavior.
- Environment details (OS, Java version, Docker version).

## Feature Requests

Open a GitHub Issue tagged with `feature-request` and describe:
- The problem your feature solves.
- Your proposed solution.
- Any alternatives you considered.

## Code of Conduct

Please review and follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all interactions.

---

Thank you for helping make LinkPulse better!
