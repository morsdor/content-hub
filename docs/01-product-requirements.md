# 01 — Product Requirements (PRD)

**Audience:** builders. This is the contract for *what* the product does. The *how* lives in
[`02-system-architecture.md`](./02-system-architecture.md) and beyond.

**Conventions:** `MUST` / `SHOULD` / `MAY` per [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119).
Requirements are tagged `[MVP]`, `[v1]`, or `[later]` per the phasing in
[`00-overview.md` §11](./00-overview.md). Acceptance criteria are written in **Given / When / Then**
form so they translate directly into Cucumber scenarios (see [`10-testing-qa.md`](./10-testing-qa.md)).

---

## 1. Personas (the people we build for)

| ID | Persona | Context | Top frustrations today |
| --- | --- | --- | --- |
| P1 | **Operator** | Runs the content business; the buyer. May be solo. | Can't see the whole pipeline; publishing is manual and risky. |
| P2 | **Editor** | Cuts raw footage into finished videos. | Premiere is slow and overkill; file handoffs are chaos. |
| P3 | **Writer** | Scripts and outlines, often offline. | Script lives in a different tool than the footage. |
| P4 | **Analyst** | Decides what to make next (often = P1). | Metrics scattered across platform dashboards. |

Each functional requirement below names the persona(s) it serves. **A requirement that serves no
persona is a bug in the PRD.**

---

## 2. Core user journeys

### J1 — "Idea to published video" (the spine)

```
P3 Writer drafts a script  ──►  P1 Operator schedules a shoot (Kanban card moves)
        │
        ▼
Footage is recorded  ──►  P2 Editor uploads raw media to ContentHub
        │
        ▼
System transcribes audio  ──►  P2 edits the video BY EDITING THE TRANSCRIPT
        │
        ▼
P2 triggers a render  ──►  browser notifies P2 when the cloud render is done
        │
        ▼
P1 reviews  ──►  schedules + publishes to YouTube + X in one action
        │
        ▼
P4 watches the cross-platform analytics funnel fill in
```

This is the journey the entire architecture exists to serve. Every service maps to a step in it.

### J2 — "Edit on a plane" (offline resilience)

P3 opens a script in the browser, loses connectivity, keeps writing for 40 minutes, lands, reconnects.
**No keystroke is lost**; changes sync and any concurrent edits reconcile. (See FR-WS-04.)

### J3 — "Two editors, one script" (live collaboration)

P2 and P3 open the same script. Each sees the other's cursor and edits appear character-by-character,
Google-Docs style. When P2's render finishes, *both* get a browser notification. (See FR-RT-01..03.)

---

## 3. Functional requirements

Feature areas map 1:1 to backend services (see [`02`](./02-system-architecture.md)). IDs are stable
and referenced throughout the docs and test suites.

### 3.1 Creator Workspace — Kanban (`FR-WS-*`) · Owner service: **Workspace Service**

| ID | Requirement | Persona | Phase |
| --- | --- | --- | --- |
| FR-WS-01 | The system MUST let a user create a **Workspace** (a container for a content business / channel) and invite members with roles (`owner`, `editor`, `viewer`). | P1 | v1 |
| FR-WS-02 | A Workspace MUST contain a **Kanban board** with user-orderable columns (default: *Ideas → Scripting → Filming → Editing → Review → Published*). | P1 | v1 |
| FR-WS-03 | A user MUST be able to create **Cards** (= a content project), drag them across columns, and attach a script, media, and metadata (title, target platforms, due date). | P1, P3 | v1 |
| FR-WS-04 | Card moves and edits MUST be reflected to all members of the Workspace within **2 s** when online. | P1 | v1 |
| FR-WS-05 | The board state MUST be authoritative in PostgreSQL; the UI MUST optimistically update and reconcile on server confirmation. | P1 | v1 |

**Acceptance (FR-WS-03):**
```gherkin
Given I am an "editor" in the "MyChannel" workspace
When I create a card "Q3 Recap" in the "Ideas" column
And I drag it to the "Scripting" column
Then the card persists in "Scripting" after a page reload
And every online member sees it in "Scripting" within 2 seconds
```

