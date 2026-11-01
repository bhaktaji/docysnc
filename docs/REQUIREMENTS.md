# DocSync — Requirements Catalog

> **Version:** 1.0  
> **Last Updated:** 2026-10-30  
> **Status:** Draft  
> **Companion Documents:** [PRD.md](./PRD.md) · [ROADMAP.md](./ROADMAP.md)

---

## How to Read This Document

Each requirement follows this template:

| Field | Description |
|---|---|
| **ID** | Unique identifier (`FR-nnn` for functional, `NFR-nnn` for non-functional) |
| **Title** | Short, descriptive name |
| **Description** | What the system must do (functional) or how well it must perform (non-functional) |
| **Priority** | `P0` = must-have for launch, `P1` = needed soon after, `P2` = important, `P3` = nice-to-have |
| **MVP Phase** | Phase 1–8 in which the requirement is first delivered |
| **Acceptance Criteria** | Testable conditions that prove the requirement is met |

> [!NOTE]
> Priority and MVP phase are correlated but not identical. A P0 requirement is always in an early phase, but a P1 requirement may appear in a later phase if it depends on foundational work.

---

## 1. Functional Requirements

### 1.1 Repository Management (FR-001 – FR-010)

---

#### FR-001 — GitHub App Installation Webhook Handling

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | The system shall receive and process GitHub App `installation` and `installation_repositories` webhook events. Upon receiving an event, the system shall persist the installation ID, account metadata, and the list of granted repositories. |
| **Acceptance Criteria** | 1. A simulated `installation.created` webhook results in a new `Installation` record in the database. 2. A simulated `installation_repositories.added` event adds the specified repositories. 3. Duplicate events are idempotent (no duplicate records). |

---

#### FR-002 — Repository Registration

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | When a repository is granted to the DocSync GitHub App, the system shall create a `Repository` entity storing the GitHub repository ID, full name (owner/repo), default branch, clone URL, and current processing status. |
| **Acceptance Criteria** | 1. After installation, each granted repo has a corresponding database record. 2. The record contains all required fields with correct values sourced from the webhook payload or GitHub API. |

---

#### FR-003 — Repository Cloning / Fetching

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | The system shall clone (first time) or fetch (subsequent times) the repository contents at the HEAD of the default branch. Cloning shall use the GitHub App installation token for authentication. |
| **Acceptance Criteria** | 1. After cloning, the repository's file tree is available on local/temporary storage. 2. Fetch updates the local copy to the latest HEAD. 3. Authentication uses a short-lived installation token, not a hard-coded PAT. |

---

#### FR-004 — Repository Status Tracking

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | Each repository shall have a processing status that transitions through: `PENDING` → `CLONING` → `INDEXING` → `READY` → `ERROR`. The current status and last-updated timestamp shall be persisted. |
| **Acceptance Criteria** | 1. Status transitions are recorded with timestamps. 2. A repository in `ERROR` state includes an error message. 3. Re-processing a failed repository resets the status to `PENDING`. |

---

#### FR-005 — Repository Metadata Synchronization

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 2 |
| **Description** | The system shall periodically synchronize repository metadata (name, default branch, visibility) with GitHub to detect renames, default-branch changes, or deletions. |
| **Acceptance Criteria** | 1. A renamed repository is updated in the database within 1 sync cycle. 2. A deleted / uninstalled repository is marked as `INACTIVE`. |

---

#### FR-006 — Repository Uninstallation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | When the GitHub App is uninstalled or a repository is removed from the installation, the system shall mark the repository as `INACTIVE`, stop processing events for it, and schedule cleanup of cached data. |
| **Acceptance Criteria** | 1. An `installation.deleted` event marks all related repositories as `INACTIVE`. 2. No further webhook events for inactive repositories trigger processing. |

---

#### FR-007 — Multi-Repository Support per Installation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | A single GitHub App installation may grant access to multiple repositories. The system shall handle all granted repositories independently. |
| **Acceptance Criteria** | 1. An installation with 5 repositories results in 5 independent `Repository` records. 2. Processing one repository does not block or affect another. |

---

#### FR-008 — Default Branch Configuration Override

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 7 |
| **Description** | The system shall allow users to override the branch that DocSync monitors (instead of the GitHub default branch) via `docsync.yaml` or the web UI. |
| **Acceptance Criteria** | 1. Setting `branch: develop` in `docsync.yaml` causes DocSync to index and monitor `develop`. 2. Push events to other branches are ignored. |

---

#### FR-009 — Repository Access Token Management

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | The system shall generate short-lived GitHub App installation access tokens on demand for API calls and git operations. Tokens shall be cached until near expiry and refreshed proactively. |
| **Acceptance Criteria** | 1. Tokens are never stored in plain text in the database. 2. A token is refreshed when less than 5 minutes remain before expiry. 3. Concurrent requests for the same installation reuse the cached token. |

