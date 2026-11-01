# ADR-002: PostgreSQL with Flyway for Data Persistence

| Field      | Value                     |
| ---------- | ------------------------- |
| **Status** | Accepted                  |
| **Date**   | 2024-01-10                |
| **Author** | DocSync Architecture Team |

## Context

DocSync needs a persistent data store for:

- User accounts, organizations, and memberships (relational, transactional)
- Repository metadata and configuration (relational with some flexible fields)
- Analysis results, code entities, findings (high-volume, write-heavy during analysis; read-heavy during serving)
- Background job queue (requires row-level locking and atomic state transitions)
- Generated documentation pages (medium-volume text storage)
- Audit events and webhook payloads (append-only, time-series-like)

Key data access patterns:

1. **Multi-tenant queries:** Almost every query filters by `organization_id`.
2. **Transactional writes:** Creating an analysis involves inserting into multiple tables atomically.
3. **Job queue operations:** Claiming a job requires `SELECT ... FOR UPDATE SKIP LOCKED` or optimistic locking.
4. **JSONB queries:** Repository configuration and job metadata are semi-structured.
5. **Full-text search (future):** Searching documentation content.

We also need a schema migration strategy that supports zero-downtime deployments and is auditable in version control.

## Decision

We will use **PostgreSQL 16** as the primary (and only) database, with **Flyway** for schema migration management and **Spring Data JPA** (Hibernate 6.x) as the ORM.

### Why PostgreSQL

- **JSONB support:** Native JSON storage with indexing (GIN indexes) for semi-structured data like repository configuration and job metadata—eliminates the need for a separate document store.
- **Row-level locking:** `SELECT ... FOR UPDATE SKIP LOCKED` is ideal for the job queue pattern, letting multiple workers claim jobs without contention.
- **Full-text search:** Built-in `tsvector`/`tsquery` with GIN indexes provides adequate full-text search for documentation pages without needing Elasticsearch.
- **ACID transactions:** Multi-table writes during analysis result persistence are straightforward.
- **Mature ecosystem:** Excellent tooling, monitoring (pg_stat_statements, pgBouncer), and operational knowledge.
- **UUID type:** Native 128-bit UUID storage (16 bytes) is more efficient than `VARCHAR(36)`.

### Why Flyway

- **Version-controlled migrations:** Each migration is a numbered SQL file committed to Git, providing a complete audit trail of schema changes.
- **Spring Boot integration:** Auto-runs on application startup; zero additional infrastructure.
- **Checksum validation:** Detects if a previously applied migration was modified (accidental or malicious).
- **Team-friendly:** Simple SQL files—no proprietary DSL to learn. Any developer who knows SQL can write migrations.
- **Rollback strategy:** We use forward-only migrations. Rollbacks are new forward migrations that undo changes. This is safer than automated rollback scripts.

### Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:docsync}
    username: ${DB_USER:docsync}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 10000
      idle-timeout: 300000
      max-lifetime: 600000

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true
    clean-disabled: true  # Never allow clean in production
```

## Alternatives Considered

### 1. MySQL 8

**Pros:**
- Widely used, large community
- Good performance for read-heavy workloads
- Mature replication

**Cons:**
- No native JSONB with indexing (JSON type exists but indexing is limited to generated columns)
- No `SKIP LOCKED` until 8.0 (and less mature than PostgreSQL's implementation)
- No built-in full-text search on JSON content
- Weaker support for UUID as a native type
- `READ COMMITTED` isolation level behaves differently (gap locking)

**Rejected because:** PostgreSQL's JSONB, `SKIP LOCKED`, and full-text search features directly match our requirements without workarounds.

### 2. MongoDB

**Pros:**
- Native document storage for flexible schemas
- Horizontal scaling with sharding
- Good for write-heavy workloads

**Cons:**
- No ACID transactions across collections (multi-document transactions added in 4.0 but with performance caveats)
- Our data is fundamentally relational (users → organizations → repositories → analyses)
- Joins require `$lookup` aggregation stages, which are less efficient and harder to reason about than SQL JOINs
- Schema enforcement requires application-level validation or JSON Schema (less rigorous than DDL)
- Operational complexity (replica sets, sharding configuration)

**Rejected because:** DocSync's data model is inherently relational with clear foreign key relationships. A document store would force us to denormalize extensively or perform expensive application-side joins. The few semi-structured fields (configuration, metadata) are well-served by PostgreSQL's JSONB.

### 3. H2 for Development / PostgreSQL for Production

**Pros:**
- Faster test execution (in-memory)
- No database setup for local development

**Cons:**
- SQL dialect differences cause subtle bugs (H2 doesn't support `JSONB`, `SKIP LOCKED`, PostgreSQL-specific functions)
- Migrations that work on H2 may fail on PostgreSQL and vice versa
- Testcontainers provides a better solution: real PostgreSQL in tests with minimal overhead

**Rejected as default approach:** We use Testcontainers to run PostgreSQL in integration tests, ensuring tests exercise the same database engine as production. H2 is not used anywhere.

### 4. Liquibase (instead of Flyway)

**Pros:**
- XML/YAML/JSON changelog formats in addition to SQL
- More sophisticated rollback support
- Database diffing tool

**Cons:**
- More complex configuration
- XML changelogs are verbose and harder to review in PRs
- Rollback features encourage practices we consider risky (automated rollbacks)
- Flyway's simpler model (numbered SQL files, forward-only) fits our workflow better

**Rejected because:** Flyway's simplicity is a feature. Plain SQL migrations are easier to write, review, and debug. We don't need Liquibase's additional abstraction layers.

## Consequences

### Positive

- **Single data store:** No operational overhead of managing multiple databases. PostgreSQL handles relational data, semi-structured data (JSONB), job queue, and full-text search.
- **Schema safety:** Flyway ensures every environment runs the exact same schema. `validate-on-migrate` catches drift at startup.
- **Developer experience:** Developers write standard SQL migrations. IntelliJ/VS Code provide excellent PostgreSQL and SQL tooling.
- **Testability:** Testcontainers ensures integration tests run against real PostgreSQL, catching dialect-specific issues before deployment.

### Negative

- **Vertical scaling for the database.** PostgreSQL scales vertically well, but if we need horizontal read scaling, we'll need read replicas. Mitigation: Connection pooling (HikariCP) and caching (Spring Cache) reduce database load.
- **Job queue in the database** adds load to the primary database. If job throughput becomes a bottleneck, we may need to extract the job queue to a dedicated system (see ADR-004 for details).
- **PostgreSQL operational expertise required.** The team needs to understand vacuuming, connection management, and query planning. Mitigation: Use managed PostgreSQL (e.g., AWS RDS, Cloud SQL) to offload most operational concerns.

### Monitoring

The following metrics should be monitored:

- Connection pool utilization (`hikaricp_connections_active`)
- Query duration (slow query log threshold: 500ms)
- Table bloat and vacuum frequency
- Flyway migration status on deployment
- Database size growth rate (especially `webhook_events` and `audit_events` tables)
