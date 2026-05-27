# 04 — Data Model

**Audience:** builders. This is the concrete schema: what tables and documents exist, which service
owns them, how they're indexed, and where consistency boundaries lie. It realizes the entities in the
[PRD](./01-product-requirements.md) on the [polyglot stores](./03-adr/0002-polyglot-postgres-and-mongo.md)
from the architecture.

> **The governing rule: one writer per dataset.** Each table/collection has exactly one owning service
> that may write it ([`02` §3.1](./02-system-architecture.md)). Other services read via that service's
> API or via events — **never** by reaching into another service's database. This is what makes the
> modular-monolith → microservices extraction ([ADR-0009](./03-adr/0009-modular-monolith-first.md))
> possible: data ownership is already partitioned even while the code is one deployable.

---

## 1. Data ownership map

| Store | Dataset | Owning service | Why this store |
| --- | --- | --- | --- |
| PostgreSQL | users, workspaces, members, cards, scripts (metadata + CRDT blob), media, EDLs, billing, OAuth grants, **outbox** | Workspace / Media / Analytics | Relational, transactional, constrained |
| MongoDB | transcripts (word-timed), platform metrics payloads | Transcription / Analytics | Nested, irregular, schema-drifting |
| S3 | raw media (immutable), rendered outputs | Media | Large binary blobs |

---

## 2. PostgreSQL schema

Conventions: `uuid` primary keys (generated app-side or `gen_random_uuid()`), `timestamptz` in UTC,
`created_at`/`updated_at` on every table, soft-delete via `deleted_at` where user-recoverable, and
**foreign keys enforced** (we want the DB to protect invariants). Migrations are managed by **Flyway**
([`08`](./08-deployment-cicd.md)); this DDL is the conceptual target, not a hand-applied script.

### 2.1 Identity & tenancy

```sql
-- Mirrors the Cognito user (authentication lives in Cognito; this is our app-side projection).
CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    cognito_sub     TEXT NOT NULL UNIQUE,          -- the Cognito subject claim; the join key to identity
    email           CITEXT NOT NULL UNIQUE,        -- case-insensitive
    display_name    TEXT NOT NULL,
    avatar_url      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A Workspace is the tenant boundary: a content business / channel.
CREATE TABLE workspace (
    id              UUID PRIMARY KEY,
    name            TEXT NOT NULL,
    plan            TEXT NOT NULL DEFAULT 'solo'    -- solo | team | studio (see billing)
                    CHECK (plan IN ('solo','team','studio')),
    created_by      UUID NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

-- Membership + role. Authorization (FR-WS-01) lives HERE, in our domain — not in Cognito (ADR-0004).
CREATE TABLE workspace_member (
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role            TEXT NOT NULL CHECK (role IN ('owner','editor','viewer')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX idx_member_user ON workspace_member(user_id);   -- "what workspaces am I in?"
```

> **Why `workspace_id` is on nearly every table below:** it is the **tenant isolation key**. Every
> query is scoped by it, every authorization check resolves through it, and it is the natural
> partition key if we ever shard. Multi-tenancy enforced by always-present `workspace_id` + row-level
> checks is simpler and safer than database-per-tenant at this scale.

### 2.2 Workspace: Kanban + scripts

