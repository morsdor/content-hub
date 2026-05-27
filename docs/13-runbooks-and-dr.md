# 13 — Runbooks & Disaster Recovery

**Audience:** on-call (you). When something breaks at 2 a.m., this is the document you open. Everything
here is **actionable** — symptoms, diagnosis steps, and fixes — not theory. Every alert in
[`09` §7](./09-observability.md) links to a runbook here.

> **The philosophy of a good runbook:** during an incident, your cognitive capacity is *halved* by
> stress. A runbook exists so you don't have to *think* — you *follow*. Each one starts with the
> symptom (how you got here), gives diagnosis steps in order, and ends with the fix and an escalation
> path. Written calm, executed panicked.

---

## 1. Incident response framework

```
 DETECT (alert/report) → TRIAGE (severity?) → MITIGATE (stop the bleeding) →
   → DIAGNOSE (root cause) → RESOLVE (fix) → RECOVER (verify healthy) → LEARN (postmortem)
```

**Severity levels** (set expectations + response):

| Sev | Meaning | Example | Response |
| --- | --- | --- | --- |
| **SEV1** | Users can't use core product; data at risk | API down; data-loss event | drop everything; mitigate now |
| **SEV2** | Major degradation | transcription badly backed up; publish failing | urgent; within the hour |
| **SEV3** | Minor / single-feature | one platform's metrics stale | next business day |

**First move is always MITIGATE, then diagnose.** Restore service first (roll back, scale up, fail
over); find the root cause second. Users don't care *why* it's down while it's down.

> **The cardinal rule for a solo operator:** *mitigate before you investigate.* The instinct to find
> the root cause first is how a 5-minute outage becomes a 2-hour one. Roll back the suspicious deploy
> ([`08` §7](./08-deployment-cicd.md)), *then* open the logs. The deploy marker in Datadog
> ([`09` §4](./09-observability.md)) usually answers "what changed?" in one glance.

---

## 2. Runbooks (common incidents)

Each: **Symptom → Diagnose → Mitigate → Resolve.**

### RB-1 — API is down / 5xx spike (SEV1)
- **Symptom:** availability SLO burn alert; users report errors.
- **Diagnose:** Datadog — which service? Gateway or downstream? Check the **deploy marker** — did
  something just ship? Check pod health (crashloop? OOM?) and DB saturation.
- **Mitigate:** if correlated with a deploy → **roll back** (`git revert` the digest bump → ArgoCD, or
  Argo Rollouts auto-rollback). If resource-starved → bump replicas / check HPA.
- **Resolve:** fix forward; add the missing test that would've caught it; postmortem if SEV1.

### RB-2 — Transcription/render badly backed up (SEV2)
- **Symptom:** consumer-lag runaway alert; users wait too long for transcripts (NFR-P-03 breach).
- **Diagnose:** Is KEDA scaling (replica count rising)? If not → KEDA/quota issue. If yes but lag still
  climbs → downstream bottleneck: check **ASR provider** latency/`429`s in Splunk (the usual culprit,
  worked example in [`09` §8](./09-observability.md)); check render compute saturation.
- **Mitigate:** raise KEDA max replicas; if ASR is throttling → **fail over to secondary ASR adapter**
  ([ADR-0005](./03-adr/0005-third-party-asr.md)); shed/deprioritize over-quota free-tier work so paid
  work drains.
- **Resolve:** the **retained Kafka log means nothing is lost** — backlog drains once capacity returns
  ([ADR-0003](./03-adr/0003-kafka-event-broker.md)). Right-size scaling ceilings.

### RB-3 — Messages landing in a DLQ (SEV2 — data at risk)
- **Symptom:** DLQ-depth alert ([`05` §6](./05-api-and-event-contracts.md)).
- **Diagnose:** inspect the DLQ message metadata (`error`, `stackTrace`, `originalEvent`). Poison data
  or a code bug? Same `traceId` → full Splunk story.
- **Mitigate:** the DLQ already unblocked the partition (by design) — other events flow. Confirm.
- **Resolve:** fix the bug → **replay** DLQ messages to the source topic via the replay job. If poison
  data → correct/drop with a documented decision. Never silently discard user-affecting events.

### RB-4 — Database degraded / failover (SEV1)
- **Symptom:** DB saturation/connection alert; slow or failing queries.
- **Diagnose:** Datadog DB metrics — connections maxed (pool leak / too many pods)? CPU? Lock waits? A
  slow query (find it via APM)?
