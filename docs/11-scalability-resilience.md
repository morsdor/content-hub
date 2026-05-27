# 11 — Scalability & Resilience

**Audience:** builders + architects. How the system **grows** under load and **survives** failure —
the two qualities that separate a demo from a production platform. This is where the "future
scalability, resilience and performance" mandate from the brief gets concrete.

> **The core distinction to hold throughout:** **scalability** is about *handling more* (more users,
> more uploads, more concurrent editors); **resilience** is about *handling broken* (a dead pod, a
> throttling provider, an AZ outage). They're different problems with different tools, and a system
> needs both. A system that scales but isn't resilient falls over the first time a dependency hiccups;
> one that's resilient but doesn't scale is reliably slow.

---

## 1. Scaling model per service (scale what's hot, not everything)

The whole reason for microservices ([ADR-0009](./03-adr/0009-modular-monolith-first.md)) is that these
have **different scaling shapes** — coupling them means scaling all-or-nothing.

| Service | Bottleneck | Scaling trigger | Mechanism |
| --- | --- | --- | --- |
| API Gateway | request CPU | request rate / CPU | **HPA** (≥2 replicas always — it's on every path) |
| Workspace (REST) | request CPU | CPU / RPS | HPA |
| Workspace (**WebSocket**) | **held connections / memory** | connection count | HPA on custom metric + cross-pod relay (§4) |
| Media (orchestration) | bursty at upload/render | CPU / queue | HPA |
| **Transcription / Render** | **CPU-heavy, batchy** | **Kafka consumer lag** | **KEDA** → scale on lag, **toward zero** when idle |
| PostgreSQL | connections, write throughput | — | vertical + **read replicas** + PgBouncer pooling |
| MongoDB (DocumentDB) | read/write throughput | — | replica set reads; shard if needed |
| Kafka (MSK) | partition throughput | — | partitions sized for parallelism ([`05` §4](./05-api-and-event-contracts.md)) |

**Three layers of autoscaling, each doing a different job:**

```
 KEDA / HPA   → scale PODS (more replicas of a service)        ← workload-aware (lag, RPS, CPU)
 Karpenter    → provision NODES for pods that can't fit         ← only if/when we add node groups
 Fargate      → each pod is its own micro-VM (no node mgmt)     ← default compute (ADR-0010)
```

Because we default to **Fargate**, "node" scaling is mostly moot (each pod gets its own micro-VM);
**Karpenter** enters only when we add GPU/cost-optimized node groups for self-hosted render/ASR
([ADR-0010](./03-adr/0010-fargate-compute.md)).

---

## 2. The headline pattern: queue-based autoscaling with KEDA

This is the most important scaling decision in the system and the one that makes the cost story work.

```
 uploads burst ──► video.uploaded piles up in Kafka ──► consumer LAG rises
                                                              │
                                              KEDA watches lag │
                                                              ▼
                        scale transcription consumers 1 → 2 → … → N   (drain faster)
                                                              │
                          lag falls back to ~0 ──► KEDA scales back ──► toward ZERO when idle
```

> **Why scale on Kafka lag, not CPU (this is the crux):** CPU-based scaling is *reactive and laggy* for
> batch work — by the time CPU is pegged, the backlog is already huge and users are already waiting.
> **Lag is the direct measure of "how far behind are we"** — the actual thing users feel. Scaling on it
> means we add workers the instant a backlog forms and remove them the instant it clears. And because
> Fargate workers cost nothing when scaled to zero ([ADR-0010](./03-adr/0010-fargate-compute.md)), an
> idle night costs ~nothing while a viral upload spike spins up dozens of workers automatically. **One
> metric — consumer lag — is simultaneously the scaling signal *and* the health/alert signal**
> ([`09` §4](./09-observability.md)). That elegance is not an accident; it's why the architecture is
> event-driven.

---

## 3. The transcription/render bottleneck (the known hard problem)

Transcription and rendering are the **expensive, slow, COGS-dominating** operations (R3 in the
[risk register](./00-overview.md)). They get explicit design:

- **Fully async, queue-buffered** — never on the request path; a spike becomes queue depth, not errors
  or timeouts ([`02` §6](./02-system-architecture.md)).
- **KEDA lag-scaling** (§2) absorbs bursts elastically.
- **Per-plan quotas** (`usage_meter`, [`04` §2.4](./04-data-model.md)) cap consumption and protect COGS
  — a runaway workspace can't bankrupt us; it hits its quota and queues.
- **Provider abstraction** lets us route to the cheapest/fastest ASR and **fail over**
  ([ADR-0005](./03-adr/0005-third-party-asr.md)).
- **Render is the heaviest** — CPU/GPU-bound video stitching. Path: start on managed compute; when
  render volume justifies it, move to **GPU node groups via Karpenter + Spot** for cost
  ([ADR-0010](./03-adr/0010-fargate-compute.md)). The EDL model ([`04` §2.3](./04-data-model.md)) helps
  here too — rendering concatenates pre-cut segments rather than reprocessing whole sources.
- **Prioritization** — paid tiers get a higher-priority queue/consumer group so free/over-quota work
  can't starve paying customers.

---

## 4. The genuinely hard scaling problem: WebSockets at 10k connections

Stateless REST scales trivially (add pods behind the ALB). **Stateful WebSocket connections do not** —
this is where naive designs break (NFR-S-02, load-tested in [`10` §6](./10-testing-qa.md)).

**The problem:** two collaborators on the same script may be connected to *different* Workspace pods. A
keystroke from user A (on pod 1) must reach user B (on pod 2). Pods don't share memory.

```
   User A ── WS ──► Workspace pod 1 ─┐
                                     ├─► relay (Kafka topic / Redis pub-sub, keyed by scriptId)
   User B ── WS ──► Workspace pod 2 ─┘     every pod holding a connection to scriptId gets the update
```

**Design:**
- **Sticky-by-nothing, relay-by-everything** — pods don't need affinity; a per-`scriptId` relay
  (Kafka topic or Redis pub/sub) broadcasts CRDT updates so whichever pods hold connections to that
  script receive them. CRDTs ([ADR-0008](./03-adr/0008-crdt-for-collaboration.md)) make this safe:
  updates are commutative/idempotent, so duplicate or reordered relays converge correctly.
- **Scale pods on connection count** (custom HPA metric), and **shed/rebalance** on pod loss — clients
  auto-reconnect (to any pod) and re-sync over REST ([`05` §3](./05-api-and-event-contracts.md)), so a
  pod dying drops connections for seconds, not data.
- **Connection limits per pod** + back-pressure so one pod can't be overwhelmed.

> **Why this is called out as *the* hard problem:** every other scaling concern here is solved by
> "add stateless replicas." Held connections + cross-pod fan-out is the one place we accept real
> stateful-distributed-systems complexity, so it gets the explicit relay design, the custom scaling
> metric, *and* the lion's share of the load-testing budget ([`10` §6](./10-testing-qa.md)). Know where
> your hard problem is and aim your effort there.

---

## 5. Caching (latency + load relief, applied surgically)

| Layer | Cache | Guards against |
| --- | --- | --- |
| Edge | CDN (CloudFront) for the SPA + static assets | every asset request hitting origin |
| Client | IndexedDB (scripts, offline) ([`01` FR-SC](./01-product-requirements.md)) | network round-trips; enables offline |
| Service | Redis for hot reads (board, session, JWKS, presence) | DB load on the hottest queries |
| DB | read replicas for read-heavy analytics/funnel | read load on the primary |

> **The caching discipline:** cache deliberately, with a clear **invalidation** story for each (the
> classic "two hard problems" — naming and cache invalidation). We add a cache when a *measured* hot
> path demands it (Datadog shows the DB query is hot), not speculatively — a stale cache is worse than
> a slow query, and an unnecessary cache is just a second source of truth to keep coherent. Optimistic
> UI ([`01` NFR-P-02](./01-product-requirements.md)) already hides most latency client-side, reducing
> how much server-side caching we actually need.

---

## 6. Resilience patterns (surviving the inevitable failure)

Failures are not exceptional — at scale, *something* is always degraded. We design for it with
**Resilience4j** (in the Spring services) and architectural patterns:

| Pattern | What it does | Where |
| --- | --- | --- |
| **Timeouts** | never wait forever on a dependency | every external/inter-service call |
| **Retries + backoff + jitter** | ride out transient blips | ASR/YouTube/X calls, DB reconnects |
| **Circuit breaker** | stop hammering a dead dependency; fail fast, recover automatically | calls to ASR, platform APIs, between services |
| **Bulkhead** | isolate resource pools so one slow dependency can't exhaust all threads | per-dependency thread/connection pools |
| **Rate limiting** | protect from overload + enforce plan quotas | Gateway + per-service |
| **Graceful degradation** | partial function beats total failure | e.g., analytics down → editing still works |
| **Idempotency** | safe retries, no double-effects | every Kafka consumer ([`05` §5](./05-api-and-event-contracts.md)) |

```java
// Calls to the ASR provider are wrapped: timeout + retry + circuit breaker + bulkhead.
@CircuitBreaker(name = "asr", fallbackMethod = "queueForRetry")  // open circuit → don't hammer a dead provider
@Retry(name = "asr")                                             // transient 5xx/429 → backoff + jitter
@Bulkhead(name = "asr")                                          // cap concurrent ASR calls; isolate the pool
@TimeLimiter(name = "asr")
public CompletionStage<Transcript> transcribe(MediaRef ref) { ... }
```

> **Why the circuit breaker matters most for the ASR/platform calls:** when a third party is down or
> throttling us, naive retries *make it worse* — we pile on requests, exhaust our own threads
> (bringing *us* down too), and delay recovery. The breaker **fails fast** after a failure threshold,
> lets the provider recover, and tests with a trickle before fully reopening. Combined with the
> retained Kafka log ([ADR-0003](./03-adr/0003-kafka-event-broker.md)), a provider outage becomes
> "transcripts are delayed and will catch up," never "we lost work or fell over."

---

## 7. Data-flow resilience: outbox, saga, idempotency

The reliability of the *event pipeline* rests on three patterns already specified, working together:

- **Transactional Outbox** ([ADR-0006](./03-adr/0006-transactional-outbox.md)) — no lost or phantom
  events across the DB↔Kafka boundary; survives crashes.
- **Idempotent consumers** ([`05` §5](./05-api-and-event-contracts.md)) — at-least-once delivery can't
  cause double-effects.
- **Saga (choreographed)** for multi-step flows (publish: render → upload to YouTube → upload to X →
  record results). Each step emits an event the next consumes; **compensating actions** handle partial
  failure (e.g., YouTube publish succeeded but X failed → mark X for retry, don't unpublish YouTube,
  surface per-platform status, FR-AP-03). We use **choreography** (events), not a central orchestrator,
  to avoid a coordinator SPOF at our scale.

> **Why a saga instead of a distributed transaction:** there is no ACID transaction spanning our DB,
> YouTube, and X — you cannot two-phase-commit a third party. The saga accepts that reality: do each
> step, emit a fact, and define what "undo/retry" means per step. Publishing being *eventually*
> consistent across platforms (with clear per-platform status) is honest about the world; pretending it
> could be atomic would be a lie that breaks the first time X's API times out.

---

## 8. SLOs (the reliability targets we engineer + measure to)

Measured via [`09`](./09-observability.md); these are the contract.

| Domain | SLI | SLO |
| --- | --- | --- |
| API | availability | **99.9%** / 30d |
| API | p99 latency (interactive reads) | **< 300 ms** |
| Edit | optimistic local apply | **< 100 ms** (client-side) |
| Collab | edit propagation p95 | **< 250 ms** |
| Transcription | completion time | **≤ 1.5× media duration** (p95) |
| Pipeline | event success (no DLQ) | **≥ 99.5%** |
| Data | RPO / RTO | **≤ 5 min / ≤ 1 hr** ([`13`](./13-runbooks-and-dr.md)) |

Each maps back to a PRD NFR ([`01` §4](./01-product-requirements.md)) — the traceability chain from
"product need" to "engineered + measured target" closes here.

---

## 9. Capacity planning & cost-aware scaling

- **Headroom, not heroics** — target ~60–70% steady-state utilization so a spike has room before
  autoscaling catches up (scaling isn't instant; cold pods take seconds).
- **Scale-to-near-zero off-peak** — Fargate + KEDA mean idle workloads cost almost nothing
  ([`12`](./12-cost-and-local-dev.md)); we don't pay for capacity we're not using.
- **Load-test to find the knee** ([`10` §6](./10-testing-qa.md)) — know the breaking point *before*
  users do; set autoscaling ceilings below the point where a dependency (DB connections!) becomes the
  real limit.
- **The DB is the usual real ceiling** — stateless pods scale freely, but they all share Postgres.
  Connection pooling (PgBouncer), read replicas, and query discipline ([`04` §4](./04-data-model.md))
  protect it; "we scaled the app and melted the database" is the classic mistake we design against.

> **The throughline:** scale *elastically* on the *right signal* (lag for batch, connections for WS,
> RPS for API), keep *resilience patterns* between every component and its dependencies, and *measure
> against SLOs* so you know — not guess — whether the system is healthy. Build for the destination's
> scale, but let autoscaling + scale-to-zero mean you only *pay* for the load you actually have today.