---

#### FR-010 — Repository Dashboard Listing

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 2 |
| **Description** | The web UI shall display a list of all onboarded repositories for the authenticated user, showing name, status, last-indexed timestamp, and freshness score. |
| **Acceptance Criteria** | 1. The list loads within 2 seconds. 2. Repositories are sorted by last-indexed timestamp (most recent first) by default. 3. The user can filter by status. |

---

### 1.2 Webhook & Event Processing (FR-011 – FR-020)

---

#### FR-011 — Webhook Endpoint

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | The system shall expose a single HTTPS endpoint (`POST /api/webhooks/github`) that receives all GitHub webhook events. The endpoint shall return `200 OK` within 5 seconds after persisting the raw event. |
| **Acceptance Criteria** | 1. The endpoint accepts valid GitHub webhook payloads. 2. Response time is ≤ 5 s. 3. The raw payload is persisted for reprocessing. |

---

#### FR-012 — Webhook Signature Verification

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | Every incoming webhook shall be verified using the `X-Hub-Signature-256` header and the configured webhook secret (HMAC-SHA256). Requests with missing or invalid signatures shall be rejected with `401 Unauthorized`. |
| **Acceptance Criteria** | 1. A request with a valid signature is accepted. 2. A request with an invalid signature returns `401`. 3. A request with no signature header returns `401`. |

---

#### FR-013 — Push Event Processing

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | When a `push` event is received for a monitored branch, the system shall extract the list of added, modified, and removed files, and enqueue an impact-analysis job. |
| **Acceptance Criteria** | 1. A push with 3 modified Java files results in an impact-analysis job containing those 3 files. 2. Pushes to non-monitored branches are acknowledged but not processed. |

---

#### FR-014 — Pull Request Event Processing

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 3 |
| **Description** | When a `pull_request.opened` or `pull_request.synchronize` event is received, the system shall analyze the PR diff for documentation impact and optionally post a review comment summarizing affected docs. |
| **Acceptance Criteria** | 1. A PR that modifies a public API method triggers an impact summary comment. 2. A PR that changes only test files does not trigger a comment. |

---

#### FR-015 — Event Deduplication

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | The system shall deduplicate webhook events using the `X-GitHub-Delivery` header as an idempotency key. Duplicate events shall be acknowledged but not re-processed. |
| **Acceptance Criteria** | 1. Sending the same event twice (same delivery ID) results in only one processing job. 2. The second request still returns `200 OK`. |

---

#### FR-016 — Event Persistence and Audit Trail

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 3 |
| **Description** | All received webhook events shall be persisted with their delivery ID, event type, repository, timestamp, and processing status (`RECEIVED`, `PROCESSING`, `COMPLETED`, `FAILED`). |
| **Acceptance Criteria** | 1. Events are queryable by repository and status. 2. Failed events include an error message. 3. Events are retained for at least 90 days. |

---

#### FR-017 — Event Retry on Failure

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 3 |
| **Description** | If processing of an event fails (e.g., transient network error during clone), the system shall retry up to 3 times with exponential backoff (1 min, 5 min, 15 min). After exhausting retries, the event is marked `FAILED`. |
| **Acceptance Criteria** | 1. A transiently failing event is retried 3 times. 2. A permanently failing event is marked `FAILED` after 3 attempts. 3. Retry intervals follow the specified backoff. |

---

#### FR-018 — Asynchronous Event Processing

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | Webhook events shall be processed asynchronously. The webhook endpoint shall enqueue the event and return immediately. A background worker pool shall pick up events for processing. |
| **Acceptance Criteria** | 1. The webhook endpoint returns before processing begins. 2. Multiple events can be processed concurrently (configurable concurrency). |

---

#### FR-019 — Event Filtering by File Pattern

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 4 |
| **Description** | The system shall allow configuration of include/exclude file patterns (glob syntax) in `docsync.yaml`. Only files matching the include patterns (and not matching exclude patterns) shall be considered for impact analysis. |
| **Acceptance Criteria** | 1. Setting `include: ["src/main/**/*.java"]` ignores changes to test files. 2. Setting `exclude: ["**/generated/**"]` ignores generated code. |

---

#### FR-020 — Rate Limiting for Webhook Endpoint

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | The webhook endpoint shall enforce rate limiting per installation (default: 60 events/minute). Excess events shall receive `429 Too Many Requests` with a `Retry-After` header. |
| **Acceptance Criteria** | 1. The 61st event within a minute returns `429`. 2. The response includes a `Retry-After` header. 3. The limit is configurable per tenant. |

---

### 1.3 Repository Analysis (FR-021 – FR-030)

---