- **Mitigate:** if connections exhausted → check PgBouncer; cap pod count; kill a runaway query. If the
  primary is failing → RDS **Multi-AZ auto-failover** promotes the standby (~60–120s); the app's
  connection pool reconnects (resilience patterns, [`11` §6](./11-scalability-resilience.md)).
- **Resolve:** add the missing index / fix the N+1 ([`04` §4](./04-data-model.md)); add a read replica
  if read-bound.

### RB-5 — A platform publish is failing (SEV2/3)
- **Symptom:** `content.published` failures; per-platform publish errors (FR-AP-03).
- **Diagnose:** which platform? Splunk for the ACL error — quota/`429` (FR-AP-06)? expired/revoked
  OAuth grant? ToS/API change?
- **Mitigate:** quota → backoff + reschedule (the saga retries the failed step without undoing the
  succeeded one, [`11` §7](./11-scalability-resilience.md)). Revoked grant → prompt the user to
  reconnect.
- **Resolve:** update the platform adapter if the API changed (contained by the ACL,
  [`02` §4](./02-system-architecture.md)).

### RB-6 — WebSocket collaboration broken / not propagating (SEV2)
- **Symptom:** edits not syncing between users; presence wrong.
- **Diagnose:** WS connections established (Datadog)? The cross-pod **relay** healthy
  ([`11` §4](./11-scalability-resilience.md))? CRDT errors in Splunk?
- **Mitigate:** clients auto-reconnect and re-sync over REST ([`05` §3](./05-api-and-event-contracts.md)),
  so the authoritative state is safe; restart/scale the relay if it's the fault.
- **Resolve:** fix the relay; load-test the fix ([`10` §6](./10-testing-qa.md)).

