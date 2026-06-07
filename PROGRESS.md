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

### 2026-05-28 — Spring Boot modular monolith skeleton
- `backend/` Maven multi-module project: parent + 5 domain modules + `app`
- Spring Boot 3.4.1 / Java 25 / Hibernate 6.6 / Kafka / MongoDB / Flyway
- `shared-kernel`: `OutboxEntry` JPA entity, `OutboxRepository`, `OutboxRelayService` (polling relay, wired but off by default)
- `shared-kernel`: `ContentHubArchRules` (published in main — app module executes them)
- Flyway `V1__initial_schema.sql`: full Postgres DDL from `docs/04` (all tables, indexes, citext extension)
- `app`: `ContentHubApplication`, `SecurityConfiguration` (JWT/OAuth2 resource server), `AwsConfiguration` (S3Presigner)
- `app`: `application.yml` / `application-dev.yml` / `application-test.yml`
- `app/arch/ModuleBoundaryTest`: 5 ArchUnit rules — all pass — enforcing no cross-module DB access (ADR-0009)
- `app/ApplicationSmokeTest`: Testcontainers smoke test (awaiting Testcontainers 1.21+ for Docker Desktop 4.75 compatibility)
- `/actuator/health` verified UP in 6s against live docker-compose stack (`SPRING_PROFILES_ACTIVE=dev`)
- Build: `make backend-build` (requires `JAVA_HOME` → JDK 25; Homebrew openjdk 25 is compatible with upgraded Lombok)

### 2026-05-28 — Local dev stack
- `docker-compose.yml` — 13 services: Postgres, MongoDB, Kafka + Zookeeper + Schema Registry,
  LocalStack (S3), mock-oauth2 (Cognito stand-in), OTel Collector, Jaeger, Prometheus, Grafana