### 3.2 Creator Workspace — Offline Script Editor (`FR-SC-*`) · Owner: **Workspace Service** + client

| ID | Requirement | Persona | Phase |
| --- | --- | --- | --- |
| FR-SC-01 | Each Card MUST have a **rich-text script** (headings, lists, bold/italic, speaker tags, timestamps). | P3 | MVP |
| FR-SC-02 | The editor MUST **autosave** locally (IndexedDB) on every change and to the server on a debounced interval (≤ 5 s) when online. | P3 | MVP |
| FR-SC-03 | The editor MUST be fully usable **offline** (PWA + Service Worker); edits queue locally and sync on reconnection. | P3 | v1 |
| FR-SC-04 | On reconnection, queued offline edits MUST sync without clobbering concurrent server changes; conflicts MUST be resolved deterministically (see [`04` §transcript/CRDT](./04-data-model.md)). | P3 | v1 |
| FR-SC-05 | The user MUST see a clear **sync status** indicator (`saved` / `saving` / `offline — N changes queued`). | P3 | v1 |

> **Why offline is a hard requirement for scripts but not for video editing:** writing is a
> low-bandwidth, high-frequency, often-disconnected activity (planes, trains, cafés). Video rendering
> is inherently a cloud/compute activity — there is nothing useful to do offline. Honoring the
> distinction keeps the offline engine small (text + JSON deltas in IndexedDB) instead of trying to
> cache gigabytes of media. This is Principle #3 from the overview, made concrete.

### 3.3 Text-Based AI Media Engine (`FR-ME-*`) · Owner: **Media Service** + **AI Transcription Service**

This is the heart of the product. Get this right and nothing else matters; get it wrong and nothing
else helps.

| ID | Requirement | Persona | Phase |
| --- | --- | --- | --- |
| FR-ME-01 | A user MUST be able to **upload raw media** (mp4/mov/mp3/wav) up to a configurable size (default 5 GB) via a resumable, direct-to-S3 upload. | P2 | MVP |
| FR-ME-02 | On upload completion, the system MUST automatically **transcribe** the audio to a time-aligned transcript (word-level start/end timestamps). | P2 | MVP |
| FR-ME-03 | The transcript MUST render in the browser as **editable text**, with each word linked to its source media time range. | P2 | MVP |
| FR-ME-04 | **Deleting text MUST mark the corresponding media range as removed** from the cut; reordering text MUST reorder the corresponding media. (Non-destructive — the source is never mutated; we maintain an Edit Decision List.) | P2 | MVP |
| FR-ME-05 | The user MUST be able to **preview** the current cut (playback that skips removed ranges) before rendering. | P2 | v1 |
| FR-ME-06 | The user MUST be able to trigger a **render** that produces a downloadable/publishable video reflecting the text edits. | P2 | v1 |
| FR-ME-07 | Rendering MUST be **asynchronous**; the user is notified on completion (FR-RT-03) and MUST be able to leave the page. | P2 | v1 |
| FR-ME-08 | The system MUST support **"filler-word removal"** and **"remove silences"** as one-click transcript operations. | P2 | later |

**The core model (FR-ME-04) explained — this is the crux of the product:**

> The source media in S3 is **immutable**. The "edit" is an **Edit Decision List (EDL)**: an ordered
> list of `(mediaId, startMs, endMs)` segments. Deleting the sentence "um, so, yeah" from the
> transcript doesn't touch the video file — it removes the segment whose time range maps to those
> words from the EDL. *Preview* plays the EDL by seeking; *render* stitches the EDL segments into a new
> file. This is why the product is fast, non-destructive, and collaborative: edits are tiny JSON
> operations on a list, not pixel work. The full EDL data model is in [`04-data-model.md`](./04-data-model.md).

**Acceptance (FR-ME-04):**
```gherkin
Given a transcribed 60-second clip where the words "um so yeah" span 12.0s–13.4s
When I delete "um so yeah" from the transcript
Then the EDL no longer contains the 12.0s–13.4s range
And previewing the cut skips from 12.0s to 13.4s with no audible gap artifact
And the source media object in S3 is byte-for-byte unchanged
```

### 3.4 Multi-Platform Analytics & Publishing (`FR-AP-*`) · Owner: **Analytics Service**

