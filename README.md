# DocSync

Developer documentation automation platform that keeps technical documentation synchronized with the actual state of a software repository.

## Overview

DocSync connects to source-code repositories, detects relevant changes, identifies potentially affected documentation, validates examples, and proposes documentation updates through reviewable pull requests.

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 20+ and npm
- Docker & Docker Compose
- PostgreSQL 16 (provided via Docker Compose)
- Git 2.40+ (required at runtime for repository clone/fetch)

### Setup

1. **Start infrastructure:**
   ```bash
   docker-compose up -d
   ```

2. **Build the application:**
   ```bash
   mvn clean package
   ```

3. **Run the application:**
   ```bash
   java -jar target/docsync-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
   ```
   Or:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

4. **Verify health:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Running Tests

```bash
# Unit tests only
mvn test

# Unit + integration tests
mvn verify
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` and proxies `/api` to the backend on `http://localhost:8080`.

## Implemented API Surface

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/github`
- `GET/PATCH /api/v1/users/me`
- `POST/GET /api/v1/organizations`, organization detail/update, membership management
- `GET /api/v1/organizations/{orgId}/connections`
- `GET /api/v1/connections/claimable`, `POST .../connections/{id}/claim`,
  `DELETE .../connections/{id}`
- `GET/POST /api/v1/organizations/{orgId}/repositories`
- `GET .../repositories/{id}`, `PATCH .../{id}/configuration`, `POST .../{id}/sync`,
  `DELETE .../{id}`
- `POST /api/v1/webhooks/github` (HMAC-SHA256 signature, no JWT)

## Documentation

See the [docs/](docs/) directory for comprehensive project documentation:

- [Product Requirements](docs/PRD.md)
- [High-Level Design](docs/HLD.md)
- [API Specification](docs/API_SPEC.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Current Status](docs/progress/CURRENT_STATUS.md)

## License

Proprietary - All rights reserved.