#### FR-021 — Java Source File Parsing

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | The system shall parse Java source files to extract public API symbols: packages, classes, interfaces, enums, methods (name, parameters, return type), and fields. Parsing shall use a robust parser (e.g., JavaParser, Eclipse JDT) that tolerates incomplete code. |
| **Acceptance Criteria** | 1. A valid Java file with 3 public methods results in 3 method symbol records. 2. A file with syntax errors is partially parsed (best-effort) and flagged. |

---

#### FR-022 — Symbol Index Storage

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | Extracted symbols shall be stored in a structured index (database table) with fields: fully qualified name, kind (class/method/field), file path, line number, signature hash, and repository/commit reference. |
| **Acceptance Criteria** | 1. Symbols are queryable by fully qualified name. 2. The signature hash changes when a method's parameter types change. 3. The index is versioned per commit. |

---

#### FR-023 — Documentation File Discovery

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | The system shall discover documentation files by scanning for Markdown (`.md`), AsciiDoc (`.adoc`), and reStructuredText (`.rst`) files in configured directories (default: `docs/`, `README.md`, repository root). |
| **Acceptance Criteria** | 1. All `.md` files in `docs/` are discovered. 2. `README.md` at the root is always included. 3. Custom directories configured in `docsync.yaml` are scanned. |

---

#### FR-024 — Code Reference Extraction from Documentation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | The system shall scan documentation files for references to source-code symbols: inline code spans containing class/method names, fenced code blocks with Java code, and explicit `@see` or `{@link}` annotations. |
| **Acceptance Criteria** | 1. A Markdown file containing `` `UserService.createUser()` `` produces a reference to `UserService.createUser`. 2. A fenced Java code block that calls `repo.save(entity)` produces a reference to `save`. |

---

#### FR-025 — Code-to-Documentation Dependency Graph

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | The system shall build and persist a directed dependency graph linking source-code symbols to the documentation files and sections that reference them. The graph shall be incrementally updatable. |
| **Acceptance Criteria** | 1. Renaming a method and querying the graph returns all documentation files that reference the old name. 2. Adding a new doc file with code references updates the graph within 30 seconds of indexing. |

---

#### FR-026 — Change Impact Analysis

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | Given a set of changed files from a push event, the system shall compute an impact report listing all documentation files potentially affected, the specific symbols involved, and a severity classification (`BREAKING`, `OUTDATED`, `INFO`). |
| **Acceptance Criteria** | 1. Deleting a public method marks referencing docs as `BREAKING`. 2. Changing a method's return type marks referencing docs as `OUTDATED`. 3. Changing a method's internal implementation (no signature change) marks referencing docs as `INFO`. |

---

#### FR-027 — OpenAPI Specification Parsing

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | The system shall parse OpenAPI 3.x specification files (YAML and JSON) to extract endpoints, HTTP methods, request/response schemas, parameters, and descriptions. |
| **Acceptance Criteria** | 1. A valid OpenAPI 3.0 YAML file is parsed without errors. 2. All `paths` entries are extracted with their operations. 3. Schema `$ref` references are resolved. |

---

#### FR-028 — OpenAPI Diff Detection

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | The system shall diff two versions of an OpenAPI spec and produce a structured changelog: added endpoints, removed endpoints, changed schemas, modified parameters. |
| **Acceptance Criteria** | 1. Adding a new endpoint appears as an `ADDED` entry. 2. Removing a required field from a response schema appears as a `BREAKING` change. 3. The diff is serializable to JSON. |

---

#### FR-029 — Incremental Indexing

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | After the initial full index, subsequent pushes shall trigger incremental indexing: only changed files are re-parsed and the symbol index / dependency graph updated accordingly. |
| **Acceptance Criteria** | 1. A push modifying 2 files out of 500 re-parses only those 2 files. 2. The dependency graph is consistent after incremental update. 3. Incremental indexing is at least 5× faster than full indexing for small changes. |

---

#### FR-030 — Javadoc Extraction

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 4 |
| **Description** | The system shall extract Javadoc comments from public API symbols and include them in the symbol index. Javadoc tags (`@param`, `@return`, `@throws`, `@since`) shall be parsed into structured fields. |
| **Acceptance Criteria** | 1. A method with `@param name The user's name` has a parameter description in the index. 2. Missing Javadoc is recorded as absent (not errored). |

---

### 1.4 Documentation Generation (FR-031 – FR-040)

---

#### FR-031 — LLM Provider Abstraction

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | The system shall define a provider-neutral interface for LLM calls (prompt in, text out). Implementations shall exist for at least one provider (e.g., OpenAI). Switching providers shall require only configuration changes, not code changes. |
| **Acceptance Criteria** | 1. The interface defines `generateText(prompt, options)` and `generateTextStream(prompt, options)`. 2. An OpenAI implementation passes integration tests. 3. Swapping to a mock provider requires changing only a config property. |

---

