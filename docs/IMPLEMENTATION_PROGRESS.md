# DocSync — Implementation Progress

> **Last Updated:** 2026-10-31  
> **Current Milestone:** M5 — AI-powered documentation generation (complete)  
> **Build:** `mvn test` ✅ 72 tests, 0 failures; `mvn -DskipTests package` ✅

## Current State

| Area | State |
|------|-------|
| M0 Application foundation | 🟡 Complete except deployment-level HTTPS/health aliases (M8) |
| M1 Identity, organizations & tenant isolation | ✅ Complete |
| M2 Repository onboarding | ✅ Complete (FR-065 automatic deferral partial) |
| M3 Webhook processing & job pipeline | ✅ Complete |
| M4 Repository analysis & quality findings | ✅ Complete |
| M5 AI-powered documentation generation | ✅ Complete (LLM provider integration mocked/unverified) |
| M6–M8 | ⬜ Not started |

## Work Completed

### Audit and baseline repair (2026-10-31)

- Verified existing source against `REQUIREMENTS.md`, `HLD.md`, `LLD.md`, `DATA_MODEL.md`, and `API_SPEC.md`.
- Reconstructed the missing implementation plan and progress documents.
- Fixed the existing build (it did not compile before this session):
  - Removed unversioned `flyway-database-postgresql` (Spring Boot 3.2.5 manages Flyway 9.x,
    which includes PostgreSQL support in `flyway-core`).
  - Fixed `BaseEntityTest` protected lifecycle-method access with test wrappers.
- Adjusted `ErrorHandlingTest` so an unknown public route is used to assert the `404`
  envelope; protected routes now correctly return `401` before routing.

### M1 — Identity, organizations & tenant isolation

- Added `V2__identity_and_organizations.sql` with users, organizations, and memberships
  (UUID keys, FKs, case-insensitive email index, optimistic-lock version columns).
- Identity module (`com.docsync.identity`):
  - `User` entity and repository.
  - Email/password registration and login using BCrypt (cost 12).
  - GitHub OAuth authorization-code exchange (`GitHubOAuthClient`) with verified-email
    fallback and account linking by GitHub ID or email.
  - JWT access tokens (HMAC-SHA256, 15-minute default, issuer `docsync`).
  - `GET/PATCH /api/v1/users/me`.
- Organization module (`com.docsync.organization`):
  - `Organization`, `OrganizationMembership`, role hierarchy, membership status.
  - Organization create/list/get/update.
  - Member list/add/role-update/remove with last-owner protection.
  - Every operation requires an active membership; tenant mismatches return `403`.
- Security:
  - Stateless JWT resource server; public auth, health/info, and webhook endpoints only.
  - JSON `401`/`403` responses with trace IDs.
  - JWT secret length validation (minimum 32 bytes).
- API additions beyond the original spec: `POST /api/v1/auth/register` and
  `POST /api/v1/auth/github` (OAuth code exchange).

### M2 — Repository onboarding

- GitHub App authentication (`com.docsync.github`):
  - PEM private-key loader accepting PKCS#8 and PKCS#1 without extra dependencies.
  - RS256 app JWT with 60-second backdating and 9-minute expiry.
  - Installation-token service with a five-minute refresh buffer and concurrency-safe
    per-installation loading; tokens live in memory only (ADR-003).
  - REST client for token creation, installation repository listing (paginated), and
    repository metadata.
- Rate-limit guard (FR-065):
  - `X-RateLimit-Remaining/Limit/Reset` are recorded after every GitHub response.
  - Non-critical calls are refused with `503` + `Retry-After` when remaining quota is
    below 10%; installation-token creation remains critical and always proceeds.
  - Automatic resumption of deferred work requires the M3 job queue, so the guard fails
    fast rather than blocking a request thread. Documented as partial.
- Connection module (`com.docsync.connection`):
  - Installation webhook upsert keyed by `(provider, installationId)`.
  - Auto-link when the installer is a DocSync user who administers exactly one
    organization; otherwise the connection stays `PENDING`.
  - Installer-only claim endpoint; claim imports all currently granted repositories.
  - Revoke, suspend, unsuspend, delete, and repository add/remove handling.
  - Token invalidation on suspend/revoke/delete.
- Repository module (`com.docsync.repository`):
  - `V3__repository_onboarding.sql` with connections, repositories, webhook events.
  - Status lifecycle `PENDING → CLONING → READY`, plus `ERROR` and `ARCHIVED`
    (`INDEXING` reserved for M4).
  - Metadata refresh on every sync (FR-005); clone URL persisted for FR-002.
  - JSONB configuration merge with path-traversal validation for `excludePaths`.
  - Disconnect archives the repository and removes its workspace best-effort.
- Git checkout (`GitCliRepositoryCloner`):
  - HTTPS clone URLs built from a validated host and `owner/repo` name.
  - Token passed through `GIT_CONFIG_*` `http.extraheader`, never the command line.
  - Interactive prompts disabled, shallow single-branch clone, per-command timeout.
  - Path-confined per-repository workspace; captured output capped and token-redacted.
  - Repository code is never executed.
