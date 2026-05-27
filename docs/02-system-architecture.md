# 02 — System Architecture

**Audience:** builders. This is the map of the whole system: the views, the tiers, the service
boundaries, how things communicate, and the end-to-end flows. Read this after the
[overview](./00-overview.md) and [PRD](./01-product-requirements.md); read it before any of the
deep-dive chapters.

We describe the architecture with the **[C4 model](https://c4model.com/)** (Context → Container →
Component → Code) because it lets us zoom from "what is this and who uses it" down to "what's inside a
service" without one giant unreadable diagram. We then overlay the **4-tier security topology** from
the architecture SVG and walk the **canonical flows**.

---

## 1. Architectural style and the principles behind it

ContentHub is an **event-driven microservices** system fronted by a **synchronous API gateway**,
deployed on **Kubernetes (EKS/Fargate)**, with **polyglot persistence** (Postgres + Mongo + S3) and a
**least-privilege network**. That is a lot of machinery, so the *why* matters more than the *what*:

| Decision | Why | ADR |
| --- | --- | --- |
| **Microservices**, not a monolith | The workloads have wildly different scaling shapes: the API is latency-sensitive and bursty; transcription/render are CPU-heavy and batchy; collaboration is connection-heavy. Coupling them means scaling all-or-nothing. | [ADR-0009](./03-adr/) |
| **Event-driven (Kafka)** core | The expensive operations (transcribe, render, fetch metrics) are *asynchronous by nature* — the user shouldn't hold a request open for 15 minutes. Events decouple producers from consumers and give us durability + replay. | [ADR-0003](./03-adr/) |
| **Sync API gateway** at the edge | Interactive operations (load board, save script) *are* request/response. Forcing them through events would add latency and complexity for no gain. We use the right tool per interaction. | [ADR-0007](./03-adr/) |
| **Polyglot persistence** | Rigid, transactional data (users, billing) wants Postgres; irregular nested platform JSON wants Mongo; large blobs want S3. One database for all three is a compromise that serves none well. | [ADR-0002](./03-adr/) |

> **The honest caveat (R2 from the overview):** the *target* architecture below is what a funded team
> operates. A solo builder should start with the **modular monolith** path in [ADR-0009](./03-adr/) —
> the same service boundaries as Java modules in one deployable — and extract services when a real
> scaling or team boundary demands it. The boundaries described here are correct either way; only the
> *deployment topology* changes. Design for the destination, deploy for the present.

---

## 2. C4 Level 1 — System Context

Who and what touches ContentHub.

```
                         ┌──────────────────────────────────────┐
        P1 Operator ─────►                                       │
        P2 Editor   ─────►            ContentHub                 │◄──── P4 Analyst
        P3 Writer   ─────►   (browser-based content platform)    │
                         └───┬───────────────┬──────────────┬────┘
                             │               │              │
                  publish +  │       OAuth + │      OAuth +  │  identity
                  metrics    │       publish │      metrics  │  (JWT)
                             ▼               ▼              ▼
                     ┌──────────────┐ ┌───────────┐ ┌────────────────┐
                     │  YouTube API │ │   X API   │ │ Amazon Cognito │
                     └──────────────┘ └───────────┘ └────────────────┘
                             ▲
                             │ ASR (audio → text)
                     ┌───────┴────────┐
                     │  ASR provider  │   (managed transcription; see ADR-0005)
                     └────────────────┘
```

**External dependencies and why each is external:**

- **Amazon Cognito** — identity. We do *not* build auth; it is a solved, high-risk, undifferentiated
  problem. ([ADR-0004](./03-adr/))
- **YouTube / X APIs** — the platforms we publish to and measure. Wrapped in an **Anti-Corruption
  Layer** so their quirks never leak into our domain (see §6).
- **ASR provider** — speech-to-text. Pluggable; our value is the editing experience, not the model.
- **Observability SaaS** (Splunk/Datadog) and **load/BDD tooling** (BlazeMeter/Cucumber) — operational,
  covered in [`09`](./09-observability.md) and [`10`](./10-testing-qa.md).

---

## 3. C4 Level 2 — Containers (the deployable units)

This is the SVG architecture diagram, formalized. "Container" = a separately deployable/runnable
thing (an SPA, a service, a database) — not a Docker container specifically, though most are.