#### FR-032 — Prompt Template Management

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | The system shall manage versioned prompt templates for each generation task (doc update, API reference, example fix). Templates shall support variable substitution (e.g., `{{className}}`, `{{diff}}`). |
| **Acceptance Criteria** | 1. Templates are stored as files or database records, not hard-coded strings. 2. Variables are substituted correctly. 3. A missing variable raises a clear error, not a silent blank. |

---

#### FR-033 — Documentation Update Generation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | Given an impact report and the current documentation content, the system shall invoke the LLM to produce a minimal, accurate patch that brings the documentation up to date with the code change. |
| **Acceptance Criteria** | 1. The generated patch applies cleanly to the current doc file. 2. The patch modifies only the sections affected by the code change. 3. The patch does not introduce references to symbols that do not exist in the codebase. |

---

#### FR-034 — API Reference Generation from OpenAPI

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 5 |
| **Description** | The system shall generate Markdown API reference pages from a parsed OpenAPI specification. Each endpoint shall have its own section with method, path, description, parameters, request body, response schemas, and example payloads. |
| **Acceptance Criteria** | 1. A spec with 10 endpoints produces a Markdown file with 10 sections. 2. Schema properties include type, description, and required status. 3. Example payloads are syntactically valid JSON. |

---

#### FR-035 — Generation Output Validation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | Before committing generated documentation, the system shall validate it: Markdown must be well-formed, code references must resolve against the symbol index, and no profanity or PII shall be present. |
| **Acceptance Criteria** | 1. A generated doc referencing a non-existent class is rejected. 2. Markdown lint (no broken headings, no unclosed code fences) passes. 3. PII patterns (email, SSN) are detected and flagged. |

---

#### FR-036 — Generation Context Window Management

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 5 |
| **Description** | The system shall manage the LLM context window by selecting the most relevant context chunks (code diff, surrounding code, existing doc sections, style guide) and truncating or summarizing to fit within the model's token limit. |
| **Acceptance Criteria** | 1. Context assembly never exceeds the configured token limit. 2. The most relevant context (direct diff, affected doc section) is always included. 3. Lower-priority context (distant code) is truncated first. |

---

#### FR-037 — Changelog Generation

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 5 |
| **Description** | The system shall generate human-readable changelog entries summarizing what changed in the documentation and why, linked to the originating code commit. |
| **Acceptance Criteria** | 1. Each changelog entry includes the commit SHA, a summary, and the affected doc files. 2. Entries are appended to a `CHANGELOG.md` or included in the PR description. |

---

#### FR-038 — Style Guide Enforcement during Generation

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 5 |
| **Description** | Generated documentation shall conform to the project's configured style guide (e.g., Vale styles). The LLM prompt shall include relevant style rules, and post-generation validation shall check compliance. |
| **Acceptance Criteria** | 1. If the style guide says "use active voice," generated text does not use passive voice. 2. Post-generation Vale check has zero errors for configured rules. |

---

#### FR-039 — Human-in-the-Loop Approval

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | All generated documentation shall be proposed via a pull request; the system shall never auto-merge. A human reviewer must approve and merge. |
| **Acceptance Criteria** | 1. PRs are created with `auto_merge` disabled. 2. The PR body clearly states that a human review is required. |

---

#### FR-040 — Generation Feedback Loop

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 8 |
| **Description** | When a reviewer edits a DocSync-generated PR before merging, the system shall capture the diff between the generated version and the merged version to improve future prompt quality. |
| **Acceptance Criteria** | 1. Merged PRs with reviewer edits store the "generated vs. merged" diff. 2. The diff is available for prompt-tuning analysis. |

---

### 1.5 Quality & Validation (FR-041 – FR-050)

---

#### FR-041 — Vale Integration

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | The system shall run Vale (prose linter) against documentation files using project-specific or default style configurations. Results shall be stored per file with severity, rule, line number, and message. |
| **Acceptance Criteria** | 1. Vale runs successfully with the default Google style. 2. Custom `.vale.ini` from the repository is used when present. 3. Results are stored and queryable per repository. |

---

#### FR-042 — Link Checking

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | The system shall check all internal and external links in documentation files. Internal links are validated against the repository file tree; external links are validated via HTTP HEAD requests with a configurable timeout. |
| **Acceptance Criteria** | 1. A broken internal link (`[guide](./nonexistent.md)`) is flagged. 2. An external link returning `404` is flagged. 3. External link checking respects a 10-second timeout. |

---

#### FR-043 — Code Example Extraction

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 7 |
| **Description** | The system shall extract fenced code blocks from documentation files, capturing the language tag, content, file path, and line range. |
| **Acceptance Criteria** | 1. A doc with 5 fenced Java code blocks results in 5 extracted examples. 2. Code blocks without a language tag are extracted with `unknown` language. |

---