- Webhook module (`com.docsync.webhook`):
  - Constant-time HMAC-SHA256 verification of `X-Hub-Signature-256`.
  - Delivery-id idempotency via unique `(provider, delivery_id)`.
  - Persisted `RECEIVED`/`PROCESSED`/`IGNORED`/`FAILED` status for later replay.
  - Unsupported events (push, pull_request, ...) stored as `IGNORED` for M3.
- Shared tenant authorization (`OrganizationAuthorizationService`) used by organizations,
  connections, and repositories.

### M3 — Webhook processing & job pipeline

- V4 migration for analysis jobs, pull requests, repository processing state.
- Durable PostgreSQL queue with `FOR UPDATE SKIP LOCKED`, polling, retries (1/5/15 minutes), stale-job recovery, and dead-letter handling.
- Push and pull-request webhook routing with repository resolution, monitored-branch filtering, idempotent delivery handling, and job enqueueing.
- Changed-file detection via GitHub compare plus push-payload fallback, including rename inference.
- Pull-request tracking and job status APIs plus a job-queue health indicator.
- Tests: `WebhookProcessingApiTest` (4).

### M4 — Repository analysis & quality findings

- V5 migration for analyses, code entities, documentation pages, code references, and findings.
- Java source parsing with JavaParser, documentation discovery, code-reference extraction, and symbol-index persistence.
- Full and incremental analysis, impact findings, missing-documentation findings, and OpenAPI endpoint counting.
- Analysis, findings, and documentation APIs.
- Tests: `AnalysisApiTest` (1).

### M5 — AI-powered documentation generation

- V6 migration for generation runs and generated documentation changes.
- Provider-agnostic LLM adapter with OpenAI and mock implementations.
- Prompt templates, context truncation, output validation, unified diff generation.
- Generation, approval, rejection, and regeneration APIs with version tracking.
- Tests: `GenerationApiTest` (2).

### M7 — Frontend and backend integration

- React 19 + TypeScript + Vite frontend in `frontend/`.
- Premium responsive UI with light/dark theme, reusable design system, dashboard, organizations, repositories, connections, analysis, documentation review, and jobs screens.
- JWT auth, protected routes, organization selection, TanStack Query integration, mutation handling, polling, Markdown preview, and unified diff review.
- Backend gaps closed: global job listing and documentation page content retrieval.
- Frontend verification: build, lint (warnings only), and 1 smoke test pass.
- Browser automation verified login, registration, organization creation, dashboard, navigation, theme switching, and logout.

## Files Changed

- `pom.xml` — added OAuth2 resource server (Nimbus JWT); removed invalid Flyway module.
- `src/main/resources/application.yml` — JWT and GitHub OAuth configuration.
- `src/main/resources/db/migration/V2__identity_and_organizations.sql`
- `src/main/java/com/docsync/config/SecurityConfig.java` — JWT, password encoder, JSON auth errors.
- `src/main/java/com/docsync/config/GlobalExceptionHandler.java` — 401 credentials, 409 integrity.
- `src/main/java/com/docsync/identity/**` — entity, DTOs, GitHub client, JWT, service, controllers.
- `src/main/java/com/docsync/organization/**` — entities, DTOs, repositories, service, controller.
- `src/test/java/com/docsync/identity/AuthApiTest.java`
- `src/test/java/com/docsync/identity/GitHubAuthApiTest.java`
- `src/test/java/com/docsync/organization/OrganizationApiTest.java`
- `src/test/java/com/docsync/common/BaseEntityTest.java`
- `src/test/java/com/docsync/config/ErrorHandlingTest.java`
- `src/test/resources/application-test.yml`

M2 additions:

- `src/main/resources/db/migration/V3__repository_onboarding.sql`
- `src/main/java/com/docsync/github/**` — PEM loader, app JWT, token service, REST client,
  rate-limit guard, exceptions.
- `src/main/java/com/docsync/connection/**` — entity, repository, DTO, service, controller.
- `src/main/java/com/docsync/repository/**` — entity/status/configuration, DTOs, repository,
  service, controller, git cloner.
- `src/main/java/com/docsync/webhook/**` — verifier, service, controller, event entity,
  repository, DTO, exceptions.
- `src/main/java/com/docsync/organization/service/OrganizationAuthorizationService.java`
- `src/test/java/com/docsync/github/GitHubAppJwtGeneratorTest.java`
- `src/test/java/com/docsync/github/InstallationTokenServiceTest.java`
- `src/test/java/com/docsync/github/GitHubRateLimitGuardTest.java`
- `src/test/java/com/docsync/webhook/GitHubWebhookVerifierTest.java`
- `src/test/java/com/docsync/repository/clone/GitCliRepositoryClonerTest.java`
- `src/test/java/com/docsync/repository/RepositoryOnboardingApiTest.java`
- `src/main/resources/application.yml`, `src/test/resources/application-test.yml` —
  GitHub API URL, workspace root, clone host, git timeout.

