# Architecture Decision Records (ADRs)

An **ADR** captures a single significant architectural decision: the *context* that forced a choice,
the *decision* itself, and the *consequences* we accept as a result. They are short, dated, and
immutable — when a decision changes, we don't edit the old ADR, we write a new one that supersedes it.

> **Why we keep ADRs at all:** six months from now (or when a second engineer joins), the most
> expensive question is *"why on earth did we do it this way?"* Without ADRs, the answer is lost and
> people either cargo-cult the choice or thrash re-litigating it. An ADR is a five-minute write that
> saves hours of archaeology and prevents reversing a decision whose reasons you've simply forgotten.
> This is the cheapest insurance in the whole project.

## Format

Each ADR uses: **Status · Context · Decision · Consequences (positive / negative) · Alternatives
considered**. We deliberately include *negative* consequences — a decision with no downsides is a
decision you haven't understood yet.

## Status values

`Proposed` → `Accepted` → (later) `Superseded by ADR-XXXX` / `Deprecated`.

## Index

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](./0001-eks-over-ecs.md) | Use Amazon EKS (Kubernetes), not ECS, for orchestration | Accepted |
| [0002](./0002-polyglot-postgres-and-mongo.md) | Polyglot persistence: PostgreSQL + MongoDB | Accepted |
| [0003](./0003-kafka-event-broker.md) | Apache Kafka as the async event backbone | Accepted |
| [0004](./0004-cognito-for-auth.md) | Amazon Cognito for identity & auth | Accepted |
| [0005](./0005-third-party-asr.md) | Integrate a 3rd-party ASR provider, don't build one | Accepted |
| [0006](./0006-transactional-outbox.md) | Transactional Outbox for reliable event publishing | Accepted |
| [0007](./0007-spring-cloud-gateway.md) | Spring Cloud Gateway as the API edge | Accepted |
| [0008](./0008-crdt-for-collaboration.md) | CRDTs for collaborative + offline editing | Accepted |
| [0009](./0009-modular-monolith-first.md) | Start as a modular monolith; extract services on demand | Accepted |
| [0010](./0010-fargate-compute.md) | AWS Fargate for EKS compute (no managed node groups) | Accepted |