#### FR-044 — Java Code Example Validation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 7 |
| **Description** | The system shall validate Java code examples by resolving class and method references against the symbol index. An example is `VALID` if all references resolve, `BROKEN` if any reference is unresolved, or `UNRESOLVABLE` if the example is too ambiguous to validate. |
| **Acceptance Criteria** | 1. An example calling `UserService.createUser(name)` is `VALID` when `UserService.createUser(String)` exists. 2. An example calling `UserService.deleteUser(id)` is `BROKEN` when that method does not exist. 3. An example with only standard library calls is `VALID`. |

---

#### FR-045 — Validation Report Generation

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 7 |
| **Description** | The system shall generate a validation report for each repository run, aggregating Vale results, link-check results, and code-example validation results into a single structured report. |
| **Acceptance Criteria** | 1. The report includes total issues by severity (`error`, `warning`, `info`). 2. Each issue links to the specific file and line. 3. The report is available via the API and dashboard. |

---

#### FR-046 — Freshness Score Calculation

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 7 |
| **Description** | The system shall compute a freshness score for each repository: `(number of docs with no unresolved impact reports) / (total doc count) × 100`. Scores are recalculated after each push event and stored historically. |
| **Acceptance Criteria** | 1. A repo with 10 docs and 2 outdated docs has a freshness score of 80 %. 2. Scores are stored with timestamps for trend analysis. |

---

#### FR-047 — Quality Gate for PR Checks

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 7 |
| **Description** | The system shall post a GitHub commit status or check run on PRs that touch documentation files, reporting the quality check results. The check can be configured as required (blocking) or optional. |
| **Acceptance Criteria** | 1. A PR with a broken link receives a `failure` check status. 2. A PR with no issues receives a `success` check status. 3. The check name is `docsync/quality`. |

---

#### FR-048 — Scheduled Validation Runs

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 8 |
| **Description** | The system shall support scheduled validation runs (configurable cron, default: weekly) that perform full quality checks and example validation against the latest HEAD, independent of push events. |
| **Acceptance Criteria** | 1. A weekly schedule runs every Sunday at 02:00 UTC by default. 2. Results are stored as a validation run with a timestamp. 3. The schedule is configurable in `docsync.yaml`. |

---

#### FR-049 — Drift Detection

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 7 |
| **Description** | The system shall detect documentation drift: documentation files that have not been updated within a configurable threshold (default: 90 days) after the code they reference was modified. Drifted docs are flagged in the dashboard. |
| **Acceptance Criteria** | 1. A doc referencing code modified 100 days ago (doc unchanged) is flagged as drifted. 2. The threshold is configurable per repository. |

---

#### FR-050 — Validation Result Notifications

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 8 |
| **Description** | The system shall send notifications (email, webhook, or Slack) when validation results exceed configured thresholds (e.g., freshness below 80 %, broken examples > 0). |
| **Acceptance Criteria** | 1. An email is sent when freshness drops below the threshold. 2. Notifications are not sent more than once per day per trigger. |

---

### 1.6 Pull Request Management (FR-051 – FR-058)

---

#### FR-051 — Feature Branch Creation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 6 |
| **Description** | The system shall create a feature branch in the target repository for each documentation update. Branch naming convention: `docsync/update-<short-hash>`. |
| **Acceptance Criteria** | 1. The branch is created from the HEAD of the default branch. 2. Branch name follows the convention. 3. Multiple concurrent updates produce distinct branches. |

---

#### FR-052 — Commit Authoring

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 6 |
| **Description** | The system shall commit generated documentation changes with a structured commit message: `docs: <summary>` (Conventional Commits format). The commit author shall be `DocSync Bot <bot@docsync.dev>`. |
| **Acceptance Criteria** | 1. Commit messages follow Conventional Commits. 2. The author is the DocSync bot user. 3. Each commit contains only documentation file changes. |

---

#### FR-053 — Pull Request Creation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 6 |
| **Description** | The system shall open a pull request from the feature branch to the default branch. The PR body shall include: summary of changes, list of affected docs, originating commit SHAs, and a link to the impact report. |
| **Acceptance Criteria** | 1. The PR is created successfully via GitHub API. 2. The PR body contains all required sections. 3. The PR is assigned the `documentation` label. |

---

#### FR-054 — PR Status Tracking

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 6 |
| **Description** | The system shall track the lifecycle of created PRs (`OPEN`, `MERGED`, `CLOSED`, `STALE`). Status is updated via PR webhooks. |
| **Acceptance Criteria** | 1. Merging the PR updates status to `MERGED`. 2. Closing without merge updates status to `CLOSED`. 3. A PR open for more than 14 days is marked `STALE`. |

---

#### FR-055 — Conflict Detection and Resolution

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 6 |
| **Description** | If a DocSync PR has merge conflicts (due to concurrent doc edits), the system shall detect the conflict and attempt to rebase. If rebase fails, the PR body is updated with a conflict notice and the PR is labeled `conflict`. |
| **Acceptance Criteria** | 1. A conflicting PR is rebased automatically if possible. 2. If rebase fails, the `conflict` label is added. 3. The PR body includes instructions for manual resolution. |

