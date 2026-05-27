# 05 — API & Event Contracts

**Audience:** builders. These are the **contracts** between parts of the system: the REST APIs clients
call, the WebSocket protocol for real-time, and the Kafka topics services integrate over. Contracts
are the most important artifact in a distributed system — they are what let parts evolve
*independently*. Break a contract silently and you break a consumer you can't see.

> **Contract-first discipline:** the REST contracts here are the source for an OpenAPI spec; the Kafka
> events here are the source for schema-registry schemas. Both are **verified in CI** with consumer
> contract tests (Pact) and schema-compatibility checks ([`10`](./10-testing-qa.md),
> [`08`](./08-deployment-cicd.md)) so a breaking change fails the build, not production.

---

## 1. REST API conventions (apply to every endpoint)

| Concern | Convention |
| --- | --- |
| Base | `https://api.contenthub.app/v1/…` — version in the path; `v1` never breaks. |
| Auth | `Authorization: Bearer <Cognito JWT>` on every call; validated at the Gateway ([ADR-0007](./03-adr/0007-spring-cloud-gateway.md)). |
| Tenancy | `workspaceId` in the path; the Gateway/service authorizes the caller's membership + role. |
| Tracing | `X-Request-Id` accepted or minted; becomes the `traceId` propagated through events + logs (NFR-O-03). |
| Errors | RFC 9457 *Problem Details* JSON: `{ "type", "title", "status", "detail", "instance", "traceId" }`. |
| Idempotency | Unsafe POSTs that create resources accept an `Idempotency-Key` header; replays return the original result. |
| Pagination | Cursor-based: `?limit=50&cursor=…` → `{ "items": [...], "nextCursor": "…" }`. Never offset pagination on hot lists. |
| Rate limits | Surfaced as `429` + `Retry-After`; coarse limits at the Gateway, per-plan limits in-service. |

> **Why cursor pagination, not `?page=2`:** offset pagination re-scans skipped rows (slow on large
> sets) and double-shows/skips items when data changes between pages. Cursors are O(1) and stable —
> the right default for the board, media lists, and metrics feeds.

---

## 2. REST contracts by service

Shown in compact OpenAPI-flavored form. Status/auth/error conventions above apply throughout.

### 2.1 Workspace Service

```http
POST   /v1/workspaces                         # create workspace (FR-WS-01)
GET    /v1/workspaces                         # workspaces I'm a member of
POST   /v1/workspaces/{wsId}/members          # invite member {email, role}        [owner]
GET    /v1/workspaces/{wsId}/board            # columns + cards in order (hot read; NFR-P-01)
POST   /v1/workspaces/{wsId}/cards            # create card (FR-WS-03)
PATCH  /v1/workspaces/{wsId}/cards/{cardId}   # move/rename {columnId?, position?, title?} (FR-WS-03)
GET    /v1/workspaces/{wsId}/cards/{cardId}/script   # load script (CRDT state + version)
PUT    /v1/workspaces/{wsId}/cards/{cardId}/script   # persist script (debounced autosave; FR-SC-02)
```

Example — create card:

```http
POST /v1/workspaces/a2b3/cards
Authorization: Bearer <jwt>
Idempotency-Key: 7e1c-...
Content-Type: application/json

{ "title": "Q3 Recap", "columnId": "col_ideas", "targetPlatforms": ["youtube","x"] }

201 Created
{ "id": "c4d5", "title": "Q3 Recap", "columnId": "col_ideas", "position": 4096.0,
  "createdAt": "2026-05-27T10:00:00Z" }
```

> Real-time board/script updates are **not** polled over REST — they arrive over the WebSocket (§3).
> REST is the authoritative read/write; WS is the live-update channel. (FR-WS-04, FR-RT-*)

### 2.2 Media Service

```http
POST   /v1/workspaces/{wsId}/media:initiate-upload   # → presigned S3 URL (FR-ME-01; see flow below)
POST   /v1/workspaces/{wsId}/media/{mediaId}:complete-upload   # signal bytes are in S3 → emits video.uploaded
GET    /v1/workspaces/{wsId}/media/{mediaId}         # status: uploading|transcribing|ready|failed
GET    /v1/workspaces/{wsId}/cards/{cardId}/edl      # current cut (EDL segments in seq order)
PATCH  /v1/workspaces/{wsId}/cards/{cardId}/edl      # apply edit ops (delete/reorder ranges) (FR-ME-04)
POST   /v1/workspaces/{wsId}/cards/{cardId}/render    # request render → emits render.requested (FR-ME-06)
GET    /v1/workspaces/{wsId}/renders/{renderId}      # render status + (when ready) download URL
```

