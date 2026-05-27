# 10 — Testing & QA

**Audience:** builders. The strategy for proving the system works — and keeps working as it changes.
The master plan names **Cucumber** (BDD in CI) and **BlazeMeter** (WebSocket load testing); this
chapter places them in a complete, layered strategy.

> **The economic argument for testing (the only one that matters):** a bug caught by a unit test costs
> seconds; the same bug caught in production costs an incident, a rollback, lost user trust, and your
> weekend. Tests are not bureaucracy — they are the mechanism that lets a small team **change code
> fearlessly**. A system you're afraid to deploy is a system that's already failing.

---

## 1. The test pyramid (where effort goes)

```
                ▲  fewer, slower, broader
         ┌──────────────┐
         │  E2E / BDD    │   Cucumber — full user journeys (J1) on a real stack    [few]
         ├──────────────┤
         │ Load / Chaos  │   BlazeMeter (WS @ scale), fault injection              [targeted]
         ├──────────────┤
         │ Integration   │   Testcontainers — real PG/Mongo/Kafka per service      [some]
         ├──────────────┤
         │ Contract      │   Pact — REST + event contracts between services        [per boundary]
         ├──────────────┤
         │   Unit        │   JUnit5 / Vitest — domain logic, EDL math, CRDT merge  [many, fast]
         └──────────────┘
                ▼  more, faster, focused
```

> **Why a pyramid and not an "ice-cream cone" (mostly E2E):** E2E tests are slow, flaky, and expensive
> to maintain; if they're your *primary* safety net, your suite takes an hour, fails randomly, and gets
> ignored. Push correctness down to fast unit/contract tests (the wide base) and reserve the slow E2E
> layer for proving a few critical journeys end-to-end. **Speed of the inner loop is a feature** — a
> test suite that runs in 2 minutes gets run constantly; one that takes 40 minutes gets skipped.

---

## 2. Unit tests — the wide base

- **JUnit5** (services) + **Vitest** (frontend). Pure, fast, no infrastructure.
- Focus on the **domain core** (hexagonal architecture, [`02` §4](./02-system-architecture.md) makes
  this easy — no Kafka/DB needed to test logic): the **EDL split/trim math** (FR-ME-04 — the most
  bug-prone, highest-value logic in the product), authorization rules, quota calculations, CRDT merge
  behavior, normalization of platform metrics.
- **Architecture tests (ArchUnit)** live here too: assert module boundaries hold
  ([ADR-0009](./03-adr/0009-modular-monolith-first.md)) — e.g., "the Media module must not import the
  Analytics module's internals." This keeps the modular monolith from quietly rotting into a big ball
  of mud.

```java
// The EDL edit is the heart of the product → it gets the most rigorous unit coverage.
@Test void deletingWordRange_removesOnlyThatSegment_andSourceIsUntouched() {
    var edl = Edl.of(segment("m1", 0, 60_000));          // one 60s clip
    var result = edl.deleteSourceRange("m1", 12_000, 13_400);  // remove "um so yeah"
    assertThat(result.segments()).containsExactly(
        segment("m1", 0, 12_000), segment("m1", 13_400, 60_000));  // split around the gap
    assertThat(result.sourceMutated()).isFalse();         // non-destructive invariant (FR-ME-04)
}
```

---

## 3. Contract tests — making microservices safe (Pact)

The defining risk of microservices is the **silent contract break** ([`05` §7](./05-api-and-event-contracts.md)).
**Consumer-Driven Contracts (Pact)** prevent it:

- The **consumer** (e.g., Workspace Service consuming `transcription.completed`) writes a test
  declaring exactly what it expects from the message/endpoint → produces a **pact**.
- The **provider** (Transcription Service) runs `pactVerify` in *its* CI against that pact → if the
  provider's output no longer satisfies the consumer's expectation, **the provider's build fails**.

```
 Workspace (consumer)  ──defines expectation──►  pact file  ──verified by──►  Transcription (provider) CI
        "I need {mediaAssetId, transcriptId} on transcription.completed"
```

> **Why this is worth the setup cost:** without contract tests, "deploy services independently" is a
> lie — you can't actually deploy the Transcription Service alone without risking every consumer.
> Pact turns the implicit, invisible coupling into an explicit, *tested* contract, so the build — not
> a production incident — tells you when you've broken a downstream consumer. This is the test layer
> that earns the right to the microservices architecture.