```
┌─────────────────────────────────────── CLIENT (browser / PWA) ───────────────────────────────────┐
│  React + Redux + TypeScript SPA                                                                    │
│   ├─ Service Worker + IndexedDB   → offline script cache & sync queue   (FR-SC-03/04)              │
│   ├─ WebSocket client             → live cursors + render alerts        (FR-RT-*)                  │
│   └─ Cognito SDK                  → JWT auth, OAuth social login        (NFR-SEC-03)               │
└───────────────────────────────────────────┬──────────────────────────────────────────────────────┘
                                 HTTPS 443 / WSS  (JWT in Authorization header)
                                              ▼
┌─────────────────────── TIER 1 · ALB-SG (only public-facing component) ────────────────────────────┐
│  Application Load Balancer   (TLS termination, path/host routing, WAF attach point)                │
└───────────────────────────────────────────┬──────────────────────────────────────────────────────┘
                                              ▼
┌────────────────── TIER 2 · EKS-Node-SG (private subnets · EKS on Fargate) ────────────────────────┐
│                                                                                                    │
│   ┌──────────────────────────── Spring Cloud API Gateway ────────────────────────────┐            │
│   │  · validates Cognito JWT (JWKS)  · routes to services  · rate-limits  · CORS      │            │
│   └──────┬───────────────┬──────────────────┬───────────────────────┬─────────────────┘            │
│          ▼               ▼                  ▼                       ▼                              │
│   ┌────────────┐  ┌────────────┐    ┌────────────┐         ┌──────────────────┐                   │
│   │ Workspace  │  │  Media     │    │ Analytics  │         │ AI Transcription │                   │
│   │  Service   │  │  Service   │    │  Service   │         │     Service      │                   │
│   │            │  │            │    │            │         │                  │                   │
│   │ Kanban,    │  │ upload,    │    │ YouTube/X  │         │ audio → transcript│                   │
│   │ scripts,   │  │ S3, EDL,   │    │ publish +  │         │ JSON              │                   │
│   │ collab WS, │  │ render     │    │ metrics    │         │                  │                   │
│   │ notify     │  │ orchestr.  │    │            │         │                  │                   │
│   └─────┬──────┘  └─────┬──────┘    └─────┬──────┘         └────────┬─────────┘                   │
│         │               │                 │                        │                              │
│         └───────────────┴────────┬────────┴────────────────────────┘                              │
│                                   ▼                                                                │
│        ┌──────────────────────────────────────────────────────────────────────┐                  │
│        │  Apache Kafka (Amazon MSK) — async event backbone                      │                  │
│        │  topics: video.uploaded · transcription.completed · render.requested   │                  │
│        │          render.finished · content.published · metrics.ingested · *.DLQ│                  │
│        └──────────────────────────────────────────────────────────────────────┘                  │
└───────────────────────────────────────────┬──────────────────────────────────────────────────────┘
                          5432 │            27017 │           S3 API (VPC endpoint)
                               ▼                  ▼                    ▼
┌──────────────────────────── TIER 3 · DB-SG (private · data tier) ─────────────────────────────────┐
│   PostgreSQL (RDS, Multi-AZ)     MongoDB (DocumentDB)        Amazon S3                              │
│   users, workspaces, cards,      transcripts, EDLs,          raw media (immutable),                │
│   scripts, billing, OAuth,       platform metrics JSON       rendered outputs                      │
│   outbox                                                                                           │
└────────────────────────────────────────────────────────────────────────────────────────────────────┘

Cross-cutting (every tier): OpenTelemetry → Splunk (logs) · Datadog (APM/infra) · Grafana (BI)
Provisioned by: Terraform                       Identity: Amazon Cognito
```

### 3.1 Container responsibilities — and the boundary logic

The single hardest question in microservices is *"where do the lines go?"* We draw them along
**business capabilities** that have distinct **data ownership** and distinct **scaling shapes** —
not along technical layers. Each service owns its data; no service reaches into another's database.

