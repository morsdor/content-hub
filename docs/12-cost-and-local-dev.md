# 12 — Cost & Local Development

**Audience:** builders + budget-holder (you). This chapter is the financial and developer-experience
backbone of the project: how to build a genuine production-grade microservices platform **without
bleeding money**, and how to get a full stack running on a laptop in under 30 minutes (NFR-O-04,
NFR-C-01).

> **The strategic insight worth repeating from the overview:** the single biggest threat to *this*
> project isn't a technical one — it's burning thousands of dollars a month running an idle
> EKS+Kafka+Datadog cluster before there's a single paying user (R1, R2). The entire dev strategy below
> exists to **decouple learning-to-build a production system from paying-for one**. You get the full
> experience for the cost of electricity, and rent the cloud by the hour only when you genuinely need
> it.

---

## 1. The two-environment strategy (the core idea)

```
 ┌──────────────────────────────┐         ┌────────────────────────────────────────┐
 │  LOCAL EMULATION  ($0/mo)     │         │  EPHEMERAL AWS  (pennies/hour, then $0)  │
 │  ~95% of all development      │         │  production-fidelity test windows         │
 │                               │         │                                          │
 │  Docker Compose / Minikube    │  same   │  terraform apply → real VPC/EKS/MSK/RDS  │
 │  Confluent local Kafka        │  images │  run BlazeMeter + Cucumber               │
 │  Postgres + Mongo containers  │  & charts│  capture results                         │
 │  LocalStack (S3/Cognito/…)    │ ───────►│  terraform destroy → cost stops          │
 │  community Grafana            │         │                                          │
 └──────────────────────────────┘         └────────────────────────────────────────┘
        the inner loop                          the production-fidelity loop
```

The discipline that makes this safe: **same container images, same Helm charts, same code everywhere**
— only configuration (Spring profiles `dev`/`qa`/`prod`) and scale differ
([`02` §9](./02-system-architecture.md)). A bug found locally is the *same binary* that runs in prod.

---

## 2. The local stack ($0/month — where you live 95% of the time)

Everything an AWS service does in prod has a local stand-in, so the whole architecture runs offline.

| Prod (AWS) | Local equivalent | How |
| --- | --- | --- |
| EKS / Fargate | **Minikube** (or kind / Docker Desktop K8s) | same Helm charts apply to a local cluster — true K8s parity ([ADR-0001](./03-adr/0001-eks-over-ecs.md)) |
| Amazon MSK | **Confluent local Kafka** image | `docker compose up` a single-broker Kafka + Schema Registry |
| RDS PostgreSQL | **postgres** container | identical engine; Flyway migrations apply unchanged |
| DocumentDB | **mongo** container | verify DocumentDB-specific quirks in ephemeral QA ([ADR-0002](./03-adr/0002-polyglot-postgres-and-mongo.md)) |
| S3 | **LocalStack** | S3 API on localhost; presigned URLs work locally |
| Cognito | **LocalStack** / mock OIDC issuer | the Gateway validates tokens from a local issuer in the `dev` profile |
| ASR provider | **mock adapter** | returns a canned word-timed transcript for sample clips ([`10` §9](./10-testing-qa.md)) |
| Datadog | **OTel Collector → console / Jaeger** | traces locally without the SaaS |
| Grafana | **community Grafana** container | the master plan's "$0 community Grafana" |
| Splunk | local file/stdout JSON logs | structured logs readable without the SaaS |

**Two local modes, by need:**

- **Docker Compose** (fastest inner loop) — for working on one or two services + their dependencies.
  `docker compose up` and you're coding. This is the default daily driver.
- **Minikube** (K8s parity) — for testing the *Kubernetes* concerns (Helm charts, HPA/KEDA behavior,
  network policies, service accounts) before they hit the cloud. Slower to start; used when the
  orchestration itself is what you're changing.