Both REST contracts and Kafka **event** contracts are covered; event schemas are *also* gated by the
Schema Registry compatibility check ([`08` §2](./08-deployment-cicd.md)) — belt and suspenders on the
most dangerous category of change.

---

## 4. Integration tests — real dependencies via Testcontainers

Mocks lie. A mocked Postgres passes while a real query has a syntax error, a missing index, or a
transaction-isolation surprise; a mocked Kafka hides serialization and rebalance bugs.

- **Testcontainers** spins up **real** PostgreSQL, DocumentDB-compatible Mongo, and Kafka in Docker
  *during the test*, so each service is verified against the actual engines it uses in production.
- Verifies the things only real infra reveals: SQL correctness + migrations (Flyway) apply cleanly,
  JPA mappings, **the outbox→Kafka→idempotent-consumer round trip** ([ADR-0006](./03-adr/0006-transactional-outbox.md)),
  and DocumentDB API-compatibility quirks ([ADR-0002](./03-adr/0002-polyglot-postgres-and-mongo.md)).

```java
@Testcontainers
class TranscriptionFlowIT {
  @Container static KafkaContainer kafka = new KafkaContainer(...);
  @Container static MongoDBContainer mongo = new MongoDBContainer(...);

  @Test void videoUploaded_isProcessedExactlyOnce_evenIfDeliveredTwice() {
    publish("video.uploaded", event);
    publish("video.uploaded", event);              // duplicate — at-least-once delivery (05 §5)
    awaitTranscriptionCompleted();
    assertThat(mongo.transcripts().count()).isEqualTo(1);   // idempotency invariant holds
  }
}
```

> **The duplicate-delivery test above is mandatory for every consumer.** Since the outbox guarantees
> at-least-once (not exactly-once) delivery, a consumer that isn't idempotent is a latent
> data-corruption bug. We assert idempotency in CI by *deliberately delivering every event twice* — if
> the consumer produces two transcripts, the build fails. This makes the [`05` §5](./05-api-and-event-contracts.md)
> rule executable, not aspirational.

---

## 5. End-to-end BDD — Cucumber (the master-plan tool)

**Cucumber** runs **Gherkin** scenarios (the same `Given/When/Then` written in the
[PRD](./01-product-requirements.md)) against a fully-deployed stack — in the **ephemeral QA
environment** ([`07`](./07-infrastructure-terraform.md), [`12`](./12-cost-and-local-dev.md)) so it's
production-fidelity, then torn down.

```gherkin
# features/transcription.feature  — executable form of PRD FR-ME-02/03 + the J1 journey
Feature: Upload and transcribe (the core spine)

  Scenario: A creator uploads a clip and sees an editable transcript
    Given I am signed in as an editor of workspace "MyChannel"
    When I upload "sample-60s.mp4" to a new card
    Then the media status becomes "transcribing" within 5 seconds
    And within 90 seconds I receive a "transcript ready" notification over WebSocket
    And the transcript text contains "today we're shipping"
    And deleting the words "um so yeah" removes that range from the cut
    And the source media in S3 is unchanged
```

- **BDD as living documentation:** the Gherkin is readable by non-engineers (you, as the Operator), is
  the acceptance criteria from the PRD, *and* is the automated test — one artifact, three jobs. The
  PRD's acceptance blocks were written in this form deliberately so they drop straight into Cucumber.
- **Wired into CI/CD:** runs on every promotion to QA ([`08` §6](./08-deployment-cicd.md)); a failed
  scenario blocks promotion to prod.
- **Scope discipline:** E2E is the *narrow top* of the pyramid — we cover the critical journeys (J1
  spine, auth + authorization/IDOR, publish flow), not every permutation (those live in fast unit
  tests). A handful of high-value E2E scenarios, kept green and fast, beats hundreds of flaky ones.

---

## 6. Load & performance testing — BlazeMeter (the master-plan tool)

The headline NFR is **10,000 concurrent WebSocket connections** (NFR-S-02) for live collaboration —
exactly what **BlazeMeter** (managed JMeter/Gatling at scale) is for.

