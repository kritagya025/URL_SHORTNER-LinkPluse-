# Changelog

All notable changes to the LinkPulse project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-09-21

### Added
- Light and dark theme with a header toggle, `prefers-color-scheme` default and persistence via `localStorage`
- Sortable dashboard columns (original link, short link, clicks, created, expires) with `aria-sort` indicators
- Per-row click bars scaled against the busiest link for at-a-glance click distribution
- Relative timestamps ("3d ago", "in 4d") alongside absolute dates, with full dates on hover
- Backend reachability indicator in the header that flips to "Backend Offline" when polling fails
- Accessible confirmation dialog for link deletion, replacing the native `confirm()` prompt
- Keyboard shortcuts: `/` focuses the dashboard filter, `Esc` closes the dialog or clears the filter
- Skeleton loading rows on first paint and contextual empty states for "no links" vs "no matches"
- `+30 Days` expiration preset and active-state highlighting on preset buttons
- Inline SVG favicon and JetBrains Mono for short codes and generated links

### Changed
- Rebuilt the stylesheet around semantic design tokens so both themes share one system
- Dashboard table collapses into stacked cards below 760px instead of scrolling horizontally
- Original URLs render as host plus path on two lines rather than one truncated string
- Row actions use event delegation and data attributes instead of inline `onclick` handlers
- Polling pauses while the browser tab is in the background
- Toasts carry status icons, an `aria-live` region and an exit animation

### Fixed
- Expiration timestamps were converted to UTC via `toISOString()` before being sent to the backend, which
  compares against a local `LocalDateTime`; the picked wall-clock time is now sent unshifted, so presets no
  longer land in the past for users ahead of UTC
- Deleting the link currently open in the inspector now clears the inspector instead of leaving stale data
- Synced `src/main/resources/static/` with `frontend/`, which had drifted four commits behind

### Accessibility
- Visible focus rings on all interactive elements, keyboard-operable sort headers, and a
  `prefers-reduced-motion` guard that disables animations

## [1.0.0] - 2025-08-22

### Added
- Random Base62 short-code generation with collision checking for 6-character short codes
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
