# 00 — Overview

**Audience:** stakeholders, investors, and any engineer who wants the whole picture in ten minutes
before diving into the technical chapters.

**Status:** Blueprint v1.0 · 2026-05-27

---

## 1. The one-sentence pitch

> **ContentHub is a browser-based "Descript for content businesses"**: a single workspace where a
> creator or a small media team uploads raw footage, edits the video by editing its
> auto-generated transcript, collaborates in real time, and publishes + measures performance
> across YouTube and X — without ever opening a desktop video editor.

## 2. The problem we are solving

A modern content operation — even a one-person "creator empire" — is a logistics nightmare stitched
together from disconnected tools:

- A **Kanban board** (Trello/Notion) to track ideas → scripting → filming → editing → published.
- A **script doc** (Google Docs) that lives separately from the footage.
- A **heavyweight desktop editor** (Premiere/Final Cut) with a brutal learning curve, where a
  10-minute cut takes hours.
- A pile of **platform dashboards** (YouTube Studio, X Analytics) that never agree and must be
  copy-pasted into a spreadsheet to see the whole business.
- A **publishing process** that is manual, error-prone, and timezone-dependent.

Each handoff between these tools loses context, time, and money. The expensive, scarce resource in a
content business is **creative throughput** — videos shipped per week — and today it is throttled by
tooling friction, not by ideas.

## 3. The solution

ContentHub collapses that toolchain into one collaborative, browser-native product built around a
single insight that Descript pioneered and proved at scale:

> **Editing video should feel like editing a document.** Transcribe the audio, show the words, and
> let deleting a sentence delete the corresponding footage. Most "editing" is really *removing the
> bad takes and the rambling* — a text operation, not a timeline operation.

Around that core editor we wrap the rest of the content lifecycle:

| Capability | What it replaces | The 10x improvement |
| --- | --- | --- |
| **Creator Workspace** (Kanban + offline script editor) | Trello + Google Docs | Scripts and tasks live *next to* the footage they describe. |
| **Text-based AI media engine** | Premiere / Final Cut | A rough cut in minutes by editing text, not frames. |
| **Multi-platform analytics & publishing** | YouTube Studio + X Analytics + spreadsheets | One funnel view; one-click scheduled publish to both. |
| **Real-time collaboration** | "Can you send me the latest file?" | Google-Docs-style live cursors; no file versions. |
| **Enterprise observability** | Hope | The *operator* of the business can see system health and content KPIs in one place. |

## 4. Who it is for

| Persona | Description | Primary jobs-to-be-done |
| --- | --- | --- |
| **The Operator (you)** | Runs a YouTube + X content business; may be solo or lead a tiny team. | See the whole pipeline; publish reliably; understand what's working. |
| **The Editor** | Turns raw footage into a finished cut. | Fast text-based editing; no Premiere; collaborate without file chaos. |
| **The Writer / Scripter** | Produces scripts and outlines. | Offline-capable writing that syncs; lives beside the footage. |
| **The Analyst (often also the Operator)** | Decides what to make next. | Cross-platform metrics, trends, and engagement in one funnel. |

> **Why call out personas in an "overview"?** Because every scope and prioritization argument later in
> these docs resolves to *"which persona, doing which job, is blocked?"* If a proposed feature serves
> none of these four, it is out of scope. This is the cheapest decision-making tool we have.

## 5. Product principles

These are the tie-breakers. When two designs are otherwise equal, the one that better honors these
wins.

1. **Browser-native, no install.** The entire product runs in a tab. This is the whole differentiator
   versus desktop editors; we never compromise it.
2. **The transcript is the source of truth for the cut.** Edits flow text → timeline, never the
   reverse. This constraint keeps the core model simple and is why the product feels magical.
3. **Offline-tolerant where creators actually work.** Writing happens on planes and in cafés. Scripts
   MUST survive a dropped connection and sync cleanly on return. (Editing rendered video does not need
   to be offline — see the PRD for where we draw this line and why.)
4. **Async by default, sync only where it pays.** Heavy work (transcription, rendering) is fire-and-
   forget with eventual notification. We only hold a synchronous request open when the user is
   literally waiting and nothing else will do.
5. **Cost is a design constraint, not an afterthought.** The architecture is explicitly designed so
   that 95% of development costs **$0** (local emulation) and cloud is spun up ephemerally. See §8.