---

#### FR-056 — PR Deduplication

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 6 |
| **Description** | The system shall not create a new PR if an existing open PR already covers the same documentation files. Instead, the existing PR branch shall be updated with the new changes. |
| **Acceptance Criteria** | 1. Two consecutive pushes affecting the same doc result in one PR (updated), not two. 2. The PR description is updated to reference both originating commits. |

---

#### FR-057 — PR Review Comment Posting

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 6 |
| **Description** | The system shall post inline review comments on its own PRs explaining *why* each change was made (e.g., "Method `getUser` was renamed to `findUser` in commit `abc1234`"). |
| **Acceptance Criteria** | 1. Each changed hunk in the PR has an associated review comment. 2. Comments reference the specific code change. |

---

#### FR-058 — PR Metrics Collection

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 8 |
| **Description** | The system shall collect metrics for created PRs: time-to-merge, reviewer edit ratio (lines changed by reviewer / total lines), and acceptance rate. Metrics are exposed via the dashboard. |
| **Acceptance Criteria** | 1. Time-to-merge is calculated as the duration between PR creation and merge. 2. Edit ratio is calculated correctly. 3. Metrics are aggregated per repository and globally. |

---

### 1.7 Configuration & Admin (FR-059 – FR-065)

---

#### FR-059 — `docsync.yaml` Project Configuration

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | The system shall read a `docsync.yaml` file from the repository root to configure: monitored directories, file patterns, documentation directories, OpenAPI spec paths, style guide path, and feature toggles. |
| **Acceptance Criteria** | 1. A valid `docsync.yaml` is parsed without errors. 2. Missing `docsync.yaml` results in sensible defaults. 3. An invalid YAML file produces a clear error in the dashboard. |

---

#### FR-060 — Configuration Validation

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | The system shall validate `docsync.yaml` against a JSON Schema. Invalid configurations shall be reported to the user via the dashboard and shall not replace the previous valid configuration. |
| **Acceptance Criteria** | 1. An unknown key in `docsync.yaml` produces a warning. 2. A missing required key produces an error. 3. The previous valid config remains active until a valid replacement is committed. |

---

#### FR-061 — Web UI Settings Page

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 8 |
| **Description** | The web UI shall provide a settings page where users can configure DocSync behavior for each repository (equivalent to `docsync.yaml` but via GUI). Changes made in the UI shall be committed to the repository as a `docsync.yaml` update. |
| **Acceptance Criteria** | 1. All `docsync.yaml` settings are editable in the UI. 2. Saving settings creates a commit on the default branch. 3. The UI shows the current effective configuration. |

---

#### FR-062 — Audit Log

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | The system shall maintain an audit log of significant actions: repository onboarded, configuration changed, PR created, validation run completed, user login. Entries include timestamp, actor, action, and details. |
| **Acceptance Criteria** | 1. All listed action types produce audit log entries. 2. Entries are queryable by repository and time range. 3. Entries are immutable (append-only). |

---

#### FR-063 — User Authentication via GitHub OAuth

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | The system shall authenticate users via GitHub OAuth 2.0. Upon successful authentication, a session is established and the user's GitHub identity (username, avatar, email) is stored. |
| **Acceptance Criteria** | 1. Clicking "Sign in with GitHub" redirects to GitHub OAuth flow. 2. After authorization, the user is redirected back with a valid session. 3. Unauthenticated API requests return `401`. |

---

#### FR-064 — Tenant Isolation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | All data (repositories, events, reports, configurations) shall be isolated per tenant (GitHub App installation). A user shall only see repositories belonging to installations they have access to. |
| **Acceptance Criteria** | 1. User A cannot see User B's repositories. 2. API queries are automatically scoped to the user's installations. 3. Direct ID-based access to another tenant's data returns `403`. |

---

#### FR-065 — API Rate Limit Respect for GitHub

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | The system shall monitor GitHub API rate-limit headers (`X-RateLimit-Remaining`, `X-RateLimit-Reset`) and throttle outgoing requests to avoid hitting limits. When limits are approached (< 10 % remaining), non-critical requests shall be deferred. |
| **Acceptance Criteria** | 1. Rate-limit headers are read after every GitHub API call. 2. When remaining < 10 %, only critical requests (webhook responses) proceed. 3. Deferred requests resume after the reset timestamp. |

---

## 2. Non-Functional Requirements

### 2.1 Performance (NFR-001 – NFR-005)

---

#### NFR-001 — Webhook Acknowledgment Latency

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | The webhook endpoint shall acknowledge incoming events (return HTTP 200) within 5 seconds under normal load. |
| **Acceptance Criteria** | P99 response time ≤ 5 s measured over a rolling 24-hour window. |

---