> **Why bother with Minikube at all if Compose is faster?** Because the things that break in
> production are often K8s-specific — a misconfigured readiness probe, a Helm value, a network policy,
> a KEDA scaler that doesn't trigger. Catching those locally on real Kubernetes (Minikube) is far
> cheaper than discovering them in the ephemeral cloud env. Compose for *app logic*, Minikube for
> *platform behavior*. Use the lighter tool until the heavier one's fidelity is what you need.

---

## 3. The 30-minute onboarding (NFR-O-04)

The target developer experience — the whole local stack from a clean machine:

```bash
# Prereqs: Docker, a JDK, Node, kubectl, minikube, terraform  (a `make doctor` checks these)
git clone <repo> && cd content-hub
make dev-up            # docker compose: kafka+registry, postgres, mongo, localstack, grafana, otel
make migrate           # Flyway applies the schema to local postgres
make seed              # deterministic fixtures: a demo workspace, user, sample media (10 §9)
make run               # start the (modular-monolith / services) app against the local stack
# open http://localhost:3000 — sign in with the seeded user, upload a sample clip, see a transcript
```

> A `Makefile` (or `Taskfile`) wrapping these is itself a deliverable in the scaffolding pass — the
> onboarding command *is* the spec for "what local needs." If `make dev-up` doesn't bring up the whole
> world, the local story is broken, and that's a bug we fix immediately. The inner loop is sacred.

---

## 4. The ephemeral AWS loop (pennies/hour — production fidelity on demand)

When you need to test against *real* infrastructure (real ALB behavior, real MSK, real autoscaling,
real DocumentDB quirks, real load):

```bash
cd infra/envs/qa
terraform apply -auto-approve     # ~10–20 min: full VPC/EKS/SG/RDS/DocumentDB/MSK stack
# CD deploys the proven images (08); run the heavy tests:
#   - BlazeMeter: 10k WebSocket load test (10 §6)
#   - Cucumber: critical-journey BDD on a real stack (10 §5)
#   - chaos game-day / AZ-failover drill (10 §7)
terraform destroy -auto-approve   # tears down EVERY billable asset → spend returns to ~$0
```

**Cost math (illustrative):** a QA stack running for a **3-hour** test window costs roughly the
per-hour sum of EKS control plane + a few small RDS/DocumentDB/MSK instances + an ALB — on the order of
a few dollars for the window, versus **hundreds to thousands per month** if left running. Run it,
test it, *destroy it.* The `destroy` is not cleanup — it's the point.

> **The guardrail that lets you sleep:** prod state is a separate Terraform state key with
> `prevent_destroy` on the data stores ([`07` §6](./07-infrastructure-terraform.md)), so a
> `terraform destroy` in `envs/qa` *cannot* touch prod. Ephemeral means ephemeral *only* for the
> throwaway environments.

---

## 5. Production cost structure (when there are paying users)

Once live, cost should **track revenue**, not run ahead of it. Costs split into fixed (floor) and
variable (scales with usage):

| Category | Service | Fixed/Variable | Cost driver | Lever |
| --- | --- | --- | --- | --- |
| Compute | EKS control plane | Fixed (~$0.10/hr/cluster) | per cluster | one cluster, multi-namespace |
| Compute | Fargate pods | **Variable** | pod-seconds | scale-to-near-zero (KEDA, [`11`](./11-scalability-resilience.md)) |
| **COGS** | ASR minutes | **Variable** | transcription volume | per-plan quotas; cheapest adequate provider ([ADR-0005](./03-adr/0005-third-party-asr.md)) |
| **COGS** | Render compute | **Variable** | render minutes | Spot + GPU node groups when justified ([ADR-0010](./03-adr/0010-fargate-compute.md)) |
| Data | RDS + DocumentDB | Fixed-ish | instance size + Multi-AZ | right-size; replicas only when needed |
| Data | MSK | Fixed-ish | broker count | 3 brokers prod; scale partitions not brokers first |
| Storage | S3 | **Variable** | GB stored + egress | lifecycle to IA/Glacier; CDN to cut egress (§6) |
| Network | NAT, data transfer | Variable | egress volume | VPC endpoints (skip NAT for S3/ECR/etc.) |
| Observability | Datadog/Splunk | **Variable (sneaky!)** | host + ingested log/trace volume | sample traces; tier log retention ([`09`](./09-observability.md)) |