| Test | Target | Validates |
| --- | --- | --- |
| **WebSocket soak** | 10k concurrent WS, sustained edits/cursors | NFR-S-02, NFR-P-04; no dropped frames; Workspace pod + cross-pod relay scaling ([`11`](./11-scalability-resilience.md)) |
| API stress | ramp request rate to find the knee | NFR-P-01 latency under load; autoscaling kicks in (HPA) |
| **Pipeline flood** | burst N uploads → watch consumer lag + KEDA | NFR-S-03; transcription scales on lag; nothing dropped |
| Spike | sudden 10× traffic | graceful degradation, rate limiting, recovery |

- **Run against the ephemeral QA cloud**, not local — load tests are only meaningful against
  production-like infra (real ALB, real MSK, real autoscaling). Apply → load test → capture results →
  destroy ([`07` §6](./07-infrastructure-terraform.md)). This is *the* reason the ephemeral-env
  capability exists.
- **Tie results to SLOs** ([`11`](./11-scalability-resilience.md)): a load test "passes" only if p99
  latency and error rate stay within SLO at target concurrency, *and* the system autoscales and
  recovers — not merely "it didn't crash."

> **Why load-test the WebSocket path specifically and obsessively:** stateless REST scales boringly
> (add pods). **Stateful WebSocket connections are the hard scaling problem** in this system
> ([`11` §WS](./11-scalability-resilience.md)) — 10k held connections, cross-pod edit relay, presence
> fan-out. This is exactly where naive designs fall over at scale, so it's where we spend our load-test
> budget before users find the limits for us.

---

## 7. Chaos & resilience testing

Resilience claims ([`11`](./11-scalability-resilience.md)) are worthless until tested by *injecting the
failures they claim to survive*:

- **Kill a pod mid-request** → traffic reroutes, no user-visible error (graceful shutdown drains
  in-flight work).
- **Kill the ASR provider** (block egress) → jobs retry, then DLQ + alert; **no event lost**; failover
  to secondary adapter ([ADR-0005](./03-adr/0005-third-party-asr.md)).
- **Sever a DB connection / fail over RDS** → connection pool recovers; circuit breaker opens then
  closes ([`11`](./11-scalability-resilience.md)).
- **Partition Kafka / induce consumer rebalance** → idempotency holds; processing resumes; lag drains.
- **AZ failure drill** → Multi-AZ failover within RTO ([`13`](./13-runbooks-and-dr.md)).

Start as **scheduled game-days** in QA (manual fault injection); graduate to automated chaos
experiments. *Don't* run chaos in prod until the system has earned that confidence — chaos engineering
is a maturity destination, not a starting point for a solo build.

---

## 8. Quality gates (what must pass to ship)

Consolidated from [`08`](./08-deployment-cicd.md) — the bars a change clears on its way to prod:

| Gate | Bar |
| --- | --- |
| Unit + Arch tests | 100% pass; coverage ≥ target on domain core (not chasing 100% everywhere) |
| Contract tests (Pact) | all consumer pacts satisfied |
| Schema compatibility | no breaking Kafka/REST change ([`05` §7](./05-api-and-event-contracts.md)) |
| Integration (Testcontainers) | pass, incl. duplicate-delivery idempotency |
| Security scans | no critical CVEs, no secrets, IaC clean ([`06`](./06-security-architecture.md)) |
| BDD (Cucumber) on QA | critical journeys green |
| Load (pre-release / scheduled) | SLOs hold at target concurrency |

> **On coverage targets:** we set a meaningful bar on the **domain core** (EDL math, authz, CRDT,
> quotas) and don't fetishize a global percentage. 100% coverage of getters proves nothing; thorough
> coverage of the EDL split logic prevents the bug that silently corrupts someone's video. Aim tests
> where the **risk and value** are, not where the coverage number is easiest to inflate.

---

## 9. Test data & environment management

- **Deterministic fixtures** — builders/factories for users, workspaces, media; seed scripts for the
  ephemeral QA env.
- **Synthetic media** — a small library of short sample clips with known transcripts so transcription
  assertions are stable and don't depend on a live ASR's exact output (mock the ASR adapter in
  integration; use the real provider only in select E2E).
- **No production data in tests** — privacy ([`06`](./06-security-architecture.md)); generate or
  anonymize.
- **Isolation** — each test run gets a clean schema/namespace (Testcontainers gives this for free; the
  ephemeral QA env is clean by construction — it was just `terraform apply`'d).