#### NFR-002 — Repository Indexing Throughput

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 4 |
| **Description** | Full indexing of a repository with up to 100,000 lines of Java code shall complete within 60 seconds. |
| **Acceptance Criteria** | A benchmark repository of 100 k LOC completes full indexing in ≤ 60 s on a standard 2-vCPU instance. |

---

#### NFR-003 — Incremental Indexing Speed

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 4 |
| **Description** | Incremental indexing (≤ 20 changed files) shall complete within 10 seconds. |
| **Acceptance Criteria** | A push modifying 20 files in a 100 k LOC repo completes incremental indexing in ≤ 10 s. |

---

#### NFR-004 — Dashboard Page Load Time

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | All dashboard pages shall load (first contentful paint) within 2 seconds on a broadband connection. |
| **Acceptance Criteria** | Lighthouse FCP ≤ 2 s for the repository list, detail, and report pages. |

---

#### NFR-005 — LLM Response Timeout

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | LLM generation requests shall have a configurable timeout (default: 120 seconds). If the timeout is exceeded, the request is retried once before marking the generation as `FAILED`. |
| **Acceptance Criteria** | A simulated slow LLM response exceeding 120 s triggers a retry. After two failures, the job is marked `FAILED`. |

---

### 2.2 Security (NFR-006 – NFR-011)

---

#### NFR-006 — Webhook Signature Verification

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | All incoming webhooks shall be verified via HMAC-SHA256 using the configured webhook secret. Invalid signatures shall be rejected. |
| **Acceptance Criteria** | 100 % of processed webhooks have verified signatures. Unverified requests are logged and rejected. |

---

#### NFR-007 — Secrets Encryption at Rest

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | All secrets (GitHub App private key, webhook secret, API keys) shall be encrypted at rest using AES-256. Secrets shall never appear in logs. |
| **Acceptance Criteria** | 1. Database columns storing secrets are encrypted. 2. A full-text search of application logs for known secret values yields zero results. |

---

#### NFR-008 — HTTPS Only

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | All external communication (webhooks, API, dashboard) shall be served over HTTPS with TLS 1.2+. HTTP requests shall be redirected to HTTPS. |
| **Acceptance Criteria** | An HTTP request to any endpoint receives a `301` redirect to HTTPS. TLS 1.0 and 1.1 are disabled. |

---

#### NFR-009 — OAuth Scope Minimization

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | The GitHub OAuth integration shall request only the minimum required scopes: `read:user`, `user:email`. The GitHub App shall request only necessary permissions: repository contents (read/write), pull requests (read/write), checks (write), metadata (read). |
| **Acceptance Criteria** | The OAuth authorization URL includes only the specified scopes. The App manifest lists only the specified permissions. |

---

#### NFR-010 — Input Validation

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | All user and webhook input shall be validated and sanitized. SQL injection, XSS, and path-traversal attacks shall be prevented. |
| **Acceptance Criteria** | 1. Parameterized queries are used for all database access (no string concatenation). 2. HTML output is escaped. 3. File paths are canonicalized and restricted to allowed directories. |

---

#### NFR-011 — Dependency Vulnerability Scanning

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | The CI pipeline shall include dependency vulnerability scanning (e.g., OWASP Dependency-Check, GitHub Dependabot). Critical vulnerabilities shall block the build. |
| **Acceptance Criteria** | 1. A dependency with a known CVE (CVSS ≥ 9.0) fails the build. 2. Scanning runs on every PR. |

---

### 2.3 Reliability (NFR-012 – NFR-016)

---

#### NFR-012 — Uptime SLA

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 8 |
| **Description** | The system shall maintain 99.5 % availability measured monthly (excluding planned maintenance windows). |
| **Acceptance Criteria** | Monthly uptime ≥ 99.5 % as measured by an external uptime monitor hitting the `/health` endpoint. |

---

#### NFR-013 — At-Least-Once Event Processing

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | Every webhook event that receives a `200 OK` acknowledgment shall be processed at least once. Event loss is unacceptable. |
| **Acceptance Criteria** | Under simulated worker crashes, all acknowledged events are eventually processed after restart. |

---

#### NFR-014 — Graceful Degradation on LLM Failure

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 5 |
| **Description** | If the LLM provider is unavailable, the system shall continue to function: indexing, analysis, validation, and reporting shall work normally. Only generation jobs are paused. |
| **Acceptance Criteria** | 1. With the LLM provider down, push events are still processed and impact reports generated. 2. Generation jobs are queued and retried when the provider recovers. |

---

#### NFR-015 — Database Backup and Recovery

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 8 |
| **Description** | PostgreSQL databases shall be backed up daily with point-in-time recovery (PITR) capability. Recovery time objective (RTO): 1 hour. Recovery point objective (RPO): 1 hour. |
| **Acceptance Criteria** | A simulated database failure is recovered from backup within 1 hour with ≤ 1 hour of data loss. |

---