6. **Operable by a small team.** Every component must be observable, recoverable, and explainable.
   Cleverness that can't be debugged at 2 a.m. is a liability.

## 6. Scope at a glance (and what we are *not* building)

**In scope** (detailed in [`01-product-requirements.md`](./01-product-requirements.md)):
the five core features above, on the architecture in [`02-system-architecture.md`](./02-system-architecture.md).

**Explicitly out of scope** (at least for v1) — and *why*, because saying no is the job:

| Not building | Why not |
| --- | --- |
| A frame-accurate, multi-track timeline editor (Premiere parity) | Directly contradicts Principle #2. The whole bet is that text editing is *enough* for the 80% case. |
| Our own ML transcription model | Undifferentiated heavy lifting. We integrate a managed/3rd-party ASR engine and put our value in the *editing experience*. See [ADR-0005](./03-adr/). |
| Live streaming / real-time broadcast | Different product, different infrastructure (low-latency media), different buyer. |
| Mobile native apps | The PWA covers "review on phone." Native editing on mobile is a v3+ conversation. |
| Platforms beyond YouTube + X (TikTok, IG, LinkedIn) | Each integration is real cost. We prove the model on two, architect for more (see the Analytics Service design). |

## 7. Business case (illustrative)

> **These numbers are planning assumptions, not promises** — stated explicitly so they can be
> challenged and revised. The point is to show the unit economics *work* and that the cost
> architecture keeps us solvent while we find product-market fit.

**Revenue model:** seat-based SaaS subscription, with usage-metered transcription/render minutes
above a plan allowance.

| Tier | Target user | Price (illustrative) | Included |
| --- | --- | --- | --- |
| Solo | Single creator | $29 / mo | 1 seat, 300 transcription min, 60 render min |
| Team | Small media team | $99 / mo | 5 seats, 1,500 min, 300 render min |
| Studio | Multi-channel operator | $299 / mo | 15 seats, pooled minutes, priority render |

**Cost of goods sold (COGS) per active workspace** is dominated by three variable costs:
transcription minutes (3rd-party ASR), render compute (the expensive one), and media storage/egress
(S3 + CDN). The architecture's job is to keep **fixed** cloud cost near-zero until paying users exist
(see §8), so that COGS scales *with* revenue rather than ahead of it.

**Why this matters to the architecture:** the event-driven, autoscaling design means idle workspaces
cost almost nothing (services scale toward zero; storage is the only floor). A subscriber who uploads
nothing this month costs us cents. This is the financial reason behind the async/Kafka design, not
just an engineering aesthetic.

## 8. Cost posture — the headline for any budget-holder

Running EKS + Kafka + Datadog 24/7 is **prohibitively expensive for a pre-revenue product** — easily
thousands of dollars a month for an idle cluster. ContentHub's development strategy is built to avoid
that trap entirely. (Full detail in [`12-cost-and-local-dev.md`](./12-cost-and-local-dev.md).)

| Environment | When it runs | Monthly cost | How |
| --- | --- | --- | --- |
| **Local emulation** | ~95% of all development | **$0** | Docker Compose / Minikube run the pods locally; Confluent local Kafka image; community Grafana; LocalStack for AWS APIs. |
| **Ephemeral AWS** | Only for production-fidelity testing & demos | **Pennies per hour** | `terraform apply` stands up the *entire* VPC/EKS/SG/data stack, run the tests, then `terraform destroy` removes every billable asset. |
| **Production** | Once there are paying users | Scales with revenue | Autoscaling to near-zero when idle; COGS tracks usage. |

> **Why this is the single most important strategic decision in the whole project:** it decouples
> *learning to build a production system* from *paying for a production system*. A solo builder can
> develop, integration-test, and load-test a genuine microservices-on-Kubernetes platform for
> essentially the cost of electricity, and only pay AWS for the minutes they actually need the cloud.

## 9. Success metrics

**Product (does it create value?)**

- **Time-to-rough-cut**: median minutes from upload to a first text-edited cut. Target: < 15 min for a
  10-min source video. *This is the north-star — it is the entire value proposition, measured.*
- **Weekly videos published per active workspace** (creative throughput — the thing we exist to raise).
- **Activation**: % of new workspaces that publish at least one piece of content within 14 days.
- **Collaboration depth**: % of edits made in sessions with ≥2 concurrent users.

