# ADR-0003 — Apache Kafka as the async event backbone

**Status:** Accepted · 2026-05-27

## Context

The expensive operations in ContentHub — transcription, rendering, publishing, metric ingestion — are
slow, retryable, and benefit from decoupling producers from consumers. We need an async backbone that
provides: durability (a job must survive a crash), replay (reprocess after a bug fix), fan-out
(multiple consumers of one event), and **backpressure** (when transcription is overwhelmed, work
queues rather than fails). The master plan specifies **Apache Kafka**.

## Decision

Use **Apache Kafka** (Amazon **MSK** in cloud; Confluent local image in dev) as the event backbone for
all asynchronous, event-driven communication between services. Topics are the integration contract;
the catalog and schemas live in [`05`](../05-api-and-event-contracts.md).

## Consequences

**Positive**
- **Durable, replayable log** — events persist; a consumer can be rewound to reprocess after a fix.
  A traditional queue (SQS) deletes on consume; Kafka retains, which is exactly what we want for the
  transcription/render pipeline.
- **Consumer-lag = the autoscaling signal.** KEDA scales workers on partition lag — the foundation of
  our scale-to-near-zero cost story ([`11`](../11-scalability-resilience.md)).
- **Fan-out** — `transcription.completed` is consumed by both the notifier (Workspace) and potential
  future consumers (search indexer, analytics) with zero producer changes.
- **Natural backpressure** — bursts accumulate in the log; nothing topples over.

**Negative (accepted)**
- Kafka is **operationally heavy** — partitions, consumer groups, rebalancing, retention tuning. MSK
  offloads broker ops but not the conceptual complexity.
- Requires disciplined **idempotent consumers** ([ADR-0006](./0006-transactional-outbox.md)) and
  **schema governance** to avoid breaking consumers on event changes.
- Higher baseline cost than SQS in cloud — mitigated by ephemeral environments.

## Alternatives considered

- **Amazon SQS + SNS.** Simpler, cheaper, fully managed, scales to zero naturally. Rejected as the
  *primary* backbone because it lacks the durable replayable log and lag-based scaling we lean on;
  however SQS remains a fine choice for simple point-to-point tasks and is the leading candidate if
  Kafka's operational weight proves unjustified at low scale.
- **RabbitMQ.** Strong routing, but again no durable replay log and another thing to self-operate.
- **Direct synchronous calls between services.** Rejected outright — couples services, propagates
  latency, and turns a 15-minute render into a held HTTP connection.

> **The decisive factor:** replay. When (not if) we ship a transcription bug, being able to rewind the
> consumer and reprocess every affected `video.uploaded` event — instead of asking users to re-upload —
> is worth Kafka's operational cost.
