# 09 — Observability

**Audience:** builders + on-call. Observability is the ability to ask *new* questions about your
running system without shipping new code. For a small team operating a distributed system, it is not
optional — it is the difference between "we got paged, found the cause in 4 minutes, rolled back" and
"the system is down and we have no idea why." FR-OB-02 makes telemetry a **day-one** requirement, not a
later phase.

> **The framing that matters:** *monitoring* tells you **whether** something is wrong (a dashboard, an
> alert). *Observability* lets you discover **why** without a redeploy (high-cardinality traces, logs,
> metrics you can slice arbitrarily). In a microservices + event-driven system, a request fans out
> across services and Kafka; without correlated telemetry, debugging is archaeology. We instrument so
> that any user-visible symptom can be traced to its root cause through one correlated story.

---

## 1. The three pillars (and the tool for each)

| Pillar | Question it answers | Our tool | Master-plan mapping |
| --- | --- | --- | --- |
| **Logs** | "What exactly happened, in detail, at this moment?" | **Splunk** | "Splunk — centralized tracing for all Spring Boot errors" |
| **Metrics** | "What's the aggregate health/trend?" | **Datadog** + Prometheus; **Grafana** for BI | "Datadog — EKS pod health + API latency", "Grafana — usage metrics" |
| **Traces** | "Where did this *one request* spend time / fail, across services?" | **Datadog APM** (OTel) | "Datadog APM" |

These are not three silos — they are **one correlated story** stitched by a shared **`traceId`**
([`05` §4](./05-api-and-event-contracts.md), NFR-O-03): a log line, a metric exemplar, and a trace span
all carry the same id, so you pivot from "error in Splunk" → "the trace in Datadog" → "the spike on the
dashboard" in seconds.

---

## 2. Instrumentation: OpenTelemetry as the vendor-neutral foundation

We instrument with **OpenTelemetry (OTel)** — a single standard SDK + collector — rather than
vendor-specific agents wired into the code.

```
 Service (Spring Boot + OTel SDK)
   │  emits logs + metrics + traces (OTLP), all stamped with traceId/spanId
   ▼
 OpenTelemetry Collector (a DaemonSet/sidecar-compatible deployment)
   ├──► Splunk      (logs)            via HEC
   ├──► Datadog     (traces + infra metrics)
   └──► Prometheus  (metrics) ──► Grafana (BI dashboards)
```

> **Why OTel instead of dropping the Datadog/Splunk agents directly in the app:** vendor lock-in at the
> *instrumentation* layer is expensive and sticky — re-instrumenting hundreds of spans to switch
> backends is brutal. With OTel, the **code emits once** in a standard format and the **Collector**
> routes to whichever backend(s) we choose (and can fan out to several). Changing observability vendors
> becomes a Collector config change, not a code change across every service. Same principle as the ASR
> adapter ([ADR-0005](./03-adr/0005-third-party-asr.md)): isolate the vendor behind a standard seam.

**Auto + manual instrumentation:** the OTel Java agent auto-instruments HTTP, JDBC, and Kafka clients
(so the transcription flow in [`02` §7](./02-system-architecture.md) is traced end-to-end for free);
we add **manual spans** around domain-meaningful work (EDL apply, render orchestration, ASR call) and
**business events** (video published, transcription completed) for the BI layer.

---

## 3. Logs — Splunk

- **Structured JSON logs**, never free-text. Every line: `{ timestamp, level, service, traceId,
  workspaceId, userId?, msg, ...fields }`. Structured logs are *queryable* (`status=500 AND
  service=media`), free-text logs are not.
- **Shipped via HEC** (HTTP Event Collector) through the OTel Collector → Splunk indexes.
- **Correlation** — the `traceId` on every line is the join key to the Datadog trace and to other
  services' logs for the same request. One `traceId` search reconstructs a request's entire journey
  across services and Kafka.
- **Secret scrubbing** — a logging filter redacts tokens/PII *before* emission ([`06`](./06-security-architecture.md));
  logging a secret is a security incident, so this is enforced, not hoped.
- **Retention tiers** — hot (searchable) for ~30 days, archived to S3 (cheap) beyond; security/audit
  logs retained longer.

> **Why structured-from-day-one and not "we'll add fields later":** you cannot retroactively query logs
> you didn't structure. The first time you're debugging a 2 a.m. incident and can type
> `traceId=req_9f1c | transaction` and watch the whole request assemble itself across five services —
> that's the moment this discipline pays for itself a hundred times over.

---

## 4. Metrics & APM — Datadog

**Two complementary method-frames** tell us where to look:

- **RED** (for request-driven services: Gateway, Workspace, Analytics) — **R**ate, **E**rrors,
  **D**uration. "How many requests, how many failing, how slow?"
- **USE** (for resources: pods, DB, Kafka) — **U**tilization, **S**aturation, **E**rrors. "How busy,
  how queued-up, how broken?"

