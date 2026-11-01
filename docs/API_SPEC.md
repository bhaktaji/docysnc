# DocSync API Specification

> Version: 1.0.0 | Base URL: `/api/v1` | Protocol: HTTPS

## Table of Contents

- [Conventions](#conventions)
- [Authentication](#authentication-mechanisms)
- [Common Patterns](#common-patterns)
- [Endpoints](#endpoints)
  - [Auth](#auth)
  - [Users](#users)
  - [Organizations](#organizations)
  - [Members](#members)
  - [Connections](#connections)
  - [Repositories](#repositories)
  - [Analysis](#analysis)
  - [Documentation](#documentation)
  - [Findings](#findings)
  - [Pull Requests](#pull-requests)
  - [Jobs](#jobs)
  - [Webhooks](#webhooks)
  - [Health](#health)
  - [Usage](#usage)
- [Rate Limiting](#rate-limiting)
- [Webhook Payloads](#webhook-payloads)

---

## Conventions

| Convention         | Value                                      |
| ------------------ | ------------------------------------------ |
| Base URL           | `/api/v1`                                  |
| Content-Type       | `application/json`                         |
| Character Encoding | UTF-8                                      |
| Date Format        | ISO 8601 (`2024-01-15T10:30:00Z`)          |
| ID Format          | UUID v4 (`550e8400-e29b-41d4-a716-446655440000`) |
| Null Handling       | Omitted from responses; explicit `null` in PATCH to clear a field |

All endpoints are prefixed with `/api/v1` unless otherwise noted (e.g., webhooks, actuator).

---

## Authentication Mechanisms

DocSync supports two authentication methods. Every request to a protected endpoint **must** include exactly one.

### 1. Bearer JWT Token

Used by interactive clients (web UI, CLI).

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

- Tokens are obtained via `POST /api/v1/auth/login`.
- Access tokens expire after **15 minutes**.
- Refresh tokens expire after **7 days** and are rotated on each use.
- The JWT payload includes `sub` (userId), `orgId` (current organization), and `roles`.

### 2. API Key

Used by CI/CD integrations and server-to-server calls.

```
X-API-Key: ds_live_a1b2c3d4e5f6...
```

- API keys are scoped to an organization and carry a fixed role.
- Keys are prefixed with `ds_live_` (production) or `ds_test_` (sandbox).
- Keys can be revoked without affecting other keys.

---

## Common Patterns

### Pagination

All list endpoints support cursor-free page-based pagination.

**Request Parameters:**

| Parameter | Type    | Default          | Description                          |
| --------- | ------- | ---------------- | ------------------------------------ |
| `page`    | integer | `0`              | Zero-based page index                |
| `size`    | integer | `20`             | Items per page (max 100)             |
| `sort`    | string  | `createdAt,desc` | Field and direction (asc/desc)       |

**Response Envelope:**

```json
{
  "content": [ ... ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

### Error Format

All error responses follow a consistent structure:

```json
{
  "error": {
    "code": "ERR_VALIDATION",
    "message": "Request validation failed",
    "details": [
      {
        "field": "name",
        "reason": "must not be blank"
      }
    ],
    "traceId": "abc123-def456-ghi789"
  }
}
```

### HTTP Status Codes

| Code | Meaning                | Usage                                              |
| ---- | ---------------------- | -------------------------------------------------- |
| 200  | OK                     | Successful GET, PATCH                              |
| 201  | Created                | Successful POST that creates a resource             |
| 204  | No Content             | Successful DELETE, or action with no response body  |
| 400  | Bad Request            | Malformed JSON, missing required fields             |
| 401  | Unauthorized           | Missing or expired authentication                   |
| 403  | Forbidden              | Valid auth but insufficient permissions              |
| 404  | Not Found              | Resource does not exist or is outside tenant scope   |
| 409  | Conflict               | Duplicate resource or concurrent modification        |
| 422  | Unprocessable Entity   | Valid JSON but semantically invalid (e.g., invalid enum value) |
| 429  | Too Many Requests      | Rate limit exceeded                                 |
| 500  | Internal Server Error  | Unexpected server failure                           |
| 503  | Service Unavailable    | Maintenance mode or dependency down                  |

### Common Error Codes

| Code                     | HTTP Status | Description                              |
| ------------------------ | ----------- | ---------------------------------------- |
| `ERR_VALIDATION`         | 400         | Request body validation failed           |
| `ERR_AUTH_INVALID`       | 401         | Invalid credentials                      |
| `ERR_TOKEN_EXPIRED`      | 401         | JWT access token has expired             |
| `ERR_FORBIDDEN`          | 403         | Insufficient role/permissions            |
| `ERR_NOT_FOUND`          | 404         | Resource not found                       |
| `ERR_CONFLICT`           | 409         | Resource already exists                  |
| `ERR_RATE_LIMITED`       | 429         | Too many requests                        |
| `ERR_GITHUB_RATE_LIMITED`| 503         | GitHub quota nearly exhausted; retry after `Retry-After` |
| `ERR_ANALYSIS_RUNNING`   | 409         | An analysis is already in progress       |
| `ERR_REPO_UNREACHABLE`   | 422         | Cannot connect to the repository         |
| `ERR_WEBHOOK_SIGNATURE`  | 401         | Missing or invalid GitHub webhook signature |
| `ERR_WEBHOOK_INVALID`    | 400         | Signed webhook is missing required headers or JSON |
| `ERR_QUOTA_EXCEEDED`     | 422         | Organization usage quota exceeded        |
| `ERR_INTERNAL`           | 500         | Unhandled server error                   |

---

## Endpoints

### Auth

#### `POST /api/v1/auth/login`

Authenticate a user and receive JWT tokens.

**Request:**

```json
{
  "email": "dev@example.com",
  "password": "s3cur3P@ssw0rd"
}
```

**Response `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJl...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "dev@example.com",
    "displayName": "Jane Developer"
  }
}
```

**Error `401`:**

```json
{
  "error": {
    "code": "ERR_AUTH_INVALID",
    "message": "Invalid email or password",
    "details": [],
    "traceId": "tr-abc123"
  }
}
```

---

#### `POST /api/v1/auth/register`

Create a local account and receive an access token. Implemented as an extension to the
original spec; passwords must be 12–128 characters.

**Request:**

```json
{
  "email": "dev@example.com",
  "password": "a-strong-password-123",
  "displayName": "Jane Developer"
}
```

**Response `201 Created`:** same shape as `POST /api/v1/auth/login`.

---

#### `POST /api/v1/auth/github`

Complete the GitHub OAuth flow. The client exchanges the browser authorization code with
GitHub using `GITHUB_OAUTH_CLIENT_ID` / `GITHUB_OAUTH_CLIENT_SECRET` (scopes `read:user`,
`user:email`), links or creates the user, and returns an access token.

**Request:**

```json
{
  "code": "github-oauth-authorization-code"
}
```

**Response `200 OK`:** same shape as `POST /api/v1/auth/login`.

---

#### `POST /api/v1/auth/token/refresh`

> **Status:** not yet implemented (planned). Access tokens currently expire after 15
> minutes and must be re-obtained through login.

Exchange a valid refresh token for a new access/refresh token pair. The old refresh token is invalidated (rotation).

**Request:**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJl..."
}
```

**Response `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9r...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

---

#### `DELETE /api/v1/auth/logout`

Invalidate the current refresh token and revoke the session.

**Headers:** `Authorization: Bearer <accessToken>`

**Response `204 No Content`:** _(empty body)_

---

### Users

#### `GET /api/v1/users/me`

Retrieve the authenticated user's profile.

**Response `200 OK`:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "dev@example.com",
  "displayName": "Jane Developer",
  "avatarUrl": "https://avatars.example.com/jane.png",
  "createdAt": "2024-01-10T08:00:00Z",
  "updatedAt": "2024-06-15T12:30:00Z",
  "organizations": [
    {
      "id": "org-uuid-001",
      "name": "Acme Corp",
      "role": "ADMIN"
    }
  ]
}
```

---

#### `PATCH /api/v1/users/me`

Update the authenticated user's profile. Only provided fields are modified.

**Request:**

```json
{
  "displayName": "Jane Dev",
  "avatarUrl": "https://avatars.example.com/jane-v2.png"
}
```

**Response `200 OK`:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "dev@example.com",
  "displayName": "Jane Dev",
  "avatarUrl": "https://avatars.example.com/jane-v2.png",
  "createdAt": "2024-01-10T08:00:00Z",
  "updatedAt": "2024-06-15T14:00:00Z"
}
```

---

### Organizations

#### `GET /api/v1/organizations`

List organizations the authenticated user belongs to.

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "org-uuid-001",
      "name": "Acme Corp",
      "slug": "acme-corp",
      "plan": "TEAM",
      "createdAt": "2024-01-05T10:00:00Z",
      "memberCount": 12
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

#### `POST /api/v1/organizations`

Create a new organization. The authenticated user becomes the `OWNER`.

**Request:**

```json
{
  "name": "Acme Corp",
  "slug": "acme-corp"
}
```

**Response `201 Created`:**

```json
{
  "id": "org-uuid-001",
  "name": "Acme Corp",
  "slug": "acme-corp",
  "plan": "FREE",
  "createdAt": "2024-01-05T10:00:00Z",
  "memberCount": 1
}
```

**Error `409`:** Slug already taken.

---

#### `GET /api/v1/organizations/{id}`

Retrieve a specific organization by ID. The user must be a member.

**Response `200 OK`:**

```json
{
  "id": "org-uuid-001",
  "name": "Acme Corp",
  "slug": "acme-corp",
  "plan": "TEAM",
  "billingEmail": "billing@acme.com",
  "createdAt": "2024-01-05T10:00:00Z",
  "updatedAt": "2024-03-20T09:15:00Z",
  "memberCount": 12,
  "repositoryCount": 5
}
```

---

#### `PATCH /api/v1/organizations/{id}`

Update organization details. Requires `ADMIN` or `OWNER` role.

**Request:**

```json
{
  "name": "Acme Corporation",
  "billingEmail": "finance@acme.com"
}
```

**Response `200 OK`:** _(Updated organization object)_

---

### Members

#### `GET /api/v1/organizations/{id}/members`

List members of an organization. Supports pagination.

**Query Parameters:** `page`, `size`, `sort`, `role` (optional filter: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER`)

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "member-uuid-001",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "email": "dev@example.com",
      "displayName": "Jane Developer",
      "role": "ADMIN",
      "joinedAt": "2024-01-05T10:00:00Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 12,
    "totalPages": 1
  }
}
```

---

#### `POST /api/v1/organizations/{id}/members`

Invite a new member to the organization. Requires `ADMIN` or `OWNER` role.

**Request:**

```json
{
  "email": "newdev@example.com",
  "role": "MEMBER"
}
```

**Response `201 Created`:**

```json
{
  "id": "member-uuid-002",
  "email": "newdev@example.com",
  "role": "MEMBER",
  "status": "PENDING",
  "invitedAt": "2024-06-20T14:00:00Z"
}
```

---

#### `PATCH /api/v1/organizations/{id}/members/{memberId}`

Update a member's role. Requires `ADMIN` or `OWNER` role. Cannot demote the last `OWNER`.

**Request:**

```json
{
  "role": "ADMIN"
}
```

**Response `200 OK`:**

```json
{
  "id": "member-uuid-002",
  "userId": "user-uuid-002",
  "email": "newdev@example.com",
  "displayName": "New Developer",
  "role": "ADMIN",
  "joinedAt": "2024-06-20T14:00:00Z"
}
```

---

#### `DELETE /api/v1/organizations/{id}/members/{memberId}`

Remove a member from the organization. Requires `ADMIN` or `OWNER` role. Cannot remove the last `OWNER`.

**Response `204 No Content`:** _(empty body)_

---

### Connections

A GitHub App installation is linked to at most one organization. Installation tokens are
never returned by the API and are not persisted; they are cached in memory until shortly
before expiry.

#### `GET /api/v1/organizations/{organizationId}/connections`

List installations linked to the organization. Any active member may read.

**Response `200 OK`:**

```json
[
  {
    "id": "connection-uuid-001",
    "organizationId": "org-uuid-001",
    "provider": "GITHUB",
    "installationId": 12345678,
    "accountLogin": "acme-corp",
    "accountType": "Organization",
    "status": "ACTIVE",
    "createdAt": "2024-02-01T08:55:00Z",
    "updatedAt": "2024-02-01T08:55:00Z"
  }
]
```

**Connection statuses:** `PENDING` (installation not yet linked), `ACTIVE`, `SUSPENDED`,
`REVOKED`, `ERROR`.

---

#### `GET /api/v1/connections/claimable`

List `PENDING` installations created by the authenticated user's GitHub account. Returns
`[]` when the user has no linked GitHub identity.

---

#### `POST /api/v1/organizations/{organizationId}/connections/{connectionId}/claim`

Link a pending installation to an organization. Requires `ADMIN` or `OWNER`, and the
authenticated user must be the GitHub account that installed the App. All repositories
currently granted to the installation are imported with status `PENDING`.

**Response `200 OK`:** _(connection object with `status: "ACTIVE"`)_

**Errors:** `403` when the caller is not the installer or lacks the role; `404` when the
connection belongs to another tenant; `422` when the connection is not `PENDING`.

---

#### `DELETE /api/v1/organizations/{organizationId}/connections/{connectionId}`

Revoke the local link and archive all repositories imported from that installation.
Requires `ADMIN` or `OWNER`. The GitHub App is **not** uninstalled from GitHub.

**Response `200 OK`:** _(connection object with `status: "REVOKED"`)_

---

### Repositories

All repository endpoints are organization-scoped and require active organization
membership. Connecting and disconnecting require `ADMIN` or `OWNER`; configuration and
synchronization require `MEMBER` or higher.

**Statuses:** `PENDING` → `CLONING` → `READY`, plus `ERROR` and `ARCHIVED`. `INDEXING` is
reserved for the analysis pipeline delivered in a later milestone.

#### `GET /api/v1/organizations/{organizationId}/repositories`

List repositories in the organization, most recently synchronized first.

**Query Parameters:** `status` (optional: `PENDING`, `CLONING`, `INDEXING`, `READY`,
`ERROR`, `ARCHIVED`)

**Response `200 OK`:**

```json
[
  {
    "id": "repo-uuid-001",
    "organizationId": "org-uuid-001",
    "connectionId": "connection-uuid-001",
    "provider": "GITHUB",
    "installationId": 12345678,
    "externalId": 123456789,
    "name": "backend-api",
    "fullName": "acme-corp/backend-api",
    "cloneUrl": "https://github.com/acme-corp/backend-api.git",
    "defaultBranch": "main",
    "status": "READY",
    "configuration": {
      "autoAnalyze": true,
      "analyzePullRequests": true,
      "languages": ["java", "kotlin"],
      "excludePaths": ["**/test/**"]
    },
    "errorMessage": null,
    "lastSyncedAt": "2024-06-19T23:00:00Z",
    "lastCommitSha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
    "lastAnalysisAt": null,
    "createdAt": "2024-02-01T09:00:00Z",
    "updatedAt": "2024-06-19T23:00:00Z"
  }
]
```

---

#### `POST /api/v1/organizations/{organizationId}/repositories`

Connect a repository from an active installation. Metadata is read from GitHub using a
short-lived installation token, so the repository must be granted to the installation.

**Request:**

```json
{
  "installationId": 12345678,
  "fullName": "acme-corp/backend-api",
  "defaultBranch": "main",
  "configuration": {
    "autoAnalyze": true,
    "analyzePullRequests": true,
    "languages": ["java", "kotlin"],
    "excludePaths": ["**/test/**", "**/generated/**"]
  }
}
```

`defaultBranch` is optional and overrides GitHub's default branch. `configuration` is
optional; omitted fields use the defaults (`autoAnalyze: true`, `analyzePullRequests: true`,
empty `languages`/`excludePaths`).

**Response `201 Created`:** _(repository object with `status: "PENDING"`)_

**Errors:** `404` when the installation is not active for this organization; `409` when
the repository is already connected; `422` when the installation ID is unknown; `502` when
GitHub metadata cannot be read.

---

#### `GET /api/v1/organizations/{organizationId}/repositories/{repositoryId}`

Retrieve a specific repository. Repositories outside the organization return `404`.

**Response `200 OK`:** _(Full repository object including configuration and sync state)_

---

#### `POST /api/v1/organizations/{organizationId}/repositories/{repositoryId}/sync`

Refresh repository metadata from GitHub, then clone (first sync) or fetch (later syncs)
the configured branch into the confined workspace using an installation token.

Requires `MEMBER` or higher. Archive repositories cannot be synchronized (`422`).
Git or GitHub failures are recorded on the repository (`status: "ERROR"`,
`errorMessage`), and the endpoint returns `200` with the updated repository so clients can
inspect the failure. A subsequent sync resets the status and retries.

**Response `200 OK`:** _(repository object with `READY` or `ERROR`)_

---

#### `DELETE /api/v1/organizations/{organizationId}/repositories/{repositoryId}`

Disconnect a repository from DocSync. Requires `ADMIN` or `OWNER`. This does **not**
delete the repository on GitHub. The repository is marked `ARCHIVED`, stops receiving
processing, and its local workspace is removed best-effort. Analysis/documentation data
retention is handled by the later analysis milestones.

**Response `204 No Content`:** _(empty body)_

---

#### `PATCH /api/v1/organizations/{organizationId}/repositories/{repositoryId}/configuration`

Update repository analysis configuration. Omitted fields are preserved.

**Request:**

```json
{
  "autoAnalyze": false,
  "excludePaths": ["**/test/**", "**/generated/**", "**/vendor/**"]
}
```

**Response `200 OK`:**

```json
{
  "autoAnalyze": false,
  "analyzePullRequests": true,
  "languages": ["java", "kotlin"],
  "excludePaths": ["**/test/**", "**/generated/**", "**/vendor/**"]
}
```

---

### Analysis

#### `POST /api/v1/repositories/{id}/analysis`

Trigger a new analysis for a repository. Returns `409` if an analysis is already running.

**Request:**

```json
{
  "branch": "main",
  "commitSha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "scope": "FULL"
}
```

> `scope` can be `FULL` (entire codebase) or `INCREMENTAL` (changed files only).

**Response `201 Created`:**

```json
{
  "id": "analysis-uuid-001",
  "repositoryId": "repo-uuid-001",
  "branch": "main",
  "commitSha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "scope": "FULL",
  "status": "QUEUED",
  "createdAt": "2024-06-20T10:00:00Z",
  "jobId": "job-uuid-001"
}
```

---

#### `GET /api/v1/repositories/{id}/analysis`

List analysis runs for a repository. Supports pagination.

**Query Parameters:** `page`, `size`, `sort`, `status` (optional: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`)

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "analysis-uuid-001",
      "branch": "main",
      "commitSha": "a1b2c3d4...",
      "scope": "FULL",
      "status": "COMPLETED",
      "stats": {
        "filesAnalyzed": 128,
        "entitiesFound": 342,
        "findingsCount": 15,
        "pagesGenerated": 48,
        "durationMs": 45200
      },
      "createdAt": "2024-06-20T10:00:00Z",
      "completedAt": "2024-06-20T10:00:45Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 23,
    "totalPages": 2
  }
}
```

---

#### `GET /api/v1/repositories/{id}/analysis/{analysisId}`

Retrieve detailed results of a specific analysis run.

**Response `200 OK`:**

```json
{
  "id": "analysis-uuid-001",
  "repositoryId": "repo-uuid-001",
  "branch": "main",
  "commitSha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "scope": "FULL",
  "status": "COMPLETED",
  "stats": {
    "filesAnalyzed": 128,
    "entitiesFound": 342,
    "findingsCount": 15,
    "pagesGenerated": 48,
    "durationMs": 45200
  },
  "createdAt": "2024-06-20T10:00:00Z",
  "completedAt": "2024-06-20T10:00:45Z",
  "jobId": "job-uuid-001",
  "error": null
}
```

---

### Documentation

#### `GET /api/v1/repositories/{id}/documentation`

List generated documentation pages for a repository.

**Query Parameters:** `page`, `size`, `sort`, `type` (optional: `API_REFERENCE`, `GUIDE`, `ARCHITECTURE`, `DATA_MODEL`), `search` (optional: full-text search)

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "doc-uuid-001",
      "repositoryId": "repo-uuid-001",
      "title": "UserService API Reference",
      "type": "API_REFERENCE",
      "path": "services/user-service",
      "generatedFrom": "analysis-uuid-001",
      "createdAt": "2024-06-20T10:00:45Z",
      "updatedAt": "2024-06-20T10:00:45Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 48,
    "totalPages": 3
  }
}
```

---

#### `GET /api/v1/repositories/{id}/documentation/{pageId}`

Retrieve a single documentation page with full rendered content.

**Response `200 OK`:**

```json
{
  "id": "doc-uuid-001",
  "repositoryId": "repo-uuid-001",
  "title": "UserService API Reference",
  "type": "API_REFERENCE",
  "path": "services/user-service",
  "content": "# UserService\n\n## Overview\n\nThe `UserService` handles user lifecycle operations...\n\n## Methods\n\n### `createUser(CreateUserRequest)`\n\n...",
  "format": "MARKDOWN",
  "sourceEntities": [
    {
      "entityId": "entity-uuid-001",
      "type": "CLASS",
      "qualifiedName": "com.acme.service.UserService"
    }
  ],
  "generatedFrom": "analysis-uuid-001",
  "provenance": {
    "method": "DETERMINISTIC",
    "confidence": 1.0
  },
  "createdAt": "2024-06-20T10:00:45Z",
  "updatedAt": "2024-06-20T10:00:45Z"
}
```

---

### Findings

#### `GET /api/v1/repositories/{id}/findings`

List documentation findings (issues, gaps, suggestions) for a repository.

**Query Parameters:** `page`, `size`, `sort`, `severity` (optional: `CRITICAL`, `WARNING`, `INFO`), `category` (optional: `MISSING_DOCS`, `OUTDATED`, `INCONSISTENT`, `INCOMPLETE`), `status` (optional: `OPEN`, `RESOLVED`, `IGNORED`)

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "finding-uuid-001",
      "repositoryId": "repo-uuid-001",
      "severity": "WARNING",
      "category": "MISSING_DOCS",
      "title": "Public method lacks documentation",
      "description": "The method `processPayment()` in `PaymentService` is public but has no Javadoc.",
      "entityReference": {
        "entityId": "entity-uuid-010",
        "type": "METHOD",
        "qualifiedName": "com.acme.service.PaymentService.processPayment",
        "filePath": "src/main/java/com/acme/service/PaymentService.java",
        "lineNumber": 45
      },
      "status": "OPEN",
      "analysisId": "analysis-uuid-001",
      "createdAt": "2024-06-20T10:00:45Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 15,
    "totalPages": 1
  }
}
```

---

#### `GET /api/v1/repositories/{id}/findings/summary`

Get an aggregated summary of findings for a repository.

**Response `200 OK`:**

```json
{
  "repositoryId": "repo-uuid-001",
  "totalFindings": 15,
  "bySeverity": {
    "CRITICAL": 2,
    "WARNING": 8,
    "INFO": 5
  },
  "byCategory": {
    "MISSING_DOCS": 6,
    "OUTDATED": 4,
    "INCONSISTENT": 3,
    "INCOMPLETE": 2
  },
  "byStatus": {
    "OPEN": 12,
    "RESOLVED": 2,
    "IGNORED": 1
  },
  "trend": {
    "previousAnalysis": 18,
    "change": -3,
    "changePercent": -16.7
  },
  "lastAnalysisAt": "2024-06-20T10:00:45Z"
}
```

---

### Pull Requests

#### `GET /api/v1/repositories/{id}/pull-requests`

List pull requests that DocSync has analyzed for a repository.

**Query Parameters:** `page`, `size`, `sort`, `status` (optional: `OPEN`, `MERGED`, `CLOSED`), `analyzed` (optional boolean)

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "pr-uuid-001",
      "repositoryId": "repo-uuid-001",
      "prNumber": 142,
      "title": "Add payment processing module",
      "author": "jdeveloper",
      "status": "OPEN",
      "headBranch": "feature/payments",
      "baseBranch": "main",
      "analysisId": "analysis-uuid-005",
      "findingsCount": 3,
      "documentationImpact": {
        "pagesAffected": 4,
        "newPages": 2,
        "modifiedPages": 2
      },
      "createdAt": "2024-06-18T14:00:00Z",
      "updatedAt": "2024-06-19T09:30:00Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 8,
    "totalPages": 1
  }
}
```

---

#### `GET /api/v1/repositories/{id}/pull-requests/{prId}`

Retrieve detailed pull request analysis including documentation diff.

**Response `200 OK`:**

```json
{
  "id": "pr-uuid-001",
  "repositoryId": "repo-uuid-001",
  "prNumber": 142,
  "title": "Add payment processing module",
  "author": "jdeveloper",
  "status": "OPEN",
  "headBranch": "feature/payments",
  "baseBranch": "main",
  "analysisId": "analysis-uuid-005",
  "findings": [
    {
      "id": "finding-uuid-020",
      "severity": "WARNING",
      "category": "MISSING_DOCS",
      "title": "New public class lacks documentation",
      "description": "PaymentProcessor is a new public class without Javadoc."
    }
  ],
  "documentationImpact": {
    "pagesAffected": 4,
    "newPages": 2,
    "modifiedPages": 2,
    "details": [
      {
        "pageId": "doc-uuid-050",
        "title": "PaymentProcessor Reference",
        "changeType": "ADDED"
      },
      {
        "pageId": "doc-uuid-010",
        "title": "Service Layer Overview",
        "changeType": "MODIFIED"
      }
    ]
  },
  "createdAt": "2024-06-18T14:00:00Z",
  "updatedAt": "2024-06-19T09:30:00Z"
}
```

---

### Jobs

#### `GET /api/v1/jobs/{jobId}`

Retrieve the status and details of a background job.

**Response `200 OK`:**

```json
{
  "id": "job-uuid-001",
  "type": "ANALYSIS",
  "status": "RUNNING",
  "progress": {
    "current": 64,
    "total": 128,
    "percent": 50,
    "currentStep": "Parsing source files"
  },
  "metadata": {
    "repositoryId": "repo-uuid-001",
    "analysisId": "analysis-uuid-001",
    "branch": "main"
  },
  "createdAt": "2024-06-20T10:00:00Z",
  "startedAt": "2024-06-20T10:00:02Z",
  "completedAt": null,
  "error": null
}
```

**Job Statuses:** `QUEUED` → `RUNNING` → `COMPLETED` | `FAILED` | `CANCELLED`

---

#### `GET /api/v1/jobs`

List background jobs. Supports filtering by type and status.

**Query Parameters:** `page`, `size`, `sort`, `type` (optional: `ANALYSIS`, `DOCUMENTATION_GENERATION`, `REPOSITORY_SYNC`), `status` (optional: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`), `repositoryId` (optional)

**Response `200 OK`:** _(Paginated list of job objects)_

---

### Webhooks

#### `POST /api/v1/webhooks/github`

> **Note:** This endpoint does **not** use Bearer/API-Key authentication. GitHub webhook
> signature verification (`X-Hub-Signature-256`, HMAC-SHA256) is used instead.

**Headers:**

| Header                    | Description                                   |
| ------------------------- | --------------------------------------------- |
| `X-GitHub-Event`          | Event type (`push`, `pull_request`, etc.)     |
| `X-Hub-Signature-256`    | HMAC-SHA256 signature of the payload           |
| `X-GitHub-Delivery`      | Unique delivery GUID                           |
| `Content-Type`            | `application/json`                             |

**Verification:** The raw payload is verified using `HMAC-SHA256(webhook_secret, body)` and
compared against the `X-Hub-Signature-256` header with a constant-time comparison. Missing
headers, a missing secret, or a mismatched signature are rejected.

**Idempotency:** Deliveries are keyed by `X-GitHub-Delivery`. A repeated delivery returns
the stored result without creating duplicate connections or repositories.

**Supported events in this milestone:** `installation` (`created`, `deleted`, `suspend`,
`unsuspend`, `new_permissions_accepted`) and `installation_repositories` (`added`,
`removed`). Other events (for example `push` and `pull_request`) are stored with status
`IGNORED` for the M3 job pipeline.

**Response `200 OK`:**

```json
{
  "received": true,
  "eventId": "delivery-guid-from-github",
  "status": "PROCESSED"
}
```

`status` is `PROCESSED`, `IGNORED`, or `FAILED`. `FAILED` deliveries are retained with an
error message for inspection.

**Response `400`:** Signature is valid but `X-GitHub-Event`/`X-GitHub-Delivery` is missing
or the payload is not a JSON object (`ERR_WEBHOOK_INVALID`).

**Response `401`:** Missing or invalid signature (`ERR_WEBHOOK_SIGNATURE`).

See [Webhook Payloads](#webhook-payloads) for event-specific payload examples.

---

### Health

#### `GET /actuator/health`

> **Note:** Not prefixed with `/api/v1`. No authentication required.

**Response `200 OK`:**

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "github": { "status": "UP" }
  }
}
```

---

#### `GET /actuator/info`

> **Note:** Not prefixed with `/api/v1`. No authentication required.

**Response `200 OK`:**

```json
{
  "app": {
    "name": "DocSync",
    "version": "1.2.0",
    "buildTime": "2024-06-20T08:00:00Z"
  },
  "java": {
    "version": "21.0.2"
  }
}
```

---

### Usage

#### `GET /api/v1/organizations/{id}/usage`

Retrieve current usage metrics and quota information for an organization.

**Response `200 OK`:**

```json
{
  "organizationId": "org-uuid-001",
  "plan": "TEAM",
  "period": {
    "start": "2024-06-01T00:00:00Z",
    "end": "2024-06-30T23:59:59Z"
  },
  "repositories": {
    "used": 5,
    "limit": 20
  },
  "analysisRuns": {
    "used": 47,
    "limit": 500
  },
  "members": {
    "used": 12,
    "limit": 25
  },
  "aiTokens": {
    "used": 125000,
    "limit": 500000
  }
}
```

---

## Rate Limiting

All API responses include rate limit headers:

| Header                  | Description                              |
| ----------------------- | ---------------------------------------- |
| `X-RateLimit-Limit`     | Maximum requests per window              |
| `X-RateLimit-Remaining` | Remaining requests in current window     |
| `X-RateLimit-Reset`     | Unix timestamp when the window resets    |
| `Retry-After`           | Seconds to wait (only on `429` responses)|

**Default Limits:**

| Plan   | Requests per minute | Analysis triggers per hour |
| ------ | ------------------- | -------------------------- |
| FREE   | 60                  | 5                          |
| TEAM   | 300                 | 30                         |
| ENTERPRISE | 1000            | 100                        |

When rate limited, the API returns:

```json
{
  "error": {
    "code": "ERR_RATE_LIMITED",
    "message": "Rate limit exceeded. Retry after 32 seconds.",
    "details": [],
    "traceId": "tr-xyz789"
  }
}
```

> **Implementation status:** DocSync's own per-plan throttling is not implemented yet
> (planned for M8). Separately, when GitHub's API quota is nearly exhausted (below 10%
> remaining) during repository onboarding, the API returns `503` with
> `ERR_GITHUB_RATE_LIMITED` and a `Retry-After` header. Installation-token creation is
> treated as critical and always proceeds; automatic retry of deferred work lands with M3.

---

## Webhook Payloads

### GitHub Push Event

Received when code is pushed to a monitored branch.

```json
{
  "ref": "refs/heads/main",
  "after": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "before": "0000000000000000000000000000000000000000",
  "repository": {
    "id": 123456789,
    "full_name": "acme-corp/backend-api",
    "default_branch": "main"
  },
  "pusher": {
    "name": "jdeveloper",
    "email": "dev@example.com"
  },
  "commits": [
    {
      "id": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
      "message": "Add payment processing module",
      "timestamp": "2024-06-20T10:00:00Z",
      "added": ["src/main/java/com/acme/service/PaymentService.java"],
      "removed": [],
      "modified": ["src/main/java/com/acme/config/AppConfig.java"]
    }
  ],
  "installation": {
    "id": 12345678
  }
}
```

**DocSync Behavior:** If `autoAnalyze` is enabled for the repository and the push is to the `defaultBranch`, an `INCREMENTAL` analysis is automatically triggered.

---

### GitHub Pull Request Event

Received when a pull request is opened, updated, or merged.

```json
{
  "action": "opened",
  "number": 142,
  "pull_request": {
    "id": 987654321,
    "number": 142,
    "title": "Add payment processing module",
    "state": "open",
    "head": {
      "ref": "feature/payments",
      "sha": "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3"
    },
    "base": {
      "ref": "main",
      "sha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
    },
    "user": {
      "login": "jdeveloper"
    }
  },
  "repository": {
    "id": 123456789,
    "full_name": "acme-corp/backend-api"
  },
  "installation": {
    "id": 12345678
  }
}
```

**DocSync Behavior:** If `analyzePullRequests` is enabled, DocSync runs an analysis on the PR's head branch and posts a summary comment on the PR with documentation impact and findings.
