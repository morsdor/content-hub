# ContentHub — Engineering Documentation

> A browser-based, Descript-style content production platform for operating a multi-channel
> YouTube + X (Twitter) content business. Upload raw media, transcribe it, edit the video by
> editing its transcript, collaborate live, and publish + measure across platforms — all from
> the browser.

This `docs/` tree is the **source of truth** for what ContentHub is, how it is built, how it is
operated, and *why* each decision was made. It is written to two audiences at once:

- **Builders** (you, and any engineer who joins): deep technical detail with the reasoning behind
  every choice, so the docs teach as well as specify.
- **Stakeholders** (you-as-founder, investors, partners): a self-contained business + risk view
  in [`00-overview.md`](./00-overview.md).

---

## How to read this

| If you are… | Start here | Then read |
| --- | --- | --- |
| A stakeholder / investor | [`00-overview.md`](./00-overview.md) | §"Business case", §"Cost posture", §"Risk register" |
| A new engineer, day one | [`00-overview.md`](./00-overview.md) → [`02-system-architecture.md`](./02-system-architecture.md) | [`12-cost-and-local-dev.md`](./12-cost-and-local-dev.md) to get a local stack running |
| Building a feature | [`01-product-requirements.md`](./01-product-requirements.md) | [`04-data-model.md`](./04-data-model.md), [`05-api-and-event-contracts.md`](./05-api-and-event-contracts.md) |
| Doing infra / DevOps | [`07-infrastructure-terraform.md`](./07-infrastructure-terraform.md) | [`08-deployment-cicd.md`](./08-deployment-cicd.md), [`06-security-architecture.md`](./06-security-architecture.md) |
| On call | [`13-runbooks-and-dr.md`](./13-runbooks-and-dr.md) | [`09-observability.md`](./09-observability.md) |

---

## Document index

| # | Document | What it answers |
| --- | --- | --- |
| 00 | [Overview](./00-overview.md) | What are we building, for whom, why, at what cost, against what risks? |
| 01 | [Product Requirements (PRD)](./01-product-requirements.md) | Personas, journeys, functional + non-functional requirements, acceptance criteria, MVP scope. |
| 02 | [System Architecture](./02-system-architecture.md) | C4 views, the 4-tier topology, service boundaries, sync vs async, end-to-end flows. |
| 03 | [Architecture Decision Records](./03-adr/) | The *why* behind the big choices, each as a dated, reviewable record. |
| 04 | [Data Model](./04-data-model.md) | Postgres DDL, Mongo schemas, who owns what data, indexing, the transcript timing model. |
| 05 | [API & Event Contracts](./05-api-and-event-contracts.md) | REST + WebSocket contracts, the Kafka topic catalog, schemas, idempotency, DLQs. |
| 06 | [Security Architecture](./06-security-architecture.md) | The security-group firewall chain, auth, IAM/IRSA, secrets, encryption, threat model. |
| 07 | [Infrastructure (Terraform)](./07-infrastructure-terraform.md) | Module layout, remote state, env composition, the apply/destroy ephemeral workflow. |
| 08 | [Deployment & CI/CD](./08-deployment-cicd.md) | Pipeline stages, GitOps, progressive delivery, DB migrations, rollback. |
| 09 | [Observability](./09-observability.md) | Logs/metrics/traces, OpenTelemetry, Splunk, Datadog, Grafana, SLOs & error budgets. |
| 10 | [Testing & QA](./10-testing-qa.md) | Test pyramid, Cucumber BDD, contract tests, Testcontainers, BlazeMeter load tests. |
| 11 | [Scalability & Resilience](./11-scalability-resilience.md) | HPA/KEDA/Karpenter, the transcription bottleneck, circuit breakers, saga/outbox. |
| 12 | [Cost & Local Dev](./12-cost-and-local-dev.md) | $0 local emulation vs. pennies-per-hour ephemeral AWS, FinOps guardrails. |
| 13 | [Runbooks & DR](./13-runbooks-and-dr.md) | Incident playbooks, RTO/RPO, backup/restore, postmortems. |

---

## The system in one diagram

```
 Browser (React + Redux + TS, Service Worker/IndexedDB, WebSocket, Cognito SDK)
        │  HTTPS 443 / WSS
        ▼
 ┌─────────────────────────── ALB-SG (public edge, 80/443 only) ───────────────────────────┐
 │  Application Load Balancer                                                                │
 └───────────────────────────────────────────┬──────────────────────────────────────────────┘
                                              ▼
 ┌──────────────────── EKS-Node-SG (private; Kubernetes on Fargate) ──────────────────────────┐
 │  Spring Cloud API Gateway  (JWT validation · routing · rate limit)                          │
 │        ├── Workspace Service     (Kanban, scripts, collab fan-out)                          │
 │        ├── Media Service         (upload, S3, render orchestration)                         │
 │        ├── Analytics Service     (YouTube/X metrics, publishing)                            │
 │        └── AI Transcription Svc  (audio → transcript JSON)                                  │
 │                                                                                             │
 │  Apache Kafka (MSK)  topics: video.uploaded · transcription.completed · render.finished …   │
 └───────────────────────────────────────────┬──────────────────────────────────────────────┘
                                              ▼
 ┌──────────────────────────── DB-SG (private; data tier) ────────────────────────────────────┐
 │  PostgreSQL (RDS) :5432     MongoDB (DocumentDB) :27017     S3 (raw + rendered media)        │
 └─────────────────────────────────────────────────────────────────────────────────────────────┘

 Cross-cutting:  Terraform (all infra)  ·  Splunk (logs)  ·  Datadog (APM/infra)  ·  Grafana (BI)
 External:       YouTube API · X API  ·  BlazeMeter (load) · Cucumber (BDD CI)
```

Full, annotated versions of this diagram live in [`02-system-architecture.md`](./02-system-architecture.md).

---

## Conventions used in these docs

- **Rationale callouts** — blocks marked *"Why:"* explain the reasoning, not just the decision. They
  are the part worth reading twice.
- **Tradeoff tables** — where a choice has real downsides, they are stated plainly. No decision here
  is free.
- **`MUST` / `SHOULD` / `MAY`** follow [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) when used in
  requirements.
- **Phase tags** — `[MVP]`, `[v1]`, `[later]` mark when a capability is expected to land. Building
  everything at once is how solo projects die; sequencing is a feature.

---

## Status

| Field | Value |
| --- | --- |
| Document set version | 1.0 (initial blueprint) |
| Last updated | 2026-05-27 |
| Code scaffolding | **Not yet** — produced after this blueprint is approved (Terraform modules, docker-compose local stack, Spring Boot service skeletons, CI). |
| Source materials | `ContentHub — master plan` + `contenthub_architecture_overview.svg` (repo root) |
