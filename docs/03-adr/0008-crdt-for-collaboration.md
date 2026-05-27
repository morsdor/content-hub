# ADR-0008 — CRDTs for collaborative + offline editing

**Status:** Accepted · 2026-05-27

## Context

Two requirements are really the same problem: **live collaborative script editing** (FR-RT-02, many
users editing concurrently with live cursors) and **offline editing with clean sync** (FR-SC-04, a
client edits disconnected for an arbitrarily long time, then reconciles). Both require merging
concurrent edits to shared text **without lost updates** and **without a central lock**. An offline
client is just a replica that has been out of contact for a while — so a mechanism that handles offline
naturally also handles live collaboration.

## Decision

Use **CRDTs (Conflict-free Replicated Data Types)** — specifically a **sequence CRDT** for rich text
(e.g., a Yjs/Automerge-style structure) — as the data model for scripts. Each client holds a replica;
edits are commutative, associative, idempotent operations that merge to the same state regardless of
order or delay. The Workspace Service relays CRDT updates between connected clients (per-`scriptId`)
and persists the document; IndexedDB holds the offline replica + pending updates.

## Consequences

**Positive**
- **Offline and live collaboration are one implementation**, not two (directly satisfies FR-SC-04 +
  FR-RT-02). A reconnecting client merges cleanly no matter how long it was gone.
- **No central sequencing server / lock** — merges are deterministic and peer-friendly; the server is a
  relay + persistence point, not a bottleneck (helps WS scaling, [`11`](../11-scalability-resilience.md)).
- Mature libraries exist (Yjs, Automerge) — we integrate, we don't invent the algorithm.

**Negative (accepted)**
- **Metadata overhead** — CRDTs carry per-character/op bookkeeping; documents are larger than plain
  text. Acceptable for scripts (prose, not gigabytes).
- **No global invariants** — CRDTs guarantee convergence, not arbitrary business rules across the doc.
  Fine for free-form scripts; we don't impose structural constraints on prose.
- A real **learning curve**; debugging convergence issues is subtle. Mitigated by using a
  battle-tested library rather than hand-rolling.

## Alternatives considered

- **Operational Transformation (OT)** (classic Google Docs). Proven, but requires a central server to
  transform/sequence every op — a scaling chokepoint — and does **not** give offline-for-free.
  Rejected: CRDTs subsume the offline requirement at lower architectural risk for us.
- **Last-write-wins on whole document.** Trivial, but *loses concurrent edits* — unacceptable for
  collaboration; only tolerable for single-user drafts.
- **Pessimistic locking (one editor at a time).** Kills the collaboration feature outright. Rejected.

> **Scope discipline:** CRDTs apply to **scripts** (collaborative prose). The Kanban board, EDL, and
> structured records use ordinary transactional Postgres with optimistic concurrency — they don't need
> CRDT machinery, and applying it there would be over-engineering. Right tool, right place.