| ID | Requirement | Persona | Phase |
| --- | --- | --- | --- |
| FR-AP-01 | A user MUST be able to connect their **YouTube** and **X** accounts via OAuth and store the grant securely. | P1 | v1 |
| FR-AP-02 | A user MUST be able to **schedule** a rendered video to publish to one or more connected platforms at a chosen time. | P1 | v1 |
| FR-AP-03 | The system MUST publish at the scheduled time and report success/failure per platform; failures MUST be retried with backoff and surfaced to the user. | P1 | v1 |
| FR-AP-04 | The system MUST **ingest engagement metrics** (views, likes, comments, watch time) from connected platforms on a schedule and store the raw payloads. | P4 | v1 |
| FR-AP-05 | The user MUST see a **cross-platform funnel/dashboard**: per-piece and aggregate metrics normalized across YouTube + X. | P4 | v2 |
| FR-AP-06 | Platform API quota limits MUST be respected; the system MUST degrade gracefully (queue + delay) rather than fail when throttled. | P1, P4 | v1 |

> **Why metrics land in MongoDB, not Postgres:** YouTube and X return deeply nested, irregular,
> schema-drifting JSON that differs per platform and changes when the platforms change their APIs.
> Forcing that into rigid relational tables means a migration every time a platform adds a field.
> Document storage absorbs the variability; we compute normalized rollups for the funnel view. The
> billing and account data that *must* be rigid and transactional stays in Postgres. This polyglot
> split is [ADR-0002](./03-adr/).

### 3.5 Real-Time Collaboration (`FR-RT-*`) · Owner: **Workspace Service** (WebSocket gateway)

