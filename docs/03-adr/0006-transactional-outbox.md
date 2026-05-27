# ADR-0006 — Transactional Outbox for reliable event publishing

**Status:** Accepted · 2026-05-27

## Context

Services must both **persist a state change** (e.g., "media uploaded") and **publish an event**
(`video.uploaded`) so downstream services react. Doing these as two independent operations creates the
classic **dual-write problem**: if the DB commit succeeds but the Kafka publish fails (or vice versa),
the system is now inconsistent — a transcript that never gets generated, or an event for a row that
doesn't exist. At-least-once Kafka delivery doesn't help if we never reliably *produced* the event.

## Decision

Use the **Transactional Outbox pattern**. A service writes its domain change **and** an `outbox` row
describing the event **in the same local DB transaction**. A separate **relay** (CDC via Debezium, or a
polling publisher) reads new outbox rows, publishes them to Kafka, and marks them sent. Combined with
**idempotent consumers**, this yields reliable, exactly-effectively-once event flow.

```
   ┌─ ONE Postgres transaction ─────────────┐
   │  INSERT media(...)                      │
   │  INSERT outbox(event='video.uploaded',  │   → commit
   │               payload={...}, sent=false)│
   └─────────────────────────────────────────┘
                    │
   relay (Debezium/poller) ──► Kafka video.uploaded ──► mark outbox row sent
```

## Consequences

**Positive**
- **No lost or phantom events** — the event is committed atomically with the state it describes.
- Survives crashes: an un-relayed outbox row is simply published on recovery.
- Works uniformly across services; pairs with the hexagonal outbound-port design ([`02` §4](../02-system-architecture.md)).

**Negative (accepted)**
- **At-least-once, not exactly-once** delivery — the relay may publish a row twice (crash between
  publish and mark-sent). Therefore **every consumer MUST be idempotent** (dedupe on event ID / upsert).
  This is a hard, non-negotiable rule, enforced in code review and contract tests ([`10`](../10-testing-qa.md)).
- Extra `outbox` table + relay component to operate (Debezium connector or a scheduled publisher).
- Slight publish latency (poll interval) unless using CDC.

## Alternatives considered

- **Kafka transactions / exactly-once semantics (EOS) spanning DB + Kafka.** Kafka EOS covers
  Kafka-to-Kafka, not Kafka-to-arbitrary-DB; it doesn't solve the dual-write to Postgres/Mongo.
- **Publish first, then write DB.** Just inverts the failure mode (phantom events). Rejected.
- **Best-effort publish, ignore failures.** Rejected — silent data loss in the core pipeline is
  unacceptable (NFR-A-02).
- **Listen-to-yourself (event-carried state, no DB write).** Heavier rework; outbox is the pragmatic,
  well-trodden choice.

> **This ADR is load-bearing for the whole event-driven design.** Every "emits event X" in
> [`05`](../05-api-and-event-contracts.md) means "writes to the outbox," and every "consumes event X"
> means "idempotently." If you remember one pattern from these docs, remember this one.