| Container | Owns (data) | Responsibilities | Scaling shape |
| --- | --- | --- | --- |
| **React SPA** | client cache (IndexedDB) | UI, optimistic edits, offline queue, WS client | CDN-served; N/A |
| **API Gateway** | none | JWT validation, routing, rate-limit, CORS, request tracing | scales with request rate |
| **Workspace Service** | workspaces, cards, scripts (Postgres) | Kanban, script CRUD, **WebSocket fan-out** for collab + notifications, sync reconciliation | connection-heavy (WS) + request |
| **Media Service** | media metadata, EDLs (Postgres), media blobs (S3) | presigned uploads, EDL management, **orchestrates render**, emits `video.uploaded` / `render.requested` | request + bursty render orchestration |
| **AI Transcription Service** | transcripts (Mongo) | consumes `video.uploaded`, calls ASR, writes transcript, emits `transcription.completed` | **queue-driven, batchy, CPU-heavy** |
| **Analytics Service** | OAuth grants (Postgres), metrics (Mongo) | platform publish, scheduled metric ingestion, funnel rollups | scheduled + bursty at publish time |

> **Why the WebSocket fan-out lives in the Workspace Service, not the Gateway:** collaboration state
> (who's editing what, cursor positions) is *workspace domain state*, and notifications are *workspace
> events*. Putting the stateful WS hub at the edge gateway would make the gateway stateful and couple
> connection management to routing. The gateway stays stateless and horizontally trivial; the
> Workspace Service owns the harder stateful-connection problem where the domain knowledge already is.
> (This service is the one place we accept stateful complexity — see [`11` §WebSocket scaling](./11-scalability-resilience.md).)

---

## 4. C4 Level 3 — Component view (inside a service)

Every Spring Boot service follows the same internal **hexagonal (ports & adapters)** layering, so that
business logic never depends on Kafka, the DB, or HTTP directly. This is what makes the
monolith→microservice extraction in [ADR-0009](./03-adr/) mechanical rather than traumatic.

```
            ┌──────────────────────── Media Service (example) ─────────────────────────┐
  HTTP ────►│  Inbound adapters                                                          │
  (REST)    │   ├─ REST controllers        ┐                                             │
  Kafka ───►│   └─ Kafka listeners         │                                             │
  events    │                              ▼                                             │
            │                        ┌──────────────┐   pure domain — no framework deps  │
            │                        │  Application │   (services, use-cases)             │
            │                        │   + Domain   │   EDL logic, render policy          │
            │                        └──────┬───────┘                                     │
            │                              ▲│                                             │
            │   Outbound adapters (ports)  ││                                             │
            │   ├─ Postgres repo (JPA)  ◄──┘│                                             │
            │   ├─ S3 client            ◄───┤                                             │
            │   ├─ Kafka producer       ◄───┤  (writes via the OUTBOX, not directly)      │
            │   └─ Render worker client ◄───┘                                             │
            └────────────────────────────────────────────────────────────────────────────┘
```

**Key internal patterns (used by every service):**

- **Hexagonal layering** — domain logic is a pure core; adapters are swappable. Testable without
  infrastructure (see Testcontainers in [`10`](./10-testing-qa.md)).
- **Transactional Outbox** — a service NEVER writes its DB and publishes to Kafka in two separate
  steps (that risks one succeeding and the other failing — a lost or phantom event). Instead it writes
  the domain change **and** the outbound event into one DB transaction (an `outbox` table); a relay
  publishes from the outbox to Kafka and marks rows sent. This gives us *at-least-once* delivery with
  no dual-write inconsistency. ([ADR-0006](./03-adr/), schema in [`04`](./04-data-model.md))
- **Idempotent consumers** — because delivery is at-least-once, every consumer must tolerate
  duplicates (dedupe on event ID / use upserts). See [`05` §idempotency](./05-api-and-event-contracts.md).
- **Anti-Corruption Layer (ACL)** — the Analytics Service wraps YouTube/X behind our own interface so
  their schema/quirks/quota errors never leak into our domain model.

---

## 5. The 4-tier security topology (the firewall chain)

The SVG's colored tiers are **AWS Security Groups** forming a strict least-privilege chain. This is
summarized here and specified fully in [`06-security-architecture.md`](./06-security-architecture.md).

```
 Internet  ──80/443──►  [ALB-SG]  ──►  [EKS-Node-SG]  ──5432/27017──►  [DB-SG]
                          ▲                  ▲                              ▲
            only SG that  │   accepts ONLY   │   accepts ONLY from         │
            allows        │   from ALB-SG;    │   EKS-Node-SG; blocks       │
            0.0.0.0/0     │   blocks internet │   internet AND the ALB      │
```

**The principle:** each tier accepts traffic *only* from the tier directly in front of it, identified
by **security group reference** (not IP ranges — SGs are dynamic and IPs are not). A compromised ALB
cannot reach the database; it can only reach the node tier. A compromised pod cannot reach the
internet outbound except through controlled egress. Defense in depth, by construction.

> **Why SG-reference rules instead of CIDR allow-lists:** in an autoscaling world, pod and node IPs
> change constantly. A rule that says *"allow from anything in EKS-Node-SG"* stays correct as nodes
> come and go; a rule that says *"allow from 10.0.3.0/24"* breaks the moment the topology shifts and
> tempts you to widen it. SG references make least-privilege the *easy* path.

---

## 6. Communication patterns — sync vs. async (the core design tension)

Choosing sync vs. async per interaction is the most consequential everyday architecture decision.
The rule:

> **Synchronous** when the user is *waiting and cannot proceed* without the answer (load board, save
> script, validate JWT). **Asynchronous** when the work is *slow, retryable, or fan-out* (transcribe,
> render, publish, ingest metrics, notify).

| Interaction | Pattern | Transport | Why |
| --- | --- | --- | --- |
| Load Kanban / script | Sync | REST via Gateway | User is blocked on the result; must be fast. |
| Save script edit | Sync (optimistic) | REST | Confirm persistence; UI already updated locally. |
| Upload media | Sync handshake + direct-to-S3 | REST (presign) + S3 PUT | Service issues a presigned URL; the bytes bypass our services entirely. |
| Transcribe | **Async** | Kafka `video.uploaded` → `transcription.completed` | 15-minute job; user must not wait; retryable. |
| Render | **Async** | Kafka `render.requested` → `render.finished` | Heavy compute; fire-and-forget + notify. |
| Notify user of completion | **Async push** | Kafka → Workspace Service → WebSocket | Decouples the worker from the client connection. |
| Live cursors | Real-time bidirectional | WebSocket | Sub-250 ms, bidirectional, high-frequency. |
| Publish to YouTube/X | **Async** | Kafka `content.published` (+ ACL call) | External, slow, quota-limited, retryable. |
| Ingest metrics | **Async, scheduled** | cron → Kafka `metrics.ingested` | Batch pull; no user waiting. |

> **Why uploads go *direct to S3* and not through the Media Service:** a 5 GB file streamed through a
> pod consumes that pod's memory/bandwidth and blocks it from serving other requests — it doesn't
> scale and it's expensive. Instead the Media Service issues a **presigned S3 URL**; the browser PUTs
> the bytes straight to S3; S3 (or the client) signals completion, which emits `video.uploaded`. Our
> compute never touches the large payload. This is the standard pattern for large-object handling and
> a recurring theme: *keep big/slow things off the request path.*

---

## 7. Canonical flow — AI transcription (the spine, end-to-end)

This is journey J1's technical realization and the reference example for the whole event-driven design.

```
1. Browser ──(POST /media, JWT)──► API Gateway ──validate JWT──► Media Service
                                                                      │
2. Media Service ── returns presigned S3 URL ─────────────────────────┘
3. Browser ── PUT bytes ───────────────────────────────────► S3  (bypasses our compute)
4. Upload complete ──► Media Service writes media row + outbox event (ONE tx)
                            │
5. Outbox relay ──► Kafka topic: video.uploaded  {mediaId, s3Key, workspaceId, traceId}
                            │
6. AI Transcription Service consumes video.uploaded (idempotent on mediaId)
       ├─ calls ASR provider (audio → word-timed JSON)
       ├─ writes transcript document → MongoDB
       └─ writes outbox event ─► Kafka: transcription.completed {mediaId, transcriptId}
                            │
7. Workspace Service consumes transcription.completed
       └─ looks up which users have this media open
       └─ pushes over WebSocket ─► Browser: "transcript ready" + payload
                            │
8. Browser renders editable transcript. User edits text → EDL updates (Media Service).
```

**Read this flow as a checklist of properties the architecture must guarantee:**

- **No lost work** — steps 4 and 6 use the outbox, so a crash between "save" and "publish" can't drop
  the event (it's already durably in the DB and will be relayed). [ADR-0006](./03-adr/)
- **No duplicates causing harm** — step 6 is idempotent on `mediaId`; replaying `video.uploaded` won't
  produce two transcripts.
- **No user waiting** — the browser is free after step 3; completion arrives as a push (step 7).
- **Traceability** — `traceId` is minted at step 1 and propagated through every event and log line, so
  the entire journey is one queryable trace in Datadog/Splunk. (NFR-O-03)
- **Backpressure-friendly** — if transcription is overwhelmed, `video.uploaded` simply accumulates in
  Kafka and KEDA scales the consumers on lag ([`11`](./11-scalability-resilience.md)); nothing breaks,
  it just drains slower.

---

## 8. Real-time collaboration architecture (FR-RT-*)

The hardest stateful problem in the system, so it gets explicit treatment.

- **Transport:** WebSocket (WSS) terminated at the Workspace Service. The ALB supports WS upgrade;
  the Gateway authenticates the initial handshake (JWT), then the connection is held by a Workspace
  pod.
- **Concurrency model:** collaborative text editing uses **CRDTs** (Conflict-free Replicated Data
  Types) for scripts — specifically a sequence CRDT (e.g., Yjs-style) so concurrent inserts/deletes
  merge deterministically without a central lock, and the *same* mechanism powers offline sync
  (an offline client is just a very-delayed replica). This is why FR-SC-04 (offline) and FR-RT-02
  (live merge) are *one* problem, not two. ([ADR-0008](./03-adr/))
- **Scaling connections across pods:** when collaborators land on different Workspace pods, edits are
  relayed between pods over a Kafka topic (or Redis pub/sub) keyed by `scriptId`, so all pods holding
  a connection to that script receive the update. (Detail + tradeoffs in [`11`](./11-scalability-resilience.md).)
- **Notifications (FR-RT-03)** reuse the same WS infrastructure: completion events from Kafka are
  fanned out to the relevant connected clients.

> **Why CRDTs over Operational Transform (OT):** OT (what Google Docs originally used) requires a
> central server to transform and sequence every operation — correct but complex and a scaling
> chokepoint. CRDTs merge peer-to-peer with eventual consistency and, crucially, give us **offline
> editing for free** because a reconnecting client merges cleanly regardless of how long it was gone.
> For a small team optimizing for correctness-without-a-central-sequencer and offline support, CRDTs
> are the lower-risk bet. Tradeoffs (metadata overhead, no global invariants) are acceptable for
> prose. Full reasoning in [ADR-0008](./03-adr/).

---

## 9. Environment topology

Three logical environments, mapped to the cost strategy ([`12`](./12-cost-and-local-dev.md)). The
*same* container images and Helm charts run everywhere; only configuration (Spring profiles `dev` /
`qa` / `prod`) and infrastructure scale differ.

| Environment | Where | Kafka | DB | Cost | Purpose |
| --- | --- | --- | --- | --- | --- |
| **local** | Docker Compose / Minikube on the laptop | Confluent local image | Postgres + Mongo containers; LocalStack for S3/Cognito | $0 | The inner dev loop — 95% of work |
| **qa (ephemeral)** | AWS via `terraform apply` | Amazon MSK | RDS + DocumentDB | pennies/hr, then destroyed | Production-fidelity + load/BDD tests |
| **prod** | AWS, always-on, autoscaling | Amazon MSK (multi-AZ) | RDS Multi-AZ + DocumentDB | scales w/ revenue | Live |

> **The "same image everywhere" rule (12-factor):** configuration comes from the environment (Spring
> profiles + secrets), never baked into the build. A bug reproduced locally is the *same binary* that
> runs in prod. This is non-negotiable for a small team — it eliminates the entire class of "works on
> my machine / works in qa but not prod" failures.

---

## 10. Architecture cross-reference

| To understand… | Read |
| --- | --- |
| Why each major choice was made | [`03-adr/`](./03-adr/) |
| The actual table/document schemas | [`04-data-model.md`](./04-data-model.md) |
| The exact REST/WS/Kafka contracts | [`05-api-and-event-contracts.md`](./05-api-and-event-contracts.md) |
| The SG chain, JWT, IAM in depth | [`06-security-architecture.md`](./06-security-architecture.md) |
| How it's provisioned | [`07-infrastructure-terraform.md`](./07-infrastructure-terraform.md) |
| How services scale & stay up | [`11-scalability-resilience.md`](./11-scalability-resilience.md) |
