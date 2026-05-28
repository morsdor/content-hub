# ContentHub — Progress Tracker

> **How to use this file:**
> - **Done** — completed milestones (append-only, brief)
> - **In Progress** — what is actively being built right now
> - **Next** — the exact next item to start (one thing at a time)
> - **Resume Prompt** — paste this verbatim to start a productive new Claude session
>
> Update this file at the end of any session where meaningful code or config was produced.
> Claude should update it as the last action of a milestone response.

---

## Done

### 2026-05-27 — Documentation blueprint
- 14-chapter engineering docs in `docs/` (overview, PRD, system arch, data model, API contracts,
  security, Terraform, CI/CD, observability, testing, scalability, cost/local-dev, runbooks)
- 10 ADRs in `docs/03-adr/` covering every major technical decision
- Architecture SVG (`contenthub_architecture_overview.svg`)

### 2026-05-28 — Branching strategy + Claude PR review (docs/08 updated)
- `feature/*` → `develop` (ephemeral QA, ~2h, then destroy) → `main` (long-running prod canary)
- CI triggers on PRs/pushes to `develop` and `main` only
- Claude automated PR review fires on every PR targeting `develop`; not on `develop → main` (human gate)
- `docs/08-deployment-cicd.md` updated: §1 diagram, §2 YAML on: triggers, new §2.1 Claude review workflow, rewritten §6

### 2026-05-28 — Local dev stack
- `docker-compose.yml` — 13 services: Postgres, MongoDB, Kafka + Zookeeper + Schema Registry,
  LocalStack (S3), mock-oauth2 (Cognito stand-in), OTel Collector, Jaeger, Prometheus, Grafana
- `infra/local/` — OTel Collector config, Prometheus scrape config, Grafana datasource provisioning
- `Makefile` — `make dev-up / dev-down / logs / doctor / clean-force`
- `.env.local.example`, `.gitignore`
- `CLAUDE.md` (this file's companion — loaded automatically by Claude Code)

---

## In Progress

_Nothing. Docs + local stack + CI/CD strategy complete. Ready to start Spring Boot skeleton._

---

## Next

### Spring Boot modular monolith skeleton

**Scope:** One Spring Boot 3.x / Java 21 Maven multi-module app. No separate deployables yet (ADR-0009).
Module structure:

```
backend/
├── pom.xml                         (parent)
├── shared-kernel/                  domain primitives, outbox model, shared event POJOs, ArchUnit rules
├── workspace-module/               Kanban, cards, scripts (CRDT blob), WebSocket fan-out
├── media-module/                   presigned S3 upload, media_asset, EDL, render trigger
├── transcription-module/           video.uploaded consumer, mock ASR adapter, transcript write to Mongo
├── analytics-module/               OAuth grant, metrics ingestion stub
└── app/                            Spring Boot entry point, wires all modules, Flyway migrations
```

**Acceptance criteria for this task:**
- [ ] Parent pom with dependency management (Spring Boot 3.x BOM, Kafka, JPA, Flyway, OTel, Testcontainers)
- [ ] Each module has its own pom, package structure (controller/service/domain/repository/adapter)
- [ ] ArchUnit test in shared-kernel enforcing no cross-module DB access (ADR-0009)
- [ ] `app/` boots against the docker-compose stack with `SPRING_PROFILES_ACTIVE=dev`
- [ ] Flyway V1 migration applying the full Postgres schema from `docs/04-data-model.md`
- [ ] `/actuator/health` returns UP (Prometheus can scrape it)
- [ ] Outbox table exists and the shared-kernel outbox relay stub is wired (even if no-op for now)

**Key files to read before starting:**
- `docs/02-system-architecture.md` §3 (module responsibilities) and §4 (hexagonal layering)
- `docs/03-adr/0009-modular-monolith-first.md`
- `docs/03-adr/0006-transactional-outbox.md`
- `docs/04-data-model.md` (the full Postgres DDL)
- `.env.local.example` (the connection strings for the `dev` Spring profile)

---

## After Next (backlog, in order)

1. **React + TypeScript SPA skeleton** — Vite + Redux Toolkit, module structure matching backend domains,
   Cognito SDK (dev profile → mock-oauth2), WebSocket client stub
2. **Flyway V2 + Phase 0 walking skeleton** — auth flow end-to-end: login → create workspace → upload
   media → see presigned S3 URL returned → `video.uploaded` event emitted to Kafka
3. **Transcription mock pipeline** — `transcription-module` consumes `video.uploaded`, calls the mock
   ASR adapter (canned word-timed JSON), writes to MongoDB, emits `transcription.completed`
4. **Terraform module skeletons** — VPC, EKS/Fargate, RDS, MSK, S3, Cognito (infra/modules/)
5. **GitHub Actions CI** — implement `ci.yml` (§2: Snyk OSS, SonarCloud, Spring Cloud Contract,
   Pact, Schema Registry plugin, Trivy) and `claude-pr-review.yml` (§2.1, PRs to `develop` only).
   QA deploy workflow (`qa-deploy.yml`) is already written in §2.2 but **PAUSED** — enable it after
   Terraform modules (item 4) are done and OIDC + IAM role are set up in AWS.

---

## Resume Prompt

> Paste this at the start of a new Claude session to restore full context.

```
Project: ContentHub — a browser-based Descript-style content production platform (YouTube + X).
I am building this production-grade, solo, as a learning + product vehicle.

Start by reading these files in order:
  1. CLAUDE.md            ← project conventions, rules, module map, local stack
  2. PROGRESS.md          ← what is done, what is in progress, exact next task
  3. docs/02-system-architecture.md §3–4   ← service responsibilities + hexagonal layering
  4. docs/03-adr/0009-modular-monolith-first.md
  5. docs/03-adr/0006-transactional-outbox.md
  6. docs/04-data-model.md                 ← the Postgres DDL we need to implement

Then pick up PROGRESS.md §Next and implement it.

Non-negotiable rules (read CLAUDE.md for the full list):
- Modular monolith first: one Spring Boot deployable, 5 Maven modules, no cross-module DB access
- All Kafka events go through the transactional outbox (ADR-0006) — never dual-write
- Every Kafka consumer must be idempotent
- Spring profile 'dev' wires to docker-compose (run `make dev-up` if not already running)
- Same image everywhere — no prod secrets in the build, only in the environment

Settled decisions (do not re-litigate):
- Branch strategy: feature/* → develop (ephemeral QA, ~2h, terraform destroy) → main (prod canary)
- CI fires on PRs/pushes to develop and main only; not on feature branches directly
- Claude automated PR review fires on every PR targeting develop (docs/08 §2.1); NOT on develop→main
- develop→main is a human-reviewed promotion PR; prod deploy is ArgoCD canary via GitOps digest bump
- CI toolchain: Snyk OSS (SCA), SonarCloud (SAST), Spring Cloud Contract + Pact (contracts),
  Confluent Schema Registry Maven plugin (Kafka compat), Trivy (image scan)
- ArgoCD runs inside EKS as pods; monorepo deploy path at infra/deploy/envs/{qa,prod}/values.yaml

Before writing any code, show the proposed structure (pom.xml layout, package tree) and get
confirmation. Then implement fully — no placeholders, no TODOs in load-bearing paths.
```
