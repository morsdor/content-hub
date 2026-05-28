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
 feature/X ──► PR to develop ──► CI + Claude PR review
                │
                merge to develop
                │
                ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │  CI: lint → arch-test → unit → secret scan → SCA → SAST → build →   │
 │  contract tests → schema compat → integration → image scan + publish │
 └──────────────────────────────────────────────────────────────────────┘
                │                                              │
    ephemeral QA spins up                         (not triggered for
    (terraform apply)                              develop merges)
    load + BDD suite
    terraform destroy  (~2h window)
                │
                └─► PR to main ──► CI (no Claude review; human approval)
                                        │
                                        merge to main
                                        │
                                        ▼
                              GitOps digest bump PR ──► ArgoCD
                              canary 5%→25%→50%→100%    (long-running prod)
                              auto-rollback on SLO breach
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
| SCA (deps) | **Snyk OSS** | no known-CVE dependencies; blocks on High/Critical ([`06`](./06-security-architecture.md)) |
| SAST + quality gate | **SonarCloud** | code smells, coverage threshold, no injected vuln patterns |
| Build | Maven, Vite | compiles, bundles |
| **Contract tests** | **Spring Cloud Contract** (service↔service) + **Pact** (frontend↔API) | REST/event contracts honored ([`05`](./05-api-and-event-contracts.md)) |
| **Schema compat** | Confluent Schema Registry Maven plugin | no breaking Kafka schema change ([`05` §7](./05-api-and-event-contracts.md)) |
| Integration | Testcontainers | real PG/Mongo/Kafka behavior ([`10`](./10-testing-qa.md)) |
| Image build + scan | Buildpacks/Docker, Trivy | image has no critical CVEs |
| Publish | ECR | push image by **digest** + Helm chart by version |

```yaml
# .github/workflows/ci.yml  (illustrative shape)
name: ci
on:
  pull_request:
    branches: [develop, main]    # feature→develop PRs AND develop→main PRs
  push:
    branches: [develop, main]    # image publish gate fires only on these branches
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }                                       # SonarCloud needs full history
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }

      # ── Quality: style, architecture boundaries, unit tests, coverage ──
      - run: mvn spotless:check                                        # Spotless lint
      - run: mvn test -P arch-test                                     # ArchUnit module boundaries
      - run: mvn test jacocoTestReport                                 # unit + coverage report

      # ── Security: SCA (deps) ──────────────────────────────────────────
      - uses: snyk/actions/maven@master
        env: { SNYK_TOKEN: '${{ secrets.SNYK_TOKEN }}' }
        with: { args: --severity-threshold=high }                      # block on High/Critical CVEs

      # ── Quality gate: SonarCloud ─────────────────────────────────────
      - uses: SonarSource/sonarcloud-github-action@master
        env:
          GITHUB_TOKEN: '${{ secrets.GITHUB_TOKEN }}'
          SONAR_TOKEN:  '${{ secrets.SONAR_TOKEN }}'

      # ── Contracts ────────────────────────────────────────────────────
      - run: mvn spring-cloud-contract:generateTests verify -P contract  # Spring Cloud Contract (svc↔svc)
      - run: mvn pact:verify                                             # Pact (frontend↔API)
      - run: mvn schema-registry:validate                               # Kafka schema compat check

      # ── Integration (Testcontainers spins up real PG/Mongo/Kafka) ────
      - run: mvn verify -P integration

      # ── Image build + CVE scan ────────────────────────────────────────
      - run: mvn spring-boot:build-image -Dspring-boot.build-image.imageName=$ECR/$SVC:$GIT_SHA
      - uses: aquasecurity/trivy-action@master
        with: { image-ref: '${{ env.ECR }}/${{ env.SVC }}:${{ env.GIT_SHA }}' }

      # ── Publish (develop or main only, by immutable digest) ─────────
      - if: github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'
        run: docker push $ECR/$SVC:$GIT_SHA
```

### 2.1 Claude automated PR review (PRs to `develop` only)

Every PR targeting `develop` gets an automated review from Claude Code before a human merges. PRs to `main` (the `develop → main` promotion) are human-reviewed only — they should already be clean.

```yaml
# .github/workflows/claude-pr-review.yml
name: claude-pr-review
on:
  pull_request:
    branches: [develop]
    types: [opened, synchronize, reopened]

jobs:
  review:
    runs-on: ubuntu-latest
    permissions:
      pull-requests: write
      contents: read
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: anthropics/claude-code-action@beta
        with:
          anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}
          direct_prompt: |
            Review this pull request against the ContentHub project conventions in CLAUDE.md.
            Check specifically:
            1. No cross-module DB access (ADR-0009) — each module touches only its own tables
            2. No dual-writes to Kafka — all events go through the transactional outbox (ADR-0006)
            3. All Kafka consumers are idempotent (use upserts or dedupe on event ID)
            4. No prod config baked into code — env-specific values via Spring profiles only
            5. Test coverage for new logic (unit tests + integration tests where appropriate)
            6. Security: no SQL injection exposure, no secrets in code, input validated at boundaries
            Post a concise review comment on the PR. Use ✅ / ⚠️ / ❌ per concern.
```

