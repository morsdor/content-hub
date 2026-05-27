# ADR-0005 — Integrate a 3rd-party ASR provider, don't build one

**Status:** Accepted · 2026-05-27

## Context

The product's magic is **editing video by editing its transcript**. That requires accurate,
**word-level time-aligned** speech-to-text (ASR). Building and operating a competitive ASR model
requires ML expertise, GPUs, training data, and continuous tuning — none of which is our
differentiator. Our differentiator is the *editing experience* on top of the transcript.

## Decision

Treat ASR as a **pluggable external capability** behind the AI Transcription Service. The service
exposes a narrow internal `TranscriptionProvider` port; concrete adapters wrap a managed ASR
(e.g., AWS Transcribe, Deepgram, AssemblyAI, or an OpenAI/Whisper-class API). The rest of the system
knows only our normalized transcript schema ([`04`](../04-data-model.md)), never the provider's.

## Consequences

**Positive**
- We spend our scarce effort on the editor, not on ML ops.
- **Provider portability** — the port/adapter boundary lets us switch or A/B providers on accuracy,
  latency, language support, and price without touching domain logic. This is an Anti-Corruption Layer.
- Cost scales with usage (per-minute ASR pricing) and maps cleanly to the per-plan transcription quota
  in the business model ([`00` §business case](../00-overview.md)).

**Negative (accepted)**
- **Vendor dependency** on accuracy, uptime, latency, and pricing changes. Mitigated by the adapter
  abstraction (swap providers) and by treating transcription as retryable/async (a provider blip
  delays, doesn't fail, the job).
- **Per-minute COGS** is now a variable cost we must meter and cap (per-plan quotas, NFR-C).
- Sending user audio to a third party has **privacy/compliance** implications — must be in the DPA,
  the ToS, and the threat model ([`06`](../06-security-architecture.md)).

## Alternatives considered

- **Self-host an open ASR model (Whisper) on GPU nodes.** Removes per-minute vendor cost and the
  privacy concern, but adds GPU infra, model ops, and scaling complexity. Rejected for v1; revisit if
  ASR COGS becomes the dominant cost — the adapter boundary means this is a contained change, not a
  rewrite. (Would supersede this ADR.)
- **AWS Transcribe specifically, hard-wired.** Keeps everything in AWS, but hard-wiring any single
  provider violates the portability we want given how fast the ASR market moves.

> **The principle this encodes:** *buy undifferentiated heavy lifting, build your differentiator.*
> ASR is a commodity improving monthly across many vendors; the transcript-driven editor is ours.
