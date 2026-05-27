# ADR-0009 — Start as a modular monolith; extract services on demand

**Status:** Accepted · 2026-05-27

## Context

The target architecture ([`02`](../02-system-architecture.md)) is a set of independently-deployable
microservices. But the biggest risk to *this* project is **R2 from the overview**: a solo/small team
building a 6-service distributed system before there is a single user drowns in operational overhead
(6 pipelines, 6 deployments, cross-service debugging, distributed-transaction headaches) and ships
nothing. Microservices solve *organizational* and *independent-scaling* problems that don't yet exist
at MVP. Premature decomposition is as costly as a premature monolith.

## Decision

Build ContentHub as a **modular monolith first**: one deployable Spring Boot application with the
service boundaries from [`02`](../02-system-architecture.md) enforced as **internal modules** (separate
Gradle/Maven modules, package-private boundaries, no cross-module DB access, communication via internal
interfaces that mirror the future event contracts). **Extract a module into its own service only when a
concrete trigger fires** (below). The boundaries are designed for extraction from day one, so it's a
deployment change, not a redesign.

**Extraction triggers** — extract a module to a service when *any* is true:
- It needs to **scale independently** (the transcription/render workers are the first and most obvious
  — they're CPU-heavy and queue-driven; likely extracted in Phase 1).
- It has a **different availability or deploy cadence** than the rest.
- A **team boundary** forms around it.
- Its **failure must be isolated** (bulkhead) from the interactive path.

## Consequences

**Positive**
- **Ship the MVP fast** with one pipeline, one deploy, one place to debug — local-first, cheap.
- The hexagonal modules ([`02` §4](../02-system-architecture.md)) + outbox + event contracts mean
  extraction is **mechanical**: replace an in-process call with a Kafka event, split the deployable.
- We pay microservice complexity **only when a real trigger justifies it** — complexity follows need.

**Negative (accepted)**
- Requires **discipline** to keep module boundaries clean inside one codebase (it's easy to take an
  illicit shortcut across modules). Enforced with architecture tests (ArchUnit) in CI ([`10`](../10-testing-qa.md)).
- The "full microservices" diagrams describe the *destination*, so readers must hold the
  "deploy-for-present" caveat in mind (stated in [`02` §1](../02-system-architecture.md)).
- Some rework at each extraction (wiring the event path) — but bounded by design.

## Alternatives considered

- **Full microservices from day one.** Architecturally "pure," operationally fatal for a small team
  pre-PMF. Rejected — this is the project's top risk.
- **Permanent monolith.** Fine until transcription/render need independent, queue-based scaling and
  start starving the interactive API for resources. Rejected as the *end state* but embraced as the
  *start*.

> **The mental model:** *modular monolith is microservices with the network turned off.* Same
> boundaries, same contracts, same data ownership — minus the distributed-systems tax until you've
> earned the right to pay it.