**The resumable, direct-to-S3 upload handshake (why it's two calls):**

```
1. POST …/media:initiate-upload {filename, contentType, sizeBytes}
   → 201 { mediaId, uploadUrl (presigned S3 PUT, short TTL), s3Key }
2. Browser PUTs bytes directly to uploadUrl  ──────────────►  S3   (our compute never sees the bytes)
3. POST …/media/{mediaId}:complete-upload
   → Media Service verifies the object exists, writes media_asset row + outbox(video.uploaded) in ONE tx
   → 202 Accepted { mediaId, status: "transcribing" }
```

This keeps gigabyte payloads off the request path ([`02` §6](./02-system-architecture.md)) and makes
uploads resumable (S3 multipart) without our services buffering anything.

### 2.3 Analytics Service

```http
POST   /v1/workspaces/{wsId}/grants/{platform}:connect   # start OAuth (FR-AP-01) → redirect
GET    /v1/workspaces/{wsId}/grants                      # connected platforms (no tokens returned!)
POST   /v1/workspaces/{wsId}/cards/{cardId}/publish      # schedule publish {platforms, publishAt} (FR-AP-02)
GET    /v1/workspaces/{wsId}/cards/{cardId}/publications # per-platform publish status
GET    /v1/workspaces/{wsId}/analytics/funnel            # normalized cross-platform metrics (FR-AP-05)
```

> Note the absence of any endpoint that returns OAuth tokens to the client — NFR-SEC-05. Tokens live
> encrypted server-side and never cross the wire to the browser.

---

## 3. WebSocket protocol (real-time — FR-RT-*, FR-WS-04)

A single authenticated WSS connection per client to the **Workspace Service**, multiplexing
collaboration and notifications. Connection: `wss://api.contenthub.app/v1/ws?token=<jwt>` (JWT
validated on upgrade; rejected with `4401` close code if invalid).

**Envelope (every message):**

```json
{ "type": "<message-type>", "channel": "<scope>", "ts": "2026-05-27T10:00:00Z", "data": { } }
```

| Direction | `type` | Purpose |
| --- | --- | --- |
| C→S | `subscribe` / `unsubscribe` | join/leave a channel: `workspace:{id}` or `script:{id}` |
| C↔S | `crdt.update` | a CRDT change to a script (binary update, base64) — merges per [ADR-0008](./03-adr/0008-crdt-for-collaboration.md) |
| C↔S | `presence.cursor` | cursor/selection position for live cursors (FR-RT-01) |
| S→C | `board.changed` | a card moved/created in a workspace (FR-WS-04) |
| S→C | `job.completed` | render/transcription done → triggers browser notification (FR-RT-03) |
| S→C | `presence.state` | who's online in the channel (FR-RT-04) |
| S→C | `error` | protocol/auth errors with a `traceId` |

**Delivery semantics:** WS is best-effort/at-most-once and a connection can drop. Therefore the WS is a
**live-update accelerator, not a source of truth** — on (re)connect the client re-fetches authoritative
state over REST, then applies live deltas. Missing a `board.changed` frame is harmless; the next REST
read reconciles. CRDT updates are the exception: they're idempotent and order-independent by
construction, so replaying or reordering them is safe.

> **Why route WS through the Workspace Service and fan out completion events via Kafka→WS:** a render
> worker has no idea which browser tabs care about its result, and shouldn't. It emits
> `render.finished` to Kafka; the Workspace Service (which *does* hold the connections) consumes it and
> pushes `job.completed` to exactly the subscribed clients. Clean separation of "did the work" from
> "knows who's watching." (See [`02` §7–8](./02-system-architecture.md).)

---

## 4. Kafka topic catalog (the integration backbone)

Topics **are** the contract between services ([ADR-0003](./03-adr/0003-kafka-event-broker.md)). Naming:
`<domain>.<event-in-past-tense>`. Events are **facts that already happened** (`video.uploaded`), never
commands — this keeps producers from assuming what consumers will do.

| Topic | Producer | Consumers | Key | Partitions | Retention |
| --- | --- | --- | --- | --- | --- |
| `video.uploaded` | Media | Transcription | `mediaAssetId` | 12 | 7d |
| `transcription.completed` | Transcription | Workspace (notify), Media | `mediaAssetId` | 12 | 7d |
| `transcription.failed` | Transcription | Workspace (notify), Media | `mediaAssetId` | 6 | 7d |
| `render.requested` | Media | Render worker | `renderId` | 12 | 7d |
| `render.finished` | Render worker | Workspace (notify), Media | `renderId` | 12 | 7d |
| `content.published` | Analytics | Workspace (notify) | `cardId` | 6 | 30d |
| `metrics.ingested` | Analytics | (future: search/BI) | `cardId` | 6 | 30d |
| `*.DLQ` | (any consumer) | on-call tooling, replay job | original key | 3 | 14d |

**Partitioning & ordering:** the **key determines ordering and parallelism**. We key by the entity
whose events must stay ordered (`mediaAssetId`, `renderId`) so all events for one asset land on one
partition and process in order, while different assets parallelize across partitions. Partition count
is set for headroom (you can add partitions but not easily reduce, and adding them reshuffles keys —
so start with comfortable room: 12 for high-volume pipeline topics).

> **Why key by `mediaAssetId` and not, say, `workspaceId`:** keying by workspace would serialize *all*
> of a busy workspace's media through one partition — a hot-partition bottleneck. Keying by the asset
> gives per-asset ordering (which is all we need) with maximum spread. Choosing the key is the single
> most consequential Kafka decision; get it from the ordering requirement, not by habit.

### 4.1 Event schema (example: `video.uploaded`)

All events share an envelope; the registry enforces it. JSON shown; we register **Avro/JSON Schema** in
a **Schema Registry** for compatibility enforcement.

```json
{
  "eventId":     "evt_01HZX...",          // ULID — the idempotency key for consumers (§5)
  "eventType":   "video.uploaded",
  "eventVersion":"1.0",
  "occurredAt":  "2026-05-27T10:00:00Z",
  "traceId":     "req_9f1c...",            // == X-Request-Id from the originating REST call (NFR-O-03)
  "workspaceId": "a2b3...",
  "data": {
    "mediaAssetId": "9f1c...",
    "s3Bucket":     "contenthub-prod-media",
    "s3Key":        "ws/a2b3/9f1c/source.mp4",
    "contentType":  "video/mp4",
    "sizeBytes":    734003200
  }
}
```

---

## 5. Idempotency, ordering, and exactly-once *effects*

The outbox ([ADR-0006](./03-adr/0006-transactional-outbox.md)) gives **at-least-once** delivery — so
duplicates *will* happen (relay retries, consumer rebalances, replays). The system stays correct
because **every consumer is idempotent**:

- **Dedupe on `eventId`** — each consumer records processed `eventId`s (a small table / Redis set with
  TTL) and no-ops on a repeat.
- **Or design naturally-idempotent effects** — e.g., "set `media_asset.status = transcribing`" is safe
  to apply twice; "increment a counter" is not (use the dedupe table for those).
- **Upsert, don't insert** — writing the transcript uses `mediaAssetId` as the key so a reprocessed
  `video.uploaded` overwrites rather than duplicates.

This is the contract that makes "exactly-once business effect" achievable on top of at-least-once
transport. **It is mandatory, not optional** — a non-idempotent consumer is a latent data-corruption
bug. CI contract tests assert idempotency by delivering every event twice ([`10`](./10-testing-qa.md)).

---

## 6. Dead-letter queues (DLQ) & poison messages

A message that fails processing repeatedly (bad data, a bug, a dependency down) must not block the
partition behind it forever. Policy:

```
consume → process
   ├─ success → commit offset
   └─ failure → retry with backoff (in-memory, N times)
                  └─ still failing → publish to <topic>.DLQ with failure metadata → commit offset
```

- **Retries** handle transient failures (ASR provider blip) — exponential backoff, capped attempts.
- **DLQ** captures the truly poisonous after retries are exhausted, with `{ originalEvent, error,
  stackTrace, attempts, firstFailedAt }`. This unblocks the partition (NFR-A-02).
- **Alerting**: any message landing in a DLQ pages on-call ([`13`](./13-runbooks-and-dr.md)); a
  **replay job** can re-emit DLQ messages to the source topic after a fix (Kafka's retained log makes
  this clean — [ADR-0003](./03-adr/0003-kafka-event-broker.md)).

---

## 7. Schema evolution & versioning (how contracts change without breaking)

| Mechanism | Rule |
| --- | --- |
| **REST** | Additive changes only within `v1` (new optional fields, new endpoints). Breaking changes → `v2` path; run both during migration. |
| **Kafka** | Schema Registry enforces **backward compatibility** by default: you may add optional fields; you may **not** remove/rename/retype existing ones. Breaking change → bump `eventVersion` and, if needed, a new topic; consumers migrate before the old is retired. |
| **WS** | Envelope is versioned; unknown `type`s are ignored by clients (forward-compatible). |

> **The discipline that makes independent deployment real:** *consumers must tolerate fields they don't
> know, and producers must never remove fields consumers might use.* This single rule — enforced by the
> registry in CI — is what lets the Media Service deploy on Tuesday and the Transcription Service deploy
> on Thursday without a coordinated big-bang release. Without it, "microservices" is just a distributed
> monolith that fails in more places.