M3–M5 additions:

- `src/main/resources/db/migration/V4__processing_pipeline.sql`
- `src/main/resources/db/migration/V5__analysis_engine.sql`
- `src/main/resources/db/migration/V6__documentation_generation.sql`
- `src/main/java/com/docsync/jobs/**`
- `src/main/java/com/docsync/changedetection/**`
- `src/main/java/com/docsync/pullrequest/**`
- `src/main/java/com/docsync/analysis/**`
- `src/main/java/com/docsync/apispec/**`
- `src/main/java/com/docsync/docgen/**`
- `src/main/java/com/docsync/common/adapter/llm/**`
- `src/main/resources/prompts/doc-update.md`
- `src/test/java/com/docsync/jobs/WebhookProcessingApiTest.java`
- `src/test/java/com/docsync/analysis/AnalysisApiTest.java`
- `src/test/java/com/docsync/docgen/GenerationApiTest.java`
- `pom.xml` — JavaParser dependency

## Tests Executed:

| Command | Result |
|---------|--------|
| `mvn test` | ✅ 32 tests, 0 failures (before adding GitHub OAuth test) |
| `mvn -Dtest=GitHubAuthApiTest test` | ✅ 1 test, 0 failures |
| `mvn test` (final run) | ✅ 33 tests, 0 failures, exit 0 |
| `mvn -DskipTests package` | ✅ `target/docsync-0.1.0-SNAPSHOT.jar` built, exit 0 |
| `mvn test` (M2 final) | ✅ 65 tests, 0 failures, exit 0 |
| `mvn -DskipTests package` (M2 final) | ✅ `target/docsync-0.1.0-SNAPSHOT.jar` (55.7 MB), exit 0 |
| `mvn test` (M3–M5 final) | ✅ 72 tests, 0 failures, exit 0 |
| `mvn -DskipTests package` (M3–M5 final) | ✅ `target/docsync-0.1.0-SNAPSHOT.jar`, exit 0 |
| `npm run build` (M7 final) | ✅ Vite production build succeeds |
| `npm run lint` (M7 final) | ✅ Warnings only, no errors |
| `npm run test` (M7 final) | ✅ 1 test, 0 failures |

Coverage includes registration, login (valid/invalid), duplicate accounts, input
validation, unauthenticated access, profile update, GitHub OAuth account creation and
idempotency, organization creation, duplicate slugs, cross-tenant access denial, role
escalation enforcement, member removal, and last-owner protection.

M2 coverage adds: RS256 claim/expiry and PKCS#1 key loading, token cache refresh/
invalidation/concurrency, rate-limit threshold and `503` mapping, webhook signature and
tamper rejection, clone input validation and token redaction, installation auto-link,
delivery idempotency, repository add/remove, installer-only claim, role enforcement,
connect/configure/sync/disconnect flow, archive behaviour, and sync error/retry.

## Remaining Tasks (M3)

1. Route `push` and `pull_request` webhook events into a durable `analysis_jobs` queue.
2. Add polling workers, retries, dead-letter handling, and stale-lock recovery.
3. Replay stored `IGNORED` webhook deliveries once handlers exist.
4. Replace the FR-065 fail-fast guard with queued deferral and automatic resumption.
5. Add repository status updates driven by the job pipeline (`INDEXING`).

## Known Blockers / Notes

- **PostgreSQL/Flyway unverified.** Docker is not available on this machine, so V1–V3 have
  not been executed against PostgreSQL; tests use H2 with `ddl-auto: create-drop` and
  Flyway disabled. Run `docker-compose up -d && mvn spring-boot:run` once Docker is
  available to validate the migrations.
- **No `git` binary on the build machine.** `git --version` fails here, so real
  clone/fetch behaviour is not runtime-verified. The cloner reports
  `Git is not available on this host`; integration tests mock `GitRepositoryCloner`, and
  the cloner unit test exercises validation/redaction paths.
- **No real GitHub App credentials.** All GitHub HTTP behaviour is mocked. The app JWT,
  token cache, rate-limit guard, and webhook verification are unit-tested, but no live
  GitHub call has been made.
- **FR-065 is partial.** Header tracking and refusal are implemented; automatic resumption
  of deferred non-critical calls still needs durable retry orchestration.
- **M5 LLM integration is mock-tested only.** `MockLlmClient` is active in the test profile;
  no live OpenAI/Anthropic call has been made. `OpenAiLlmClient` is implemented but unverified.
- **Migrations V4–V6 are unverified on PostgreSQL** for the same Docker-availability reason.
- Production must override `JWT_SECRET` (the checked-in default is development-only).
- Installation tokens and App private keys are never logged or persisted.
- No Git repository is initialised in this workspace, so changes are not committed.