- `infra/local/` — OTel Collector config, Prometheus scrape config, Grafana datasource provisioning
- `Makefile` — `make dev-up / dev-down / logs / doctor / clean-force`
- `.env.local.example`, `.gitignore`
- `CLAUDE.md` (this file's companion — loaded automatically by Claude Code)

---

### 2026-05-30 — React + TypeScript SPA skeleton + RTK Query data layer

- `frontend/` Vite 5 + React 18 + TypeScript strict project; `npm run build` → clean `dist/`
- `react-oidc-context` + `oidc-client-ts` PKCE flow; `CognitoAuthProvider` syncs JWT to Redux on auth state change
- Redux Toolkit store: `auth/slice` (token + user profile), `workspace/slice` (`currentWorkspaceId` only)
- **RTK Query** (`src/api/contentHubApi.ts`): `getWorkspaces` query + `createWorkspace` mutation; `prepareHeaders` injects Bearer token from Redux; `invalidatesTags` auto-refetches list after create
- No axios singleton, no `createAsyncThunk`, no manual loading/error state — RTK Query owns all of that
- React Router v6: `/login`, `/auth/callback`, `/workspaces`, `/workspaces/:id`; `ProtectedRoute` guard
- `WorkspaceList` + `WorkspaceCreate` use `useGetWorkspacesQuery` / `useCreateWorkspaceMutation` hooks directly
- `useWebSocket(workspaceId)` stub, `MediaUpload` and `AnalyticsDashboard` stubs in `features/`
- Makefile: `frontend-install`, `frontend-dev`, `frontend-build` targets
- Default dev config (no `.env.local` needed): OIDC → mock-oauth2 `:8090/contenthub`

### 2026-05-31 — Atlaskit integration + design system

- Installed Atlaskit packages: `@atlaskit/tokens`, `button`, `textfield`, `form`, `heading`, `spinner`, `lozenge`, `empty-state`, `page-layout`, `side-navigation`, `flag`
- `src/styles/theme.css` — CSS variable overrides for light + dark mode (Studio Green CTA, ContentHub Blue)
- `src/main.tsx` — `setGlobalTheme({ colorMode: 'light' })` bootstrap; Inter loaded via Google Fonts CDN
- All raw HTML UI elements replaced: `Button`, `Textfield`, `Form`+`Field`, `Heading`, `Spinner`, `EmptyState` used throughout
- `axios` dependency removed (RTK Query's `fetchBaseQuery` handles HTTP)
- `npm run build` passes; zero TypeScript errors

### 2026-05-31 — Design system (`docs/DESIGN.md`)

- Full token spec: Studio Green `#1ED760` CTA-only, ContentHub Blue `#1860E8`, neutral scale, semantic colours
- Light + dark mode tokens; all verified against WCAG 2.1 AA (contrast tables in §3)
- 6-colour data-viz palette (L=0.133–0.30 window) with separate dark-mode lightened variants
- Density tiers: Balanced (board/settings) + Compact (timeline editor)
- Atlaskit integration plan: package list, `setGlobalTheme` bootstrap, `theme.css` CSS variable overrides
- `CLAUDE.md` updated: Frontend conventions section (Atlaskit-only, RTK Query-only, WCAG, green CTA rule)

---

---

## Next

### Flyway V2 + Phase 0 walking skeleton

**Scope:** A runnable Vite + React 18 + TypeScript SPA in `frontend/` that mirrors the backend's
domain structure and proves the auth handshake against mock-oauth2 locally.

```
frontend/
├── package.json                  (Vite + React + TS + Redux Toolkit + React Router)
├── vite.config.ts                (dev proxy → localhost:8080, HMR on :5173)
├── index.html
└── src/
    ├── main.tsx                  (ReactDOM.createRoot, <App/>)
    ├── App.tsx                   (React Router shell: /login, /workspaces, /workspaces/:id)
    ├── auth/
    │   ├── CognitoAuthProvider.tsx   (wraps amazon-cognito-identity-js or amplify-lite)
    │   └── useAuth.ts                (hook: user, login(), logout(), token)
    ├── store/
    │   ├── index.ts              (configureStore with all slice reducers)
    │   ├── workspace/slice.ts    (workspaces list, current workspace)
    │   └── auth/slice.ts         (JWT token, user profile)
    ├── features/
    │   ├── workspace/            (WorkspaceList, WorkspaceCreate)
    │   ├── media/                (MediaUpload stub)
    │   └── analytics/            (stub)
    └── api/
        ├── client.ts             (axios instance; attaches Bearer token from store)
        └── workspaceApi.ts       (RTK Query or plain axios: GET/POST /api/v1/workspaces)
```

**Acceptance criteria for this task:**
- [ ] `npm run dev` serves the app on `:5173`; Vite proxies `/api` → `localhost:8080`
- [ ] Login page redirects to mock-oauth2 (`:8090`) and back; JWT stored in Redux + localStorage
- [ ] Authenticated `GET /api/v1/workspaces` (with Bearer header) → 200 from Spring backend
- [ ] `WorkspaceCreate` form calls `POST /api/v1/workspaces` and shows the new workspace in the list
- [ ] React Router guards: unauthenticated users redirect to `/login`
- [ ] `npm run build` produces a `dist/` (proves no TypeScript errors)
- [ ] WebSocket client stub: `useWebSocket(workspaceId)` hook exists but is no-op until Phase 1

**Key files to read before starting:**
- `docs/01-product-requirements.md` §FR-WS (workspace features) — what the UI must do
- `docs/02-system-architecture.md` §3 container responsibilities + §6 sync vs async patterns
- `docs/03-adr/0004-cognito-for-identity.md` — why Cognito/OIDC; mock-oauth2 stands in locally
- `docs/06-security-architecture.md` §JWT — token shape, claims, expiry handling
- `backend/app/src/main/java/com/contenthub/config/SecurityConfiguration.java` — what the backend expects
- `.env.local.example` — `OIDC_ISSUER_URI`, `MOCK_OAUTH2_PORT` values

---

## After Next (backlog, in order)

2. **Transcription mock pipeline** — verify the full Kafka event chain end-to-end with integration tests
3. **Terraform module skeletons** — VPC, EKS/Fargate, RDS, MSK, S3, Cognito (infra/modules/)
4. **GitHub Actions CI** — implement `ci.yml` (§2: Snyk OSS, SonarCloud, Spring Cloud Contract,
   Pact, Schema Registry plugin, Trivy) and `claude-pr-review.yml` (§2.1, PRs to `develop` only).
   QA deploy workflow (`qa-deploy.yml`) is already written in §2.2 but **PAUSED** — enable it after
   Terraform modules (item 3) are done and OIDC + IAM role are set up in AWS.

> **Note (Phase 0 walking skeleton scope):** the `Flyway V2 + Phase 0` item became the new ##Next above.
> Its acceptance criteria are the end-to-end spine: login → JWT → workspace creation → S3 upload →
> `video.uploaded` via outbox → Kafka → mock ASR → transcript in MongoDB.

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
  4. docs/05-api-and-event-contracts.md    ← REST + Kafka event contracts

Then pick up PROGRESS.md §Next and implement it.

Environment notes:
- Backend build: JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
- Use `make backend-build`, `make backend-test`, `make backend-run` (Makefile pins JDK 25)
- MongoDB URI needs ?authSource=admin (root user is in admin database)
- Testcontainers smoke test (ApplicationSmokeTest) requires Testcontainers ≥1.21 for Docker Desktop 4.75
- Frontend: `make frontend-install` then `make frontend-dev` (Vite HMR on :5173, proxies /api → :8080)
- Frontend auth: OIDC via react-oidc-context; no .env.local needed for dev (defaults to mock-oauth2 :8090)

Frontend non-negotiables (also in CLAUDE.md §Frontend conventions):
- ALL UI must use Atlaskit components — never raw <button>, <input>, <form>, <h1>–<h6>
- ALL API calls via RTK Query (src/api/contentHubApi.ts) — no createAsyncThunk, no raw axios
- ALL colors/spacing from docs/DESIGN.md tokens — never hardcode hex or px values
- Studio Green (#1ED760) is CTA-only; black label text on green-500 (WCAG contrast rule)
- Read docs/DESIGN.md before touching any UI file

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
- Frontend auth uses oidc-client-ts (standard PKCE) — works with mock-oauth2 locally and Cognito in prod
- Frontend data layer uses RTK Query (contentHubApi) — no async thunks, no axios client singleton
```