#### NFR-016 — Health Check Endpoints

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | The system shall expose `/health` (liveness) and `/health/ready` (readiness) endpoints. The readiness endpoint shall verify database connectivity and critical service availability. |
| **Acceptance Criteria** | 1. `/health` returns `200 OK` when the process is running. 2. `/health/ready` returns `503` when the database is unreachable. |

---

### 2.4 Scalability (NFR-017 – NFR-020)

---

#### NFR-017 — Horizontal Scalability

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | The application tier shall be stateless and horizontally scalable. Running 2+ instances behind a load balancer shall not cause data inconsistencies or duplicate processing. |
| **Acceptance Criteria** | 1. Two instances process a burst of 50 concurrent webhooks without duplicates (using database-based deduplication). 2. No instance-local state is required. |

---

#### NFR-018 — Repository Scale

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | The system shall support at least 500 active repositories per tenant without performance degradation beyond 20 % of baseline. |
| **Acceptance Criteria** | With 500 repositories, dashboard load time is ≤ 2.4 s (120 % of baseline). |

---

#### NFR-019 — Concurrent Event Handling

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 3 |
| **Description** | The system shall handle at least 50 concurrent webhook events without event loss or processing errors. |
| **Acceptance Criteria** | A load test sending 50 concurrent webhook events results in 50 successfully processed events. |

---

#### NFR-020 — Database Connection Pooling

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | The application shall use connection pooling (e.g., HikariCP) with configurable pool size. Default: 10 connections, max: 50. |
| **Acceptance Criteria** | 1. Under load, the number of active database connections does not exceed the configured maximum. 2. Connection pool metrics are exposed via Prometheus. |

---

### 2.5 Usability (NFR-021 – NFR-022)

---

#### NFR-021 — Onboarding Simplicity

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 2 |
| **Description** | A new user shall be able to install the GitHub App, onboard a repository, and see an initial health score in ≤ 5 minutes and ≤ 3 clicks (after GitHub App installation). |
| **Acceptance Criteria** | A usability test with 5 developers completes onboarding in ≤ 5 minutes with ≤ 3 clicks on average. |

---

#### NFR-022 — Error Messaging

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 1 |
| **Description** | All user-facing error messages shall be actionable: they must describe what went wrong and what the user can do about it. No raw stack traces in the UI. |
| **Acceptance Criteria** | Every error response includes a human-readable `message` and, where applicable, a `suggestion` field. |

---

### 2.6 Observability (NFR-023 – NFR-025)

---

#### NFR-023 — Structured Logging

| Field | Value |
|---|---|
| **Priority** | P0 |
| **MVP Phase** | 1 |
| **Description** | All application logs shall be in structured JSON format with fields: `timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, and contextual fields (e.g., `repositoryId`, `eventId`). |
| **Acceptance Criteria** | Every log line is valid JSON and contains the required fields. |

---

#### NFR-024 — Prometheus Metrics

| Field | Value |
|---|---|
| **Priority** | P1 |
| **MVP Phase** | 8 |
| **Description** | The system shall expose Prometheus-compatible metrics at `/metrics`: JVM stats, HTTP request latency histograms, event processing counts and durations, LLM call latency and token usage, and database connection pool stats. |
| **Acceptance Criteria** | Prometheus can scrape `/metrics` and all listed metric families are present. |

---

#### NFR-025 — Distributed Tracing

| Field | Value |
|---|---|
| **Priority** | P2 |
| **MVP Phase** | 8 |
| **Description** | The system shall propagate OpenTelemetry trace context across all internal operations and outgoing HTTP calls. Traces shall be exportable to a configured backend (e.g., Jaeger, Zipkin). |
| **Acceptance Criteria** | An end-to-end trace from webhook receipt to PR creation is visible in the tracing backend, with spans for each major step. |

---

## 3. Requirements Traceability Matrix

| Capability | Requirements | MVP Phase(s) |
|---|---|---|
| Application Foundation | FR-063, FR-064, NFR-007–NFR-010, NFR-016, NFR-020, NFR-022, NFR-023 | 1 |
| Repository Onboarding | FR-001–FR-010, FR-065 | 2 |
| Webhook & Events | FR-011–FR-020, NFR-001, NFR-006, NFR-013, NFR-019 | 3 |
| Analysis & Quality | FR-021–FR-030, FR-041–FR-042, FR-059–FR-060, NFR-002, NFR-003 | 4 |
| Documentation Generation | FR-031–FR-040, NFR-005, NFR-014 | 5 |
| Pull Request Integration | FR-051–FR-057 | 6 |
| Example Validation & Reporting | FR-043–FR-049 | 7 |
| Operational Readiness | FR-008, FR-020, FR-040, FR-048, FR-050, FR-058, FR-061, FR-062, NFR-004, NFR-011, NFR-012, NFR-015, NFR-017, NFR-018, NFR-024, NFR-025 | 8 |