```sql
CREATE TABLE board_column (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    position        INTEGER NOT NULL,               -- order on the board
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A Card == a content project moving across the board (FR-WS-03).
CREATE TABLE card (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    column_id       UUID NOT NULL REFERENCES board_column(id),
    title           TEXT NOT NULL,
    position        NUMERIC NOT NULL,               -- fractional ranking for cheap reordering (see note)
    target_platforms TEXT[] NOT NULL DEFAULT '{}',  -- e.g. {youtube,x}
    due_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_card_board ON card(workspace_id, column_id, position);

-- A Script belongs to a card. The collaborative text lives as a CRDT blob (ADR-0008);
-- a denormalized plain-text projection supports search.
CREATE TABLE script (
    id              UUID PRIMARY KEY,
    card_id         UUID NOT NULL UNIQUE REFERENCES card(id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    crdt_state      BYTEA NOT NULL,                 -- serialized CRDT doc (Yjs/Automerge update)
    plain_text      TEXT NOT NULL DEFAULT '',       -- projection for search / preview
    version         BIGINT NOT NULL DEFAULT 0,      -- monotonic; optimistic-concurrency guard
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

> **Two design notes worth the ink:**
> - **Fractional `position` (NUMERIC) for ordering.** To move a card between two others we set its
>   position to the average of its neighbors — an O(1) write, no renumbering of the column. Periodic
>   rebalancing avoids float exhaustion. Integer positions would force rewriting every card on each
>   drag — a needless write storm for a drag-heavy UI.
> - **CRDT as `BYTEA` + plain-text projection.** The authoritative collaborative state is the opaque
>   CRDT blob (the client library understands it); we *also* store a flattened `plain_text` so the DB
>   can do search/preview without deserializing CRDTs. The projection is derived, never authoritative.

### 2.3 Media + the Edit Decision List (the core editing model)

This realizes **FR-ME-04** — editing video by editing text — as data.

```sql
-- Metadata about an uploaded source. The bytes live in S3 (immutable); this row points at them.
CREATE TABLE media_asset (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    card_id         UUID REFERENCES card(id),
    s3_key          TEXT NOT NULL,                  -- object key in the private media bucket
    s3_bucket       TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    size_bytes      BIGINT NOT NULL,
    duration_ms     INTEGER,                        -- filled after probe
    status          TEXT NOT NULL DEFAULT 'uploading'
                    CHECK (status IN ('uploading','uploaded','transcribing','ready','failed')),
    transcript_id   TEXT,                           -- points to the Mongo transcript doc (cross-store ref)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_workspace ON media_asset(workspace_id, status);

-- An EDL is the "cut": an ordered list of segments referencing time ranges of source media.
-- Editing text mutates these rows; the source media is NEVER modified.
CREATE TABLE edit_decision_list (
    id              UUID PRIMARY KEY,
    card_id         UUID NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    version         BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE edl_segment (
    id              UUID PRIMARY KEY,
    edl_id          UUID NOT NULL REFERENCES edit_decision_list(id) ON DELETE CASCADE,
    media_asset_id  UUID NOT NULL REFERENCES media_asset(id),
    seq             INTEGER NOT NULL,               -- order of this segment in the cut
    source_start_ms INTEGER NOT NULL,               -- in-point in the SOURCE media
    source_end_ms   INTEGER NOT NULL,               -- out-point in the SOURCE media
    CHECK (source_end_ms > source_start_ms)
);
CREATE UNIQUE INDEX idx_edl_seq ON edl_segment(edl_id, seq);
```

> **How a text deletion becomes a video edit (the whole product, in three sentences):** the transcript
> (in Mongo, §3) maps every word to a `(media_asset_id, start_ms, end_ms)`. When the user deletes a run
> of words, the client computes the affected time ranges and the Media Service **splits/trims/removes
> `edl_segment` rows** so the cut skips them. *Preview* plays the segments in `seq` order by seeking the
> source; *render* concatenates them into a new S3 object. The source `media_asset` is immutable — undo
> is free, and two editors can propose different EDLs over the same footage.

### 2.4 Billing & external grants

```sql
CREATE TABLE subscription (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL UNIQUE REFERENCES workspace(id),
    plan                TEXT NOT NULL CHECK (plan IN ('solo','team','studio')),
    status              TEXT NOT NULL,              -- active | past_due | canceled
    provider_customer_id TEXT,                      -- e.g., Stripe customer
    current_period_end  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Metered usage that drives overage billing + quota enforcement (NFR-C, ADR-0005).
CREATE TABLE usage_meter (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspace(id),
    metric          TEXT NOT NULL,                  -- transcription_minutes | render_minutes | storage_gb
    period          DATE NOT NULL,                  -- billing month
    quantity        NUMERIC NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, metric, period)
);

-- OAuth grants for YouTube/X. Tokens are ENCRYPTED at the application layer before storage (NFR-SEC-05).
CREATE TABLE platform_grant (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    platform        TEXT NOT NULL CHECK (platform IN ('youtube','x')),
    external_account_id TEXT NOT NULL,
    access_token_enc  BYTEA NOT NULL,               -- envelope-encrypted (KMS data key)
    refresh_token_enc BYTEA,
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, platform, external_account_id)
);
```

### 2.5 The Outbox (reliable eventing — [ADR-0006](./03-adr/0006-transactional-outbox.md))

```sql
CREATE TABLE outbox (
    id              UUID PRIMARY KEY,
    aggregate_type  TEXT NOT NULL,                  -- e.g. 'media_asset'
    aggregate_id    UUID NOT NULL,
    event_type      TEXT NOT NULL,                  -- e.g. 'video.uploaded'  (matches Kafka topic, see 05)
    payload         JSONB NOT NULL,
    trace_id        TEXT NOT NULL,                  -- propagated end-to-end (NFR-O-03)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ                     -- NULL until the relay publishes it
);
CREATE INDEX idx_outbox_unsent ON outbox(created_at) WHERE sent_at IS NULL;  -- relay scans only unsent
```

> The partial index `WHERE sent_at IS NULL` keeps the relay's poll query cheap forever — it scans only
> the small set of pending rows, not the entire (ever-growing) outbox history. Sent rows are pruned by
> a retention job.

---

## 3. MongoDB collections

Mongo holds the data whose shape is **nested and unstable** — exactly where rigid DDL hurts
([ADR-0002](./03-adr/0002-polyglot-postgres-and-mongo.md)).

### 3.1 `transcripts` — the word-timed transcript (owned by Transcription Service)

This is the bridge between text and time that powers FR-ME-03/04.

```json
{
  "_id": "trx_01HZX...",
  "mediaAssetId": "9f1c...-uuid",        // cross-store ref back to Postgres media_asset
  "workspaceId":  "a2b3...-uuid",
  "provider":     "deepgram",            // which ASR produced this (ADR-0005); normalized below
  "language":     "en",
  "durationMs":   612000,
  "status":       "completed",
  "createdAt":    "2026-05-27T10:00:00Z",
  "segments": [
    {
      "speaker": "S1",
      "startMs": 0,
      "endMs":   4200,
      "text":    "So today we're shipping the thing.",
      "words": [
        { "w": "So",     "startMs": 0,    "endMs": 180,  "conf": 0.99 },
        { "w": "today",  "startMs": 180,  "endMs": 520,  "conf": 0.98 },
        { "w": "we're",  "startMs": 520,  "endMs": 760,  "conf": 0.97 }
        // ... every word carries its own start/end → this is what maps text edits to EDL cuts
      ]
    }
  ]
}
```

> **Why the whole transcript is one document, not a `words` table with millions of rows:** a transcript
> is always read and written as a unit (you load the whole thing into the editor; you never query "all
> words across all videos where conf < 0.5"). One document = one round-trip, no join. A 10-minute video
> is a few thousand words — comfortably within Mongo's 16 MB document limit. *If* a single asset ever
> approached that limit (hours of audio), we'd shard by segment; for our media lengths, one doc is
> correct. This is the document store earning its place.

### 3.2 `platform_metrics` — ingested YouTube/X engagement (owned by Analytics Service)

The raw, irregular payloads from FR-AP-04. We store the provider's response **verbatim** plus a small
normalized envelope, so a platform adding fields never breaks us.

```json
{
  "_id": "met_01HZ...",
  "workspaceId": "a2b3...-uuid",
  "cardId":      "c4d5...-uuid",
  "platform":    "youtube",
  "externalId":  "yt_video_abc123",
  "fetchedAt":   "2026-05-27T11:00:00Z",
  "normalized": {                         // OUR stable shape, computed across platforms for the funnel (FR-AP-05)
    "views": 10432, "likes": 812, "comments": 96, "watchTimeMin": 5210, "shares": 41
  },
  "raw": { /* the platform's full nested response, stored as-is — the schema-drift shock absorber */ }
}
```

---

## 4. Indexing strategy

Indexes are designed from the **query patterns**, not guessed. Key ones:

| Store | Index | Serves |
| --- | --- | --- |
| PG | `card(workspace_id, column_id, position)` | render a board column in order (the hot read) |
| PG | `workspace_member(user_id)` | "which workspaces am I in" on every request |
| PG | `media_asset(workspace_id, status)` | dashboards + worker pickups by status |
| PG | partial `outbox WHERE sent_at IS NULL` | the relay poll loop (stays cheap forever) |
| PG | `edl_segment(edl_id, seq)` unique | ordered cut playback + integrity |
| Mongo | `{ mediaAssetId: 1 }` on `transcripts` | load transcript for an asset (the only hot lookup) |
| Mongo | `{ workspaceId: 1, platform: 1, fetchedAt: -1 }` on `platform_metrics` | funnel queries + latest-N |

> **Rule of thumb encoded here:** index the columns in your `WHERE` + `ORDER BY`, lead composite
> indexes with the equality predicate (`workspace_id`) then the range/sort column. Don't over-index —
> every index is a write-time cost. We add indexes when a query plan or Datadog DB metric demands one,
> and we review them, rather than speculatively indexing everything.

---

## 5. Consistency boundaries (the part people get wrong)

Because we use multiple stores, we must be explicit about *where* we have strong consistency and where
we accept eventual consistency.

| Boundary | Guarantee | Mechanism |
| --- | --- | --- |
| Within one Postgres write (card move, billing) | **Strong / ACID** | single DB transaction |
| Domain change **+** its event | **Atomic** | Transactional Outbox ([ADR-0006](./03-adr/0006-transactional-outbox.md)) — same tx |
| Postgres `media_asset.transcript_id` ↔ Mongo `transcripts` | **Eventually consistent** | set after `transcription.completed`; UI shows "transcribing…" until then |
| Cross-service reactions (upload → transcript → notify) | **Eventually consistent** | Kafka events; idempotent consumers |
| Collaborative script across replicas | **Strong eventual (convergent)** | CRDT ([ADR-0008](./03-adr/0008-crdt-for-collaboration.md)) |

> **Why this table is the most important thing in the doc:** most "weird bugs" in distributed systems
> are really *someone assumed strong consistency across a boundary that's only eventually consistent.*
> Stating the boundaries explicitly — and designing the UI to tolerate the eventual ones (optimistic
> updates, "transcribing…" states, sync indicators) — is the difference between a system that feels
> solid and one that feels haunted.

---

## 6. Data lifecycle & retention (pointer)

Backups, PITR, RPO/RTO, S3 versioning/lifecycle, and GDPR-style deletion are covered in
[`13-runbooks-and-dr.md`](./13-runbooks-and-dr.md) and [`06-security-architecture.md`](./06-security-architecture.md).
The short version: Postgres + DocumentDB run **Multi-AZ with automated backups + PITR**; S3 uses
**versioning + lifecycle** (raw media to Infrequent Access after N days); transcripts/metrics are
regenerable from source or platform and so have a lighter retention bar.