**System (can we afford to operate it, reliably?)** — formalized as SLOs in [`11-scalability-resilience.md`](./11-scalability-resilience.md):

- API availability ≥ 99.9%; p99 read latency < 300 ms.
- Transcription completes within 1.5× media duration (p95).
- Zero data-loss objective (RPO ≤ 5 min) on user content and scripts.

## 10. Risk register

Honest risk accounting up front. Each risk has an owner, a likelihood/impact read, and the
mitigation already baked into the architecture (with a pointer to where it's handled).

| # | Risk | Likelihood | Impact | Mitigation (and where) |
| --- | --- | --- | --- | --- |
| R1 | **Cloud cost runs away** before revenue | Med | High | Ephemeral-infra strategy; budget alarms; scale-to-near-zero. ([`12`](./12-cost-and-local-dev.md)) |
| R2 | **Microservices over-engineering** for a solo build slows everything to a crawl | High | High | Phased build: a *modular monolith → extract services* path is offered; MVP runs few services. ([ADR-0009](./03-adr/), [`01`](./01-product-requirements.md)) |
| R3 | **Render/transcription cost & latency** dominate COGS and UX | High | High | Async pipeline, queue-based autoscaling (KEDA on Kafka lag), per-plan quotas. ([`11`](./11-scalability-resilience.md)) |
| R4 | **3rd-party platform API limits** (YouTube/X quotas, ToS changes) | Med | High | Quota-aware scheduling, backoff, the Anti-Corruption Layer pattern isolates each integration. ([`05`](./05-api-and-event-contracts.md)) |
| R5 | **Real-time collaboration correctness** (lost edits, conflicting cursors) | Med | Med | Well-trodden CRDT/OT approach scoped narrowly to scripts; offline sync via outbox + last-write reconciliation. ([`02`](./02-system-architecture.md), [`04`](./04-data-model.md)) |
| R6 | **Security misconfiguration** exposes user media or PII | Low | Critical | Least-privilege SG chain, IRSA, encryption everywhere, no public data tier, threat model. ([`06`](./06-security-architecture.md)) |
| R7 | **Solo bus-factor / operational load** | High | Med | Everything is IaC + runbooks + strong observability so the system is recoverable and explainable. ([`07`](./07-infrastructure-terraform.md), [`13`](./13-runbooks-and-dr.md)) |
| R8 | **Data loss** of user content | Low | Critical | Multi-AZ data tier, automated backups, tested restore, S3 versioning. ([`13`](./13-runbooks-and-dr.md)) |

> **R2 is the one to internalize.** The biggest threat to *this specific project* is not AWS or
> Kafka — it is building a 12-service distributed system before there is a single user. The docs that
> follow describe the full production target, but the PRD's phasing and [ADR-0009](./03-adr/)
> deliberately give you a path that starts small and earns its complexity. Read the full target as the
> *destination*, not the *first commit*.

## 11. Delivery phasing (the honest roadmap)

| Phase | Goal | What ships | What's deferred |
| --- | --- | --- | --- |
| **Phase 0 — Walking skeleton** | Prove the spine end-to-end | Auth (Cognito) → upload → S3 → transcribe → see transcript in browser. Few services, local-first. | Kanban, publishing, collab, analytics. |
| **Phase 1 — The core loop [MVP]** | The magic moment | Text-based editing of the transcript → render → download. Workspace + Media + Transcription. | Multi-platform publish, live collab. |
| **Phase 2 — The business** | Make it a content *operation* | Kanban workspace, multi-platform publish + scheduling, analytics funnel. | Advanced collab, BI dashboards. |
| **Phase 3 — The team** | Multiplayer + scale | Real-time collaboration, observability/BI dashboards, load-tested for concurrency. | — |
| **Phase 4 — Hardening** | Production-grade | Full DR, chaos testing, FinOps guardrails, SLO enforcement. | — |

Each phase is independently demoable and independently valuable. We never have a six-month gap with
nothing to show.

## 12. Where to go next

- Want the **full feature spec**? → [`01-product-requirements.md`](./01-product-requirements.md)
- Want to understand **how it all fits together**? → [`02-system-architecture.md`](./02-system-architecture.md)
- Want to **run it locally today**? → [`12-cost-and-local-dev.md`](./12-cost-and-local-dev.md)
- Want the **reasoning behind the big bets**? → [`03-adr/`](./03-adr/)