### RB-7 — Suspected security incident (SEV1)
- **Symptom:** anomalous auth (Cognito), unexpected data access, leaked-credential alert.
- **Mitigate:** rotate the affected credential immediately (Secrets Manager); revoke sessions; if a
  grant/token leaked, revoke it; tighten the relevant SG/IAM. Preserve logs (don't destroy evidence).
- **Resolve:** scope the blast radius using the least-privilege boundaries ([`06`](./06-security-architecture.md));
  follow disclosure obligations; postmortem mandatory.

---

## 3. Disaster Recovery — objectives

| Objective | Target | Meaning |
| --- | --- | --- |
| **RPO** (Recovery Point) | **≤ 5 min** | max acceptable *data loss* — we can lose at most ~5 min of writes |
| **RTO** (Recovery Time) | **≤ 1 hr** | max acceptable *downtime* to restore service |

These (from NFR-A-04, [`01` §4.3](./01-product-requirements.md)) drive every backup/replication choice
below. RPO is about **backup frequency**; RTO is about **restore speed**.

---

## 4. Backup & replication strategy

| Data store | Mechanism | RPO contribution |
| --- | --- | --- |
| **PostgreSQL (RDS)** | Multi-AZ sync standby + automated backups + **PITR** (transaction logs) | PITR → seconds-granularity restore; ~5 min worst case |
| **MongoDB (DocumentDB)** | Multi-AZ replica set + automated snapshots + PITR | minutes |
| **S3 (media)** | **Versioning** (recover overwritten/deleted) + Cross-Region Replication for prod + lifecycle | near-zero (versioned) |
| **Kafka (MSK)** | Multi-AZ replication (replication factor 3); retained log | events durable across an AZ loss |
| **Transcripts/metrics** | regenerable (re-run ASR from source media; re-pull platform metrics) | lower bar — derived data |
| **Terraform state** | versioned S3 + lock table | recover a clobbered state |

> **Why some data gets a lighter backup bar:** transcripts can be *regenerated* from the immutable
> source media ([`04` §2.3](./04-data-model.md)), and platform metrics can be *re-pulled*. We spend our
> strongest durability guarantees on the data that is **irreplaceable** — user-uploaded media (S3
> versioning + CRR) and the relational system-of-record (PG Multi-AZ + PITR). Knowing what's
> regenerable vs. irreplaceable is what keeps backup cost proportionate to actual risk.

---

## 5. Restore procedures (tested, not hoped)

### DR-1 — Single AZ failure
- **Automatic.** RDS/DocumentDB fail over to the standby AZ; EKS reschedules Fargate pods to healthy
  AZs; MSK serves from in-AZ replicas. App reconnects via resilience patterns
  ([`11` §6](./11-scalability-resilience.md)). **No data loss; minutes of partial degradation.** This
  is why the data tier is Multi-AZ in prod ([`07` §5](./07-infrastructure-terraform.md)).

### DR-2 — Accidental/malicious data deletion (e.g., a bad migration)
1. Identify the timestamp just *before* the bad event (deploy marker / audit log).
2. **PITR-restore** the DB to that point (to a *new* instance — never overwrite the running one).
3. Reconcile: repoint the app, or selectively recover the affected rows.
4. Roll **forward** with a corrective migration ([`08` §5](./08-deployment-cicd.md)) — we don't
   auto-run down-migrations.

### DR-3 — Region failure (rare, highest-effort)
- Prod: re-`terraform apply` the stack to the DR region (infra is 100% code,
  [`07`](./07-infrastructure-terraform.md)), restore DBs from cross-region snapshots, S3 already
  cross-region replicated, repoint DNS. RTO measured in the low hours — acceptable for a rare regional
  event at our stage; revisit active-active if SLAs demand it.

> **The DR claim is only real if the restore is *tested*.** An untested backup is Schrödinger's
> backup — simultaneously working and broken until you try it. We run a **restore drill** in the
> ephemeral QA environment ([`12`](./12-cost-and-local-dev.md)) — restore a prod snapshot into a
> throwaway env and verify the data — on a schedule. Cheap (ephemeral, then destroyed) and the only
> way to *know* RPO/RTO are met rather than assumed.

---

## 6. Business continuity & data-subject obligations

- **Status communication** — a status page + a way to notify users during a SEV1. Even solo, "we know,
  we're on it" preserves trust.
- **Right-to-deletion** (GDPR-style, [`06` §10](./06-security-architecture.md)) — a documented,
  tested procedure that cascades a workspace/user deletion across **Postgres → Mongo → S3
  (including versioned objects)** and revokes platform grants. Deletion is a feature with a runbook,
  not an ad-hoc `DELETE`.
- **Vendor failure contingency** — ASR provider down → secondary adapter
  ([ADR-0005](./03-adr/0005-third-party-asr.md)); observability SaaS down → local/OTel fallback retains
  data ([`09`](./09-observability.md)). Single-vendor dependencies have a documented Plan B.

---

## 7. Postmortems (turning failure into durable improvement)

After every SEV1/SEV2:

- **Blameless** — the question is *"what about the system let this happen?"*, never *"who screwed up?"*
  Blame produces hiding; blamelessness produces fixes.
- **Template:** timeline (with `traceId`s), impact (who/how long/SLO budget burned), root cause (the
  *5 whys* — push past the proximate cause), what went well, **action items with owners + dates**.
- **Action items are tracked to completion** — a postmortem whose fixes never ship is theater. The most
  valuable output is usually *"add the test/alert/runbook that would have caught or guided this,"*
  feeding back into [`10`](./10-testing-qa.md) and [`09`](./09-observability.md).

> **Why a solo builder should still write postmortems:** not for a team to read — for *future you*, who
> will have completely forgotten the details by the next time something similar happens. A postmortem
> is a letter to yourself six months from now, and it's how the system gets monotonically more reliable
> instead of breaking the same way twice.

---

## 8. The on-call starter kit (quick reference)

| Need | Where |
| --- | --- |
| "What changed recently?" | Datadog deploy markers ([`09` §4](./09-observability.md)); Git history |
| "Trace one request/journey" | search the `traceId` in Splunk + Datadog APM ([`09`](./09-observability.md)) |
| "Roll back" | `git revert` the digest bump → ArgoCD ([`08` §7](./08-deployment-cicd.md)) |
| "Is the pipeline keeping up?" | Kafka consumer lag in Datadog ([`09` §4](./09-observability.md)) |
| "Recover infra" | `terraform apply` last known-good ([`07`](./07-infrastructure-terraform.md)) |
| "Restore data" | RDS/DocumentDB PITR (DR-2 above) |
| "Replay failed events" | DLQ replay job ([`05` §6](./05-api-and-event-contracts.md)) |
| "Rotate a secret" | Secrets Manager ([`06` §4](./06-security-architecture.md)) |

> The single most useful debugging superpower in this whole system: **one `traceId` reconstructs an
> entire request's journey** across every service, Kafka hop, and log line ([`09`](./09-observability.md)).
> When in doubt, find the `traceId` and follow it.