> **Why only `develop` PRs:** the `develop → main` PR is a promotion checkpoint, not a code
> introduction point. By then Claude has already reviewed the individual feature PRs; a second
> automated review adds noise without signal. Human eyes on the promotion PR are what matter.

### 2.2 Ephemeral QA deploy on `develop` merge — **PAUSED**

> **Status: not active.** The workflow is written and ready; it is disabled via `DEPLOY_QA_ENABLED: "false"`.
> Flip to `"true"` only after all prerequisites below are met.

**Prerequisites before enabling:**

| # | What | How |
| --- | --- | --- |
| 1 | AWS OIDC provider for GitHub Actions | one-time in IAM console — lets GitHub get short-lived tokens, no static keys ever stored |
| 2 | IAM role with trust policy for this repo | trust `repo:org/content-hub:ref:refs/heads/develop`; needs EKS, RDS, MSK, S3, VPC, ECR permissions |
| 3 | Terraform state backend | S3 bucket + DynamoDB table for state locking (create manually once) |
| 4 | Terraform modules written | backlog item 4 — `infra/envs/qa/` must exist with working `main.tf` |
| 5 | GitHub Secrets/Variables set | `AWS_QA_ROLE_ARN` (secret), `TF_STATE_BUCKET` (variable), `AWS_REGION` (variable) |

**Why OIDC instead of static `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`:**  
Static keys are long-lived credentials stored in GitHub Secrets — if the repo is compromised, the keys are too.
OIDC federation issues a short-lived token scoped to the exact workflow run; it expires in minutes and there
is nothing to rotate or leak.

```yaml
# .github/workflows/qa-deploy.yml  (PAUSED — flip DEPLOY_QA_ENABLED to enable)
name: qa-deploy
on:
  push:
    branches: [develop]

permissions:
  id-token: write    # required for AWS OIDC token exchange
  contents: read

env:
  DEPLOY_QA_ENABLED: "false"    # ← flip to "true" once prerequisites are met (see §2.2)

jobs:
  deploy-qa:
    if: env.DEPLOY_QA_ENABLED == 'true'
    runs-on: ubuntu-latest
    environment: qa              # GitHub Environment gate (optional approval + env secrets)
    steps:
      - uses: actions/checkout@v4

      # ── AWS credentials via OIDC — no static keys ────────────────────
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume:    ${{ secrets.AWS_QA_ROLE_ARN }}
          aws-region:        ${{ vars.AWS_REGION }}

      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: "~1.8" }

      # ── Spin up ephemeral QA stack ────────────────────────────────────
      - name: terraform apply
        run: |
          cd infra/envs/qa
          terraform init -backend-config="bucket=${{ vars.TF_STATE_BUCKET }}" \
                         -backend-config="key=qa/terraform.tfstate"
          terraform apply -auto-approve -var="image_tag=${{ github.sha }}"

      # ── Run acceptance suite against live QA ─────────────────────────
      - name: load + BDD tests
        run: mvn verify -P qa-acceptance -Denv.base-url=${{ steps.tf-outputs.outputs.app_url }}

      # ── Always destroy — even if tests fail ──────────────────────────
      - name: terraform destroy
        if: always()
        run: |
          cd infra/envs/qa
          terraform destroy -auto-approve
```

> **The `if: always()` on destroy is load-bearing** — if tests fail and destroy is skipped, you have
> a forgotten running AWS stack burning money. `if: always()` fires regardless of prior step outcome.

> **SonarCloud vs CodeQL:** SonarCloud is the primary quality + security gate here (coverage
> enforcement, code smells, Java-specific vuln patterns). GitHub's CodeQL can be enabled as a
> complementary SAST for deeper security analysis — it runs in a separate workflow and is free for
> public repos. Both are non-blocking suggestions in PR reviews; only SonarCloud enforces a Quality
> Gate that can fail the build.
>
> **Snyk vs Dependabot:** Snyk is the CI gate (blocks merges with High/Critical CVEs). Dependabot
> runs separately as an automated PR bot that keeps dependency versions current — they serve
> different jobs and run simultaneously.

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

### 3.1 Repo structure — one monorepo or two?

The GitOps pattern requires separating *app source* from *deploy state*. Two options:

| Pattern | Structure | Recommendation |
| --- | --- | --- |
| **Two repos** (enterprise standard) | `content-hub/` (code) + `content-hub-deploy/` (Helm values per env) | Clean separation, independent history, harder to link a code change to its deploy |
| **Monorepo with deploy path** | `content-hub/infra/deploy/envs/{qa,prod}/` lives in the same repo | One PR shows code change + the resulting deploy config bump together — better for a solo build |

