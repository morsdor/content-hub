# 08 — Deployment & CI/CD

**Audience:** builders + DevOps. How code goes from a commit to running in production **safely,
repeatably, and reversibly**. The guiding goals: every change is tested before it ships, deploys are
boring, and rollback is always one step away.

> **The deploy philosophy:** the scariest deploy is the one you do rarely and manually. We make deploys
> *frequent, automated, and small* so each carries little risk and the path is well-worn. A small team
> survives by never having to think hard about *how* to ship — only *what* to ship.

---

## 1. Pipeline overview

```
 ┌─────────┐   ┌──────────────── CI (per PR / per push) ────────────────┐   ┌─────── CD ───────┐
 │  commit │──►│ lint → unit → SAST/secret/SCA → build → image scan →    │──►│ GitOps sync to    │
 │  + PR   │   │ contract tests → integration (Testcontainers) → publish │   │ env via ArgoCD     │
 └─────────┘   │ image (digest) + Helm chart                             │   │ progressive deliver│
               └──────────────────────────────────────────────────────────┘   └───────────────────┘
                                          │                                            │
                            ephemeral qa: terraform apply →                  prod: canary → promote
                            BlazeMeter + Cucumber → destroy (07,10,12)        or auto-rollback
```

Two distinct concerns, deliberately separated:
- **CI** (GitHub Actions) — *build and prove* an artifact is good. Produces an immutable, scanned,
  digest-pinned container image + a versioned Helm chart.
- **CD** (GitOps / ArgoCD) — *deliver* that artifact to an environment by reconciling cluster state to
  a Git-declared desired state.

---

## 2. Continuous Integration (GitHub Actions)

Stages, in order, with a fail-fast principle (cheapest checks first):

| Stage | Tool | Gate |
| --- | --- | --- |
| Lint / format | Spotless, ESLint, Prettier | style |
| **Architecture tests** | ArchUnit | module boundaries intact ([ADR-0009](./03-adr/0009-modular-monolith-first.md)) |
| Unit tests | JUnit5, Vitest | logic correctness ([`10`](./10-testing-qa.md)) |
| Secret scan | gitleaks | no secrets in diff ([`06`](./06-security-architecture.md)) |
| SCA (deps) | Dependabot/Trivy | no known-vuln dependencies |
| SAST | CodeQL | no injected vuln patterns |
| Build | Gradle, Vite | compiles, bundles |
| **Contract tests** | Pact | REST/event contracts honored ([`05`](./05-api-and-event-contracts.md)) |
| **Schema compat** | Schema Registry check | no breaking Kafka schema change ([`05` §7](./05-api-and-event-contracts.md)) |
| Integration | Testcontainers | real PG/Mongo/Kafka behavior ([`10`](./10-testing-qa.md)) |
| Image build + scan | Buildpacks/Docker, Trivy | image has no critical CVEs |
| Publish | ECR | push image by **digest** + Helm chart by version |

```yaml
# .github/workflows/ci.yml  (illustrative shape)
name: ci
on: [pull_request, push]
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: ./gradlew spotlessCheck archTest test jacocoTestReport   # style + boundaries + unit
      - run: ./gradlew pactVerify                                     # consumer contracts (05)
      - run: ./gradlew integrationTest                               # Testcontainers PG/Mongo/Kafka
      - uses: aquasecurity/trivy-action@master                       # image CVE scan
        with: { image-ref: '${{ env.IMAGE }}' }
      # publish only on main, by immutable digest:
      - if: github.ref == 'refs/heads/main'
        run: ./gradlew bootBuildImage --imageName=$ECR/$SVC:$GIT_SHA && docker push $ECR/$SVC:$GIT_SHA
```

> **Why contract tests and schema-compat checks are CI gates, not afterthoughts:** in a microservices
> system the most dangerous bug is the *silent* contract break — Service A removes a field Service B
> relies on, both deploy green, and B breaks in prod. Pact + the Schema Registry compatibility check
> turn that into a **failed build on A's PR**. This is the mechanism that makes "deploy services
> independently" actually safe ([`05` §7](./05-api-and-event-contracts.md)).

---

## 3. Continuous Delivery — GitOps with ArgoCD

We use **GitOps**: the desired state of each environment is declared in a Git repo (Helm values per
env); **ArgoCD** continuously reconciles the cluster to match. CI updates the image digest in Git;
ArgoCD notices and rolls it out.

```
 CI publishes image  ──►  bot opens PR bumping image digest in  deploy-repo/envs/prod/values.yaml
                              │  (human approves for prod; auto-merge for qa)
                              ▼
                       ArgoCD sees Git change ──► syncs cluster to desired state ──► rollout
```

**Why GitOps over `kubectl apply` from CI:**
- **Git is the single source of truth** — what's in the cluster equals what's in Git, auditable and
  diffable. "What's running in prod?" is answered by a Git SHA, not by SSHing around.
- **Rollback = `git revert`** — revert the digest bump and ArgoCD rolls back. No special tooling.
- **Drift correction** — if someone hand-edits the cluster, ArgoCD reverts it to the declared state
  (pairs with Terraform drift detection in [`07`](./07-infrastructure-terraform.md)).
