# ContentHub — Claude Code project context

**What this is:** A browser-based, Descript-style content production platform. Upload raw media, edit
video by editing its auto-generated transcript, collaborate live, publish to YouTube + X, measure
performance — all in a browser tab. Full blueprint in `docs/` (14 chapters, 10 ADRs).

---

## Essential commands

```bash
# Infrastructure
make doctor        # check prerequisites (Docker, Java 25, Node 20, Maven)
make dev-up        # start the full local stack (Postgres, Mongo, Kafka, LocalStack, Jaeger, Grafana)
make dev-down      # stop containers, keep volumes
make logs          # tail all container logs  (make logs svc=kafka)
make clean-force   # wipe all local data volumes

# Backend
make backend-build  # compile (requires JAVA_HOME → JDK 25)
make backend-test   # run all tests
make backend-run    # run Spring Boot with profile=dev

# Frontend
make frontend-install  # npm install in frontend/
make frontend-dev      # Vite HMR on :5173 (proxies /api → :8080)
make frontend-build    # tsc + vite build → dist/
```

---

## Current phase

**Phase 0 — Walking skeleton** (`docs/00-overview.md` §11): prove the end-to-end spine.
Auth → upload → S3 → transcribe → transcript visible in browser.

The docs blueprint is complete. The local docker-compose dev stack is running. The **next deliverable**
is the Spring Boot modular monolith skeleton. See `PROGRESS.md` for exact scope and the resume prompt.

---

## Architecture in one sentence

React SPA → Spring Cloud Gateway (JWT) → modular-monolith Spring Boot app → Kafka async backbone →
Postgres (relational) + MongoDB (transcripts/metrics) + S3 (media blobs).

---

## Frontend conventions (non-negotiable)

| Rule | Why |
|------|-----|
| **Use Atlaskit components for all UI** — never raw `<button>`, `<input>`, `<h1>`, `<form>` etc. | Design-system consistency; Atlaskit handles a11y and focus management for free |
| **RTK Query for every API call** — never `createAsyncThunk`, never raw `axios` in components | Single data layer: caching, invalidation, loading/error states managed in one place |
| **All colors, spacing, radius from `docs/DESIGN.md`** — never hardcode `#hex`, `px`, or `rem` values | Tokens stay in sync with the design spec; dark-mode flips automatically |
| **WCAG 2.1 AA minimum** — check new colors against the contrast tables in `docs/DESIGN.md §3` | Accessibility is a hard requirement, not a nice-to-have |
| **Studio Green (`#1ED760`) is CTA-only** — primary buttons, active nav, live indicators, nothing else | Scarcity preserves visual impact; decoration dilutes it |
| **Black text on green-500 buttons** — white text on `#1ED760` fails WCAG (1.9:1) | Contrast rule; black gives 11.3:1 (AAA) |

> **Design source of truth:** `docs/DESIGN.md` — tokens, colour modes, Atlaskit theme overrides,
> component quick-reference, and the five usage rules. Read it before touching any UI file.

---

## Non-negotiable rules (burn you if you break them)

| Rule | Reason | ADR |
|------|---------|-----|
| **Modular monolith first** — one Spring Boot deployable, 5 Maven modules, package-private boundaries | Building 5 separate services before a single user exists is the project's top risk (R2) | ADR-0009 |
| **Transactional outbox for all Kafka events** — write domain row + outbox row in ONE transaction | Dual-write (save DB then publish Kafka) risks lost or phantom events | ADR-0006 |
| **Every Kafka consumer must be idempotent** — dedupe on event ID / use upserts | Outbox gives at-least-once delivery; duplicates are guaranteed to arrive | ADR-0006 |
| **No cross-module DB access** — each module owns its tables, others read via API or events | This is what makes the monolith → microservice extraction mechanical, not traumatic | ADR-0009 |
| **Spring profile `dev` wires to docker-compose; never bake prod config into the build** | Same image, different config = "works on my machine" class of bugs eliminated | `docs/02` §9 |

---

## Module boundaries (the 5 domains)

```
shared-kernel       domain primitives, outbox model, shared event types, ArchUnit rules
workspace-module    Kanban boards, cards, scripts (CRDT), WebSocket collab fan-out
media-module        presigned S3 uploads, media metadata, EDL, render orchestration
transcription-module Kafka consumer for video.uploaded, mock ASR adapter, transcript write
analytics-module    YouTube/X OAuth, publish scheduling, metrics ingestion
```

---

## Key doc pointers

| Question | Read |
|----------|------|
| What are we building, for whom? | `docs/00-overview.md` |
| How does everything connect? | `docs/02-system-architecture.md` |
| Why each major decision? | `docs/03-adr/` (10 ADRs) |
| What are the DB schemas? | `docs/04-data-model.md` |
| What are the Kafka topic contracts? | `docs/05-api-and-event-contracts.md` |
| How does local dev work? | `docs/12-cost-and-local-dev.md` |
| **Design tokens, colours, Atlaskit theming?** | **`docs/DESIGN.md`** |
| What's done and what's next? | `PROGRESS.md` |

---

## Local stack (docker-compose services)

| Container | Local stand-in for | Host port |
|-----------|-------------------|-----------|
| postgres | RDS PostgreSQL | 5432 |
| mongo | DocumentDB | 27017 |
| kafka | Amazon MSK | 29092 (host) |
| schema-registry | MSK Schema Registry | 8081 |
| localstack | S3 | 4566 |
| mock-oauth2 | Amazon Cognito | 8090 |
| otel-collector | Datadog OTel agent | 4317 gRPC |
| jaeger | Datadog APM traces | 16686 UI |
| prometheus | — | 9090 |
| grafana | Grafana SaaS | 3001 |

Spring profile `dev` connection strings are in `.env.local` (gitignored; copy from `.env.local.example`).
