# DocSync — Implementation Plan

> **Status:** Living document  
> **Last Updated:** 2026-10-31  
> **Source of Truth:** `REQUIREMENTS.md` (FR/NFR catalog) and `docs/ADR/`

This plan converts the requirements catalog into deliverable milestones. A milestone is
complete only when its code is merged, its automated tests pass, and the corresponding
behaviour is verified. Progress is recorded in `IMPLEMENTATION_PROGRESS.md`.

## Milestone Overview

| ID | Milestone | Requirements | Depends on | Status |
|----|-----------|--------------|------------|--------|
| M0 | Application foundation | FR-063*, FR-064*, NFR-007–NFR-010, NFR-016, NFR-020, NFR-022, NFR-023 | — | 🟡 Partially complete |
| M1 | Identity, organizations & tenant isolation | FR-063, FR-064, NFR-007, NFR-010 | M0 | ✅ Complete |
| M2 | Repository onboarding | FR-001–FR-010, FR-065, NFR-021 | M1 | ✅ Complete (FR-065 automatic deferral partial) |
| M3 | Webhook ingestion & job pipeline | FR-011–FR-020, NFR-001, NFR-006, NFR-013, NFR-019 | M2 | ✅ Complete |
| M4 | Repository analysis & quality findings | FR-021–FR-030, FR-041–FR-042, FR-059–FR-060, NFR-002, NFR-003 | M3 | ✅ Complete |
| M5 | Documentation generation | FR-031–FR-040, NFR-005, NFR-014 | M4 | ✅ Complete (LLM provider integration mocked/unverified) |
| M6 | Pull request integration | FR-051–FR-057 | M5 | ⬜ Not started |
| M7 | Example validation & reporting | FR-043–FR-049 | M4 | ⬜ Not started |
| M8 | Operational readiness | FR-008, FR-020, FR-040, FR-048, FR-050, FR-058, FR-061–FR-062, NFR-004, NFR-011–NFR-012, NFR-015, NFR-017–NFR-018, NFR-024–NFR-025 | All | ⬜ Not started |

\* M0 supplied the structural prerequisites (security filter chain, error format, UUID base
entity, health/metrics, Flyway) for FR-063/FR-064; the actual behaviour is delivered in M1.

## M0 — Application foundation

- [x] Spring Boot 3.2.5 / Java 21 Maven project, PostgreSQL + Flyway, H2 test profile
- [x] `BaseEntity` (UUID, audit timestamps, optimistic locking)
- [x] Global exception handler with consistent `ApiErrorResponse`
- [x] Trace-ID filter and structured logging configuration
- [x] Actuator health/info/metrics/Prometheus endpoints
- [x] HikariCP configuration
- [x] Initial schema migration (`V1__initial_schema.sql`, audit events)
- [x] Baseline security configuration
- [ ] HTTPS enforcement / TLS termination (deployment concern, M8)
- [ ] `/health` and `/health/ready` aliases distinct from actuator (M8)

## M1 — Identity, organizations & tenant isolation

- [x] Users, organizations, memberships schema (`V2`)
- [x] Email/password registration and login with BCrypt (strength 12)
- [x] GitHub OAuth code exchange and account linking
- [x] Short-lived HMAC-SHA256 JWT access tokens, stateless authentication
- [x] Consistent JSON `401`/`403` responses
- [x] Organization CRUD with OWNER/ADMIN/MEMBER/VIEWER roles
- [x] Membership invite/add, role updates, removal, last-owner protection
- [x] Tenant-scoped authorization on every organization operation
- [x] User profile read/update endpoints
- [x] Automated unit/integration coverage for auth, OAuth, and tenancy

## M2 — Repository onboarding

- [x] GitHub App JWT (RS256) generation and installation-token cache (FR-009, NFR-007)
- [x] `Repository` entity and installation linkage (FR-002, FR-007)
- [x] `installation` / `installation_repositories` webhook handling (FR-001)
- [x] Repository status lifecycle and error recording (FR-004)
- [x] Shallow clone/fetch into a per-tenant workspace using installation tokens (FR-003)
- [x] Repository list/detail/configuration/disconnect API (FR-010, API_SPEC)
- [x] Repository metadata sync and uninstall deactivation (FR-005, FR-006)
- [x] Tests for installation idempotency, token refresh, and tenant scoping
- [x] GitHub rate-limit header tracking and non-critical request refusal (FR-065)

FR-065 note: headers are recorded after every GitHub call, and non-critical calls are
refused with `503` + `Retry-After` once remaining quota is below 10%. Automatic resumption
of deferred work requires the durable job queue delivered in M3; the guard deliberately
fails fast instead of blocking a request thread.

## M3 — Webhook ingestion & background jobs

- [x] HMAC-SHA256 webhook verification, replay protection, deduplication (FR-011–FR-020)
- [x] Durable `analysis_jobs` queue with polling, retries, dead-letter handling
- [x] Push/PR event routing and repository status updates
- [x] Job health indicator backed by real queue metrics
- [x] Changed-file detection and commit/PR tracking

## M4 — Repository analysis & quality findings

- [x] Java source parsing and symbol index (JavaParser)
- [x] Documentation discovery and code-reference extraction
- [x] Change impact analysis and findings
- [x] Incremental indexing
- [x] OpenAPI parsing
- [x] Analysis APIs

## M5 — AI-powered documentation generation

- [x] Provider-agnostic LLM adapter (OpenAI + mock)
- [x] Prompt templates, context truncation, validation, diff generation
- [x] Generation, approval, rejection, regeneration APIs
- [x] Generation history/version tracking

## M4–M8 — Analysis through operations

Detailed design is in `LLD.md` sections 2.5–2.13. Each milestone should be delivered as a
vertical slice with migrations, service logic, API, and tests, following the dependency
order in the table above.

## Cross-Cutting Rules

- Additive Flyway migrations only; never edit applied migrations.
- Every module exposes a service interface; no cross-module repository access.
- All tenant data access verifies active organization membership.
- Never execute untrusted repository code on the host; use sandboxed validation.
- Secrets come from environment variables and are never logged.
- New endpoints require validation, authorization, and tests before the milestone closes.