- **CI needs no cluster credentials** — it only writes to Git; ArgoCD (inside the cluster) pulls. One
  fewer powerful credential to leak.

---

## 4. Progressive delivery (canary / blue-green)

We never flip 100% of traffic to a new version at once. Two strategies by service criticality:

| Service | Strategy | Why |
| --- | --- | --- |
| API Gateway, Workspace (interactive, user-facing) | **Canary** (Argo Rollouts) — 5% → 25% → 50% → 100%, gated on SLO metrics | catches regressions on a sliver of traffic; auto-rollback on error/latency breach |
| Transcription / Render workers | **Rolling** | queue-driven; a bad version just stops consuming, lag triggers alert |
| Stateful (DB) | n/a — migrations, see §5 | data needs forward/back-compatible migration, not traffic shifting |

**Canary auto-analysis:** Argo Rollouts queries Datadog/Prometheus during each step; if the canary's
error rate or p99 latency exceeds the baseline + threshold, it **automatically rolls back** before most
users are affected. This ties deploys directly to the SLOs in [`11`](./11-scalability-resilience.md).

> **Why canary specifically for the Gateway and Workspace service:** they're on every user's critical
> path, and some regressions only appear under real traffic (a slow query that's fine in tests, a memory
> leak under load). A canary exposes the bug to 5% of users for a few minutes with automatic rollback,
> versus a full rollout exposing 100% for however long it takes a human to notice.

---

## 5. Database migrations (the genuinely hard part of CD)

Code can roll back instantly; **schema cannot**. So migrations follow the **expand/contract
(parallel-change)** pattern, decoupled from app deploys ([`07` §8](./07-infrastructure-terraform.md)).

**Tooling:** **Flyway** (versioned, ordered SQL migrations) for Postgres, run as a Kubernetes **Job**
*before* the new app version rolls out. DocumentDB is schemaless so "migrations" there are
data-backfill jobs, handled similarly.

**The expand/contract rule — never break the running version:**

```
Goal: rename column  card.title → card.name   without downtime

1. EXPAND   : add `name`; app writes BOTH, reads `title`   (new schema, old app still works)
2. MIGRATE  : backfill `name` from `title`
3. SWITCH   : deploy app that reads/writes `name`          (both columns still exist)
4. CONTRACT : after the old app version is fully gone, drop `title`   (separate, later migration)
```

- **Every migration is backward-compatible** with the currently-running app version, so a canary
  rollback never hits a schema the old code can't read. This is *why* steps are split across deploys.
- **Forward-only** — we don't write "down" migrations for prod; recovery is roll-forward + PITR
  ([`13`](./13-runbooks-and-dr.md)), because auto-running a down-migration risks data loss.
- Migrations run as a **pre-deploy Job** gated to one runner (no concurrent migrators).

> **The single most common production outage in systems like this** is a deploy that ships code and a
> breaking schema change together, then can't roll back because the old code can't read the new schema.
> Expand/contract makes that outage *structurally impossible*. It's more steps; it's worth every one.

---

## 6. Environment promotion

```
 PR merged to main
   └─► CI builds + proves artifact
        └─► auto-deploy to QA (ephemeral; terraform apply → tests → destroy, 07)
             └─► on green + manual approval ─► canary to PROD ─► (auto-analysis) ─► full
```

- **QA is ephemeral and gated** — promotion to prod requires QA's load + BDD suite to pass against a
  real cloud stack.
- **Prod requires human approval** for the digest bump (GitOps PR) — the one deliberate manual gate.
- **Same artifact promoted** — the exact image proven in QA is what reaches prod (digest-pinned); we
  never rebuild between environments ([`02` §9](./02-system-architecture.md)).

---

## 7. Rollback & recovery

| What broke | Rollback path |
| --- | --- |
| Bad app version | `git revert` the digest bump → ArgoCD restores previous version (or Argo Rollouts auto-rollback during canary) |
| Bad config | revert the values.yaml change in Git |
| Bad migration | roll **forward** with a corrective migration + PITR if data damaged ([`13`](./13-runbooks-and-dr.md)) |
| Bad infra change | `terraform apply` the previous known-good (state is versioned, [`07`](./07-infrastructure-terraform.md)) |
| Bad event/data in Kafka | replay from the retained log after fix; DLQ reprocessing ([`05` §6](./05-api-and-event-contracts.md)) |

**Rollback target: < 5 minutes** for an app-version regression. Because deploys are GitOps + canary,
rollback is the *same machinery as deploy* run backward — not a panicked novel procedure.

---

## 8. Release hygiene

- **Immutable, digest-pinned images** — `:latest` is banned in any environment; you always know exactly
  what bytes are running.
- **Feature flags** for risky product changes — decouple *deploy* (ship the code, off) from *release*
  (turn it on), so a problematic feature is disabled without a redeploy.
- **Trunk-based development** with short-lived branches — small, frequent merges keep CI fast and
  conflicts rare.
- **Conventional commits** → automated changelog + semantic version of charts.
- **Every deploy emits a deploy marker** to Datadog ([`09`](./09-observability.md)) so a latency/error
  graph can be correlated to "what shipped" at a glance.