| ID | Requirement | Persona | Phase |
| --- | --- | --- | --- |
| FR-RT-01 | Multiple users editing the same script MUST see each other's **live cursors and selections**. | P2, P3 | v3 |
| FR-RT-02 | Concurrent edits MUST merge without lost updates (collaborative editing algorithm — see [`02`](./02-system-architecture.md)). | P2, P3 | v3 |
| FR-RT-03 | When an async job (render, transcription) completes, the system MUST push a **browser notification** to the relevant users in real time. | P2 | v1 |
| FR-RT-04 | Presence (who's online in a workspace) SHOULD be visible. | P1 | v3 |

### 3.6 Enterprise Observability (`FR-OB-*`) · Owner: cross-cutting

| ID | Requirement | Persona | Phase |
| --- | --- | --- | --- |
| FR-OB-01 | Operators MUST have an in-product **system health view** (service status, recent errors, job queue depth). | P1 | v2 |
| FR-OB-02 | The platform MUST emit structured logs, metrics, and traces for every request and event (see [`09`](./09-observability.md)). | P1 | MVP→ongoing |
| FR-OB-03 | Operators SHOULD see **business KPIs** (videos published, transcription minutes used, active members) in a dashboard. | P1, P4 | v2 |

> **Note:** FR-OB-02 is tagged "MVP→ongoing" deliberately. Observability is not a phase-2 feature you
> bolt on; it is instrumented from the *first* service. The in-product *dashboards* (OB-01/03) can come
> later, but the telemetry pipeline ships with the walking skeleton. Retrofitting tracing is far more
> expensive than building it in.

---

## 4. Non-functional requirements (NFRs)

NFRs are where "production-grade" is won or lost. Each is measurable and most map to an SLO in
[`11-scalability-resilience.md`](./11-scalability-resilience.md).

### 4.1 Performance

| ID | Requirement |
| --- | --- |
| NFR-P-01 | API p99 latency for interactive reads (board, script load) MUST be < 300 ms within-region. |
| NFR-P-02 | Optimistic UI: user-initiated edits MUST reflect locally in < 100 ms regardless of network. |
| NFR-P-03 | Transcription MUST complete within **1.5× media duration** at p95 (a 10-min video → ≤ 15 min). |
| NFR-P-04 | Live collaboration edit propagation MUST be < 250 ms p95 between connected clients. |

### 4.2 Scalability

| ID | Requirement |
| --- | --- |
| NFR-S-01 | Each stateless service MUST scale horizontally with no shared in-process state. |
| NFR-S-02 | The system MUST sustain **10,000 concurrent WebSocket connections** in load tests (BlazeMeter — see [`10`](./10-testing-qa.md)) without dropping messages. |
| NFR-S-03 | The transcription/render pipeline MUST scale on **queue depth (Kafka consumer lag)**, not CPU alone, and scale toward zero when idle. |

### 4.3 Availability & resilience

| ID | Requirement |
| --- | --- |
| NFR-A-01 | Control-plane API availability target **99.9%** monthly. |
| NFR-A-02 | Failure of an async worker (transcription/render) MUST NOT lose the job; it MUST be retried and, on repeated failure, land in a DLQ with alerting. |
| NFR-A-03 | A single AZ failure MUST NOT cause data loss or > 5 min of downtime (multi-AZ data tier). |
| NFR-A-04 | **RPO ≤ 5 min, RTO ≤ 1 hour** for user content and scripts (see [`13`](./13-runbooks-and-dr.md)). |

### 4.4 Security & privacy

| ID | Requirement |
| --- | --- |
| NFR-SEC-01 | No backend resource (service, DB, broker) is reachable from the public internet — only the ALB is (see [`06`](./06-security-architecture.md)). |
| NFR-SEC-02 | All data MUST be encrypted in transit (TLS 1.2+) and at rest (KMS). |
| NFR-SEC-03 | Authentication via Cognito; every API call MUST present a validated JWT; authorization MUST be enforced per workspace + role. |
| NFR-SEC-04 | User media in S3 MUST be private; access only via short-lived presigned URLs. |
| NFR-SEC-05 | Third-party OAuth tokens (YouTube/X) MUST be stored encrypted and never exposed to the client. |

### 4.5 Usability & accessibility

| ID | Requirement |
| --- | --- |
| NFR-U-01 | The web app MUST be a responsive PWA, installable, usable on tablet for review. |
| NFR-U-02 | Core editing flows SHOULD meet WCAG 2.1 AA (keyboard navigation, contrast, ARIA on the transcript editor). |

### 4.6 Operability & maintainability

| ID | Requirement |
| --- | --- |
| NFR-O-01 | 100% of infrastructure MUST be defined in Terraform; no click-ops in any shared environment. |
| NFR-O-02 | Every service MUST expose health (`/healthz` liveness, `/readyz` readiness) and metrics endpoints. |
| NFR-O-03 | Every request and Kafka event MUST carry a **correlation/trace ID** propagated end-to-end. |
| NFR-O-04 | A new engineer MUST be able to run the full stack locally in < 30 min following [`12`](./12-cost-and-local-dev.md). |

### 4.7 Cost

| ID | Requirement |
| --- | --- |
| NFR-C-01 | Local development MUST cost **$0** (no always-on cloud dependency for the inner loop). |
| NFR-C-02 | A budget alarm MUST fire before any environment exceeds a configured monthly threshold. |
| NFR-C-03 | Non-production cloud environments MUST be ephemeral (created by `apply`, destroyed after use). |

---

## 5. Out of scope (v1)

Restating from the overview with requirement-level precision, because scope creep is the enemy:

- **No frame-level / multitrack timeline editing.** The EDL + transcript model is the only editing
  surface. (Revisit only if data shows the text model blocks real jobs.)
- **No in-house ASR model.** Transcription is a pluggable provider behind the AI Transcription Service
  ([ADR-0005](./03-adr/)).
- **No platforms beyond YouTube + X.** The Analytics Service uses a per-platform adapter pattern so a
  third is *additive*, but none ship in v1.
- **No native mobile apps.** PWA only.
- **No team billing/invoicing complexity** beyond the three subscription tiers in the overview.

---

## 6. Traceability

Every FR/NFR here is traceable forward:

```
PRD requirement  ──►  Architecture component (02)  ──►  API/Event contract (05)
       │                                                        │
       └──►  Data model (04)                                    └──►  Cucumber scenario (10)
       └──►  SLO (11)  [for NFRs]
```

This chain is how we know the system *actually* does what the product needs — not just that it
compiles. When a requirement changes, this doc changes first, and the chain tells us what else must.