> **Two cost traps specific to this stack, called out so you avoid them:**
> 1. **Observability bill shock.** Datadog/Splunk bill on *ingested volume* — verbose logging or 100%
>    trace sampling at scale can cost more than the compute it's watching. Sample traces, tier log
>    retention, and watch this line item like a hawk.
> 2. **NAT Gateway data-processing charges.** Every byte your pods send to S3/ECR/AWS APIs *through a
>    NAT* is billed. **VPC endpoints** ([`07`](./07-infrastructure-terraform.md)) route that traffic
>    privately and free — a config detail that quietly saves real money at volume.

---

## 6. FinOps guardrails (cost as a first-class, monitored metric)

Cost is treated like latency or errors — measured, budgeted, and alerted (NFR-C-02):

- **AWS Budgets + alarms** per environment; an alert fires *before* a threshold is breached
  ([`09` §7](./09-observability.md)) — Slack/ticket, not a surprise invoice.
- **Cost allocation tags** (`Environment`, `Project`, `CostCenter`, [`07` §7](./07-infrastructure-terraform.md))
  → Cost Explorer breaks spend down by environment and component, so you *know* where money goes.
- **Ephemeral-by-default for non-prod** (NFR-C-03) — nothing non-prod runs overnight; a scheduled
  sweeper destroys any QA env left up > N hours (a forgotten `apply` is the classic money leak).
- **Grafana cost-vs-usage dashboard** ([`09` §5](./09-observability.md)) — overlays COGS (ASR/render
  minutes) against revenue/plan, so unit economics ([`00` §7](./00-overview.md)) stay visible and you
  catch a tier that's underwater.
- **Right-sizing reviews** — periodic check that instance sizes match real utilization (Datadog USE
  metrics, [`09`](./09-observability.md)); downsize the over-provisioned.

---

## 7. The developer workflow, end to end

How the cost strategy shapes a normal day/week of building:

```
 DAILY (inner loop, $0):
   code → make run (local stack) → unit + integration tests (Testcontainers) → commit → PR
   CI runs the full verify suite on every PR (08, 10) — still $0 to you (GitHub-hosted runners)

 WEEKLY / PRE-RELEASE (production fidelity, pennies):
   merge to main → CI builds proven artifact
   → terraform apply ephemeral QA → BlazeMeter load + Cucumber BDD + chaos game-day
   → review results → terraform destroy
   → on green + approval: canary to prod (08)

 CONTINUOUS (prod, scales with revenue):
   ArgoCD keeps prod in sync; autoscaling + scale-to-zero keep idle cost low;
   FinOps dashboards + budget alarms keep spend honest
```

> **Why this rhythm is the whole game for a solo/small builder:** you do the overwhelming majority of
> work for free, locally, with fast feedback. You spend money only in short, deliberate,
> production-fidelity bursts that you tear down immediately. And in prod you pay for *load*, not for
> *idle capacity*. This is how one person credibly operates a microservices platform that would
> traditionally need a team and a budget — the architecture and the cost strategy are designed *for*
> that constraint, not in spite of it.

---

## 8. Cross-reference

| For… | See |
| --- | --- |
| The Terraform that powers apply/destroy | [`07-infrastructure-terraform.md`](./07-infrastructure-terraform.md) |
| What scales to zero and how | [`11-scalability-resilience.md`](./11-scalability-resilience.md) |
| The observability cost levers | [`09-observability.md`](./09-observability.md) |
| The business unit economics this protects | [`00-overview.md` §7](./00-overview.md) |
