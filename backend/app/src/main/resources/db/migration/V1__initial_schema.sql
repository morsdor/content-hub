-- V1 — ContentHub initial Postgres schema
-- All tables defined in docs/04-data-model.md §2 (PostgreSQL schema).
-- Flyway runs this once; Hibernate ddl-auto=validate then checks alignment.

CREATE EXTENSION IF NOT EXISTS citext;

-- ─────────────────────────────────────────────────────────────────────────────
-- §2.1  Identity & tenancy
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    cognito_sub     TEXT        NOT NULL UNIQUE,
    email           CITEXT      NOT NULL UNIQUE,
    display_name    TEXT        NOT NULL,
    avatar_url      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace (
    id              UUID PRIMARY KEY,
    name            TEXT        NOT NULL,
    plan            TEXT        NOT NULL DEFAULT 'solo'
                    CHECK (plan IN ('solo','team','studio')),
    created_by      UUID        NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE workspace_member (
    workspace_id    UUID NOT NULL REFERENCES workspace(id)  ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES app_user(id)   ON DELETE CASCADE,
    role            TEXT NOT NULL CHECK (role IN ('owner','editor','viewer')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX idx_member_user ON workspace_member(user_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- §2.2  Workspace: Kanban + scripts
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE board_column (
    id              UUID PRIMARY KEY,
    workspace_id    UUID        NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    position        INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE card (
    id               UUID PRIMARY KEY,
    workspace_id     UUID         NOT NULL REFERENCES workspace(id)     ON DELETE CASCADE,
    column_id        UUID         NOT NULL REFERENCES board_column(id),
    title            TEXT         NOT NULL,
    position         NUMERIC      NOT NULL,
    target_platforms TEXT[]       NOT NULL DEFAULT '{}',
    due_date         DATE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);
CREATE INDEX idx_card_board ON card(workspace_id, column_id, position);

CREATE TABLE script (
    id              UUID PRIMARY KEY,
    card_id         UUID        NOT NULL UNIQUE REFERENCES card(id) ON DELETE CASCADE,
    workspace_id    UUID        NOT NULL REFERENCES workspace(id)   ON DELETE CASCADE,
    crdt_state      BYTEA       NOT NULL,
    plain_text      TEXT        NOT NULL DEFAULT '',
    version         BIGINT      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- §2.3  Media + Edit Decision List
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE media_asset (
    id              UUID PRIMARY KEY,
    workspace_id    UUID        NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    card_id         UUID        REFERENCES card(id),
    s3_key          TEXT        NOT NULL,
    s3_bucket       TEXT        NOT NULL,
    content_type    TEXT        NOT NULL,
    size_bytes      BIGINT      NOT NULL,
    duration_ms     INTEGER,
    status          TEXT        NOT NULL DEFAULT 'uploading'
                    CHECK (status IN ('uploading','uploaded','transcribing','ready','failed')),
    transcript_id   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_workspace ON media_asset(workspace_id, status);

CREATE TABLE edit_decision_list (
    id              UUID PRIMARY KEY,
    card_id         UUID        NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    workspace_id    UUID        NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    version         BIGINT      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE edl_segment (
    id              UUID    PRIMARY KEY,
    edl_id          UUID    NOT NULL REFERENCES edit_decision_list(id) ON DELETE CASCADE,
    media_asset_id  UUID    NOT NULL REFERENCES media_asset(id),
    seq             INTEGER NOT NULL,
    source_start_ms INTEGER NOT NULL,
    source_end_ms   INTEGER NOT NULL,
    CHECK (source_end_ms > source_start_ms)
);
CREATE UNIQUE INDEX idx_edl_seq ON edl_segment(edl_id, seq);

-- ─────────────────────────────────────────────────────────────────────────────
-- §2.4  Billing & external grants
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE subscription (
    id                   UUID PRIMARY KEY,
    workspace_id         UUID        NOT NULL UNIQUE REFERENCES workspace(id),
    plan                 TEXT        NOT NULL CHECK (plan IN ('solo','team','studio')),
    status               TEXT        NOT NULL,
    provider_customer_id TEXT,
    current_period_end   TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_meter (
    id              UUID    PRIMARY KEY,
    workspace_id    UUID    NOT NULL REFERENCES workspace(id),
    metric          TEXT    NOT NULL,
    period          DATE    NOT NULL,
    quantity        NUMERIC NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, metric, period)
);

CREATE TABLE platform_grant (
    id                   UUID PRIMARY KEY,
    workspace_id         UUID        NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    platform             TEXT        NOT NULL CHECK (platform IN ('youtube','x')),
    external_account_id  TEXT        NOT NULL,
    access_token_enc     BYTEA       NOT NULL,
    refresh_token_enc    BYTEA,
    scopes               TEXT[]      NOT NULL DEFAULT '{}',
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, platform, external_account_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- §2.5  Transactional outbox  (ADR-0006)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE outbox (
    id              UUID    PRIMARY KEY,
    aggregate_type  TEXT    NOT NULL,
    aggregate_id    UUID    NOT NULL,
    event_type      TEXT    NOT NULL,
    payload         JSONB   NOT NULL,
    trace_id        TEXT    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);
-- Partial index keeps the relay poll query O(pending rows), not O(all-time rows)
CREATE INDEX idx_outbox_unsent ON outbox(created_at) WHERE sent_at IS NULL;