| Layer | Key metrics |
| --- | --- |
| Edge / API | request rate, 4xx/5xx rate, p50/p95/p99 latency per route (RED) |
| Pods (EKS/Fargate) | CPU/mem utilization + saturation, restarts, OOMKills (USE) |
| **Kafka (the pipeline's pulse)** | **consumer lag per group** (drives autoscaling *and* alerting), throughput, partition skew |
| Databases | connections, query latency, replication lag, lock waits (USE) |
| Async jobs | transcription/render queue depth, processing time, success/failure, **DLQ depth** |
| Business (→ Grafana) | videos published, transcription minutes used, active members, MAU |

- **APM traces** — distributed traces show the waterfall of a request across Gateway → service → DB →
  Kafka, with the slow span highlighted. This is how you find *which hop* is slow, not just *that* it's
  slow.
- **Deploy markers** — every deploy ([`08` §8](./08-deployment-cicd.md)) annotates Datadog graphs, so
  "latency jumped at 14:32" instantly correlates to "the 14:31 deploy."
- **Fargate-compatible integration** — since there are no nodes to run a DaemonSet on
  ([ADR-0010](./03-adr/0010-fargate-compute.md)), the Datadog agent runs in the Fargate-aware mode.

> **Consumer lag is the single most important metric in this system.** It is simultaneously the
> **autoscaling signal** (KEDA scales transcription/render on it, [`11`](./11-scalability-resilience.md))
> *and* the **health signal** (rising lag = consumers can't keep up = users waiting longer for
> transcripts). One number tells you both "are we keeping up?" and "should we add workers?"

---

## 5. Business intelligence — Grafana

Per the master plan, **Grafana queries Postgres/Mongo for platform usage metrics**. This is the
**operator-facing** layer (FR-OB-01/03), distinct from the engineering APM:

- **Data sources:** Postgres (workspaces, cards, usage_meter, subscriptions) + Mongo (aggregated
  platform metrics) + Prometheus (system metrics).
- **Dashboards:** content throughput (videos/week), transcription/render minutes consumed vs. plan
  quota, active workspaces/members, cross-platform engagement funnel (FR-AP-05), and **cost-vs-usage**
  (FinOps, [`12`](./12-cost-and-local-dev.md)).
- **Why Grafana here and Datadog for APM:** Datadog excels at real-time system/APM telemetry; Grafana
  excels at flexible querying over our *own databases* for product/business KPIs. Using each for its
  strength avoids forcing business queries through an APM tool not built for them.

---

## 6. SLIs, SLOs, and error budgets

We define **SLIs** (what we measure), set **SLOs** (the target), and derive an **error budget** (how
much failure we're allowed) — the contract between reliability and shipping speed. Full SLO table in
[`11-scalability-resilience.md`](./11-scalability-resilience.md); the observability job is to *measure*
them.

| SLI | SLO | Error budget |
| --- | --- | --- |
| API availability (non-5xx / total) | 99.9% / 30d | ~43 min/month of "down" |
| API p99 latency (interactive reads) | < 300 ms | — |
| Transcription completion time | ≤ 1.5× media duration (p95) | — |
| Pipeline success (no DLQ) | ≥ 99.5% of events | 0.5% may need replay |

> **What the error budget actually buys you:** it turns "should we ship this risky change?" from an
> argument into arithmetic. If the month's budget is intact, ship — you have room. If you've burned it
> (too many incidents), the budget *forces a freeze* on feature work to spend on reliability. It aligns
> the natural tension between "move fast" and "stay up" with a number both sides agree on, instead of
> vibes.

---

## 7. Alerting & on-call

**Alert on symptoms users feel, not on every metric twitch.** The cardinal sin of alerting is noise —
an on-call that's paged constantly stops reading pages.

| Alert (symptom) | Condition | Severity |
| --- | --- | --- |
| API error-budget burn | fast-burn (e.g., 2% errors over 5m) | **page** |
| API latency SLO breach | p99 > 300ms sustained 10m | page |
| Kafka consumer lag runaway | lag > threshold & rising 15m (not draining) | page |
| DLQ message arrived | any message in any `*.DLQ` | page (data at risk, [`05`](./05-api-and-event-contracts.md)) |
| DB saturation | connections/CPU near limit | page |
| Pod crashloop | restarts > N in 10m | page |
| Cost guardrail | env spend > budget | ticket/Slack ([`12`](./12-cost-and-local-dev.md)) |
| Cert/secret expiry approaching | < 14d | ticket |

- **Multi-window burn-rate alerts** for SLOs (fast-burn → page now; slow-burn → ticket) — catches both
  "outage now" and "slowly bleeding the budget."
- **Runbook link on every alert** — each page links the relevant runbook in
  [`13-runbooks-and-dr.md`](./13-runbooks-and-dr.md). A page without a runbook is an unfinished alert.
- **Solo-team reality:** with one operator, alert *discipline* is survival — only page on what truly
  needs a human *now*; everything else is a ticket reviewed in the morning. Tune aggressively; a noisy
  pager is a broken pager.

---

## 8. How the pillars solve a real incident (worked example)

> **Symptom:** users report "transcripts are taking forever."
>
> 1. **Metric (Datadog):** `transcription` consumer **lag** is climbing and not draining → consumers
>    can't keep up. USE says: saturation high.
> 2. **Metric:** transcription pod CPU is maxed but replica count is stuck — KEDA *should* have scaled.
>    Check KEDA/HPA events.
> 3. **Trace (Datadog APM):** individual transcription spans show the **ASR provider call** p95 tripled
>    → the bottleneck is the external provider, not our code.
> 4. **Log (Splunk):** `service=transcription level=WARN` shows ASR `429 rate-limited` responses with
>    the same `traceId`s → we're being throttled by the provider.
> 5. **Resolution:** back-off + spread load; if sustained, fail over to the secondary ASR adapter
>    ([ADR-0005](./03-adr/0005-third-party-asr.md)). The retained Kafka log means **no events are
>    lost** — they drain once capacity returns ([ADR-0003](./03-adr/0003-kafka-event-broker.md)).
>
> Three pillars, one `traceId`, root cause in minutes — *that* is observability earning its keep.