**This project uses the monorepo approach.** ArgoCD watches `infra/deploy/envs/prod/` in this
same repo. The bot that bumps the image digest opens a PR against this repo (not a separate one),
so the audit trail is: one PR = the code change + its deploy config in one place.

```
content-hub/
├── backend/                    ← Spring Boot source (this repo)
├── frontend/                   ← React source (this repo)
├── infra/
│   ├── local/                  ← docker-compose configs (local dev, $0)
│   ├── modules/                ← Terraform modules (VPC, EKS, RDS, MSK…)
│   ├── envs/
│   │   ├── qa/                 ← ephemeral; terraform apply/destroy
│   │   └── prod/               ← always-on; prevent_destroy on data stores
│   └── deploy/
│       ├── qa/values.yaml      ← image digests + config for QA   ← ArgoCD watches this
│       └── prod/values.yaml    ← image digests + config for prod  ← ArgoCD watches this
└── helm/
    └── contenthub/             ← Helm chart templates (shared across envs)
```

### 3.2 Where does ArgoCD run?

**ArgoCD runs as pods inside your EKS cluster** — it is not a separate server, not a SaaS, and
not something you pay for independently.

```
 ┌──────────────────────────── EKS Cluster ───────────────────────────────┐
 │                                                                          │
 │   ┌─── argocd namespace ─────────────────────────────────────────────┐  │
 │   │  argocd-server     (UI + API)                                     │  │
 │   │  argocd-repo-server (clones + renders Helm charts from Git)       │  │
 │   │  application-controller  (reconciles desired → actual state)      │  │
 │   └───────────────────────────────────────────────────────────────────┘  │
 │                                                                          │
 │   ┌─── contenthub namespace ───────────────────────────────────────────┐ │
 │   │  app pods (what ArgoCD manages)                                    │ │
 │   └────────────────────────────────────────────────────────────────────┘ │
 └──────────────────────────────────────────────────────────────────────────┘
          │  pulls from Git (outbound only)
          ▼
 content-hub repo  ──  infra/deploy/prod/values.yaml
```

**How it gets there:** ArgoCD is installed into the cluster once via Terraform (a Helm release in
[`07-infrastructure-terraform.md`](./07-infrastructure-terraform.md)):

```hcl
resource "helm_release" "argocd" {
  name       = "argocd"
  repository = "https://argoproj.github.io/argo-helm"
  chart      = "argo-cd"
  namespace  = "argocd"
  # values: repo URL, RBAC, SSO with Cognito
}
```

After that, ArgoCD is self-managing — it can even update itself via a GitOps `Application` that
points at its own Helm values. Cost is just the Fargate pod-seconds for ~6 lightweight pods (cents
per hour). For the local and ephemeral-QA environments ArgoCD is **not used** — you run the app
directly (`make run`) or deploy via the Helm chart directly in the ephemeral stack.

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

## 6. Branch strategy and environment promotion

### Branches

| Branch | Purpose | Lifetime |
| --- | --- | --- |
| `feature/*` | one feature/fix; tested locally against docker-compose | short-lived |
| `develop` | integration branch; triggers ephemeral QA on every merge | long-lived |
| `main` | production-ready code; triggers prod canary deploy on every merge | permanent |

### Promotion flow

```
 feature/X
   │
   ├─► PR to develop ──► CI runs + Claude automated review
   │                         │ (human reviews Claude's comment + approves)
   │                         ▼
   │                   merged to develop
   │                         │
   │                         ├─► [PAUSED] ephemeral QA: terraform apply        ← re-enable in §2.2
   │                         │     load tests (BlazeMeter) + BDD suite          when Terraform modules
   │                         │     ≤2h window → terraform destroy               + OIDC are ready
   │                         │
   │                         └─► (while QA paused) promote manually to main after local validation
   │
   └─► (repeat per feature)

 PR to main approved + merged
   │
   └─► CI rebuilds + re-proves artifact
        └─► GitOps: bot bumps image digest in infra/deploy/prod/values.yaml
             └─► ArgoCD picks up the change
                  └─► canary: 5% → 25% → 50% → 100%
                       │  auto-rollback if SLO breached during any step
                       └─► full traffic on prod
```

- **QA deploy is PAUSED** — the `qa-deploy.yml` workflow exists but is disabled (`DEPLOY_QA_ENABLED: "false"`). See §2.2 for the full prerequisite checklist (OIDC, IAM role, Terraform state backend, Terraform modules). Enable it when backlog item 4 (Terraform modules) is complete.
- **While paused:** merge `develop → main` manually after validating locally against docker-compose. No AWS spend until QA is enabled.
- **Prod requires human approval** for the `develop → main` PR — the one deliberate manual gate.
- **Same artifact promoted** — the exact image proven in QA will reach prod (digest-pinned); never rebuilt between environments ([`02` §9](./02-system-architecture.md)).

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
