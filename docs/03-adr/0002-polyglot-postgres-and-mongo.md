# ADR-0002 — Polyglot persistence: PostgreSQL + MongoDB

**Status:** Accepted · 2026-05-27

## Context

ContentHub stores two fundamentally different *shapes* of data:

1. **Rigid, relational, transactional** — users, workspaces, members/roles, Kanban cards, billing,
   OAuth grants, the media/EDL metadata. These have stable schemas, strong relationships, and demand
   ACID guarantees (you cannot half-charge a customer or lose a role assignment).
2. **Irregular, nested, schema-drifting** — AI transcription output (deeply nested word-timing trees)
   and YouTube/X analytics payloads (different per platform, and they change their APIs without
   warning). Forcing these into relational tables means a schema migration every time a platform adds
   a field, and a forest of join tables to model nesting.

The master plan specifies **PostgreSQL** for structured data and **MongoDB** for unstructured.

## Decision

Use **polyglot persistence**: **PostgreSQL (RDS)** for relational/transactional data and **MongoDB
(DocumentDB)** for nested/irregular document data. Each service owns its store; no cross-service DB
access.

## Consequences

**Positive**
- Each data shape gets the engine that fits: Postgres gives ACID, constraints, and rich querying for
  the data that needs it; Mongo absorbs schema variability for the data that has it.
- Platform API changes (new metric fields) require **no migration** — documents just carry new keys.
- Transcripts (large nested word arrays) read/write as a single document — no join explosion.

**Negative (accepted)**
- **Two databases to operate, back up, secure, and monitor** instead of one.
- **No cross-store transactions or joins** — combining a card (PG) with its transcript (Mongo) happens
  in the application layer. We accept eventual consistency across stores and use the outbox/events to
  keep them coherent ([ADR-0006](./0006-transactional-outbox.md)).
- Two data-access skill sets and two sets of operational gotchas.

## Alternatives considered

- **Postgres only, with `JSONB`** for the irregular data. Genuinely tempting — `JSONB` handles nested
  JSON well and would mean one database. Rejected for v1 because the master plan mandates the split
  *and* because very large/deeply-nested transcript and analytics documents are exactly Mongo's
  sweet spot; but this is the most reasonable alternative and is explicitly the **fallback** if
  operating two stores proves too heavy for a solo operator. (If revisited, this ADR would be
  superseded.)
- **Mongo only.** Rejected: we are not giving up ACID + foreign keys for billing and roles.
- **A managed search/analytics store (e.g., OpenSearch) for metrics.** Deferred: useful later for the
  funnel queries, but additive, not a replacement for the document store.

> **Why DocumentDB rather than self-hosted Mongo or Atlas:** DocumentDB sits inside our VPC behind the
> DB-SG ([`06`](../06-security-architecture.md)) with the same operational model (backups, multi-AZ,
> KMS) as RDS, keeping the data tier uniform from a security/ops standpoint. The tradeoff is
> DocumentDB's partial Mongo API compatibility — we pin to supported features and verify with
> Testcontainers ([`10`](../10-testing-qa.md)).
