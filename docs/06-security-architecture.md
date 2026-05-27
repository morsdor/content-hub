# 06 — Security Architecture

**Audience:** builders + anyone signing off on risk. Security here is **defense in depth**: no single
control is trusted alone. We layer network isolation, identity, least-privilege IAM, encryption, and
application controls so that any one failure is contained by the next layer.

> **The mental model:** assume every layer *will* eventually be partially breached, and design so that
> a breach at one layer reaches nothing valuable at the next. The famous question to keep asking is
> *"if an attacker had this, what could they reach?"* — the answer should always be "almost nothing."

---

## 1. The Security Group firewall chain (network layer)

This is the backbone control from the architecture SVG: a **strict least-privilege chain** where
**no backend resource touches the public internet**. Security Groups are *stateful* firewalls attached
to network interfaces; the chain is enforced by **SG-to-SG references**, not IP ranges.

```
 Internet (0.0.0.0/0)
      │  :80 / :443 ONLY
      ▼
┌──────────────────────────── ALB-SG ────────────────────────────┐
│  Inbound : 0.0.0.0/0  on 80, 443         (the ONLY public ingress)│
│  Outbound: → EKS-Node-SG  (nothing else)                          │
└───────────────────────────────┬─────────────────────────────────┘
                                 ▼
┌──────────────────────────── EKS-Node-SG ───────────────────────┐
│  Inbound : FROM ALB-SG only  (internet BLOCKED)                  │
│            + intra-SG on 8080 (pod-to-pod / service mesh)        │
│  Outbound: → DB-SG (5432, 27017) ; → MSK (9092/9094) ;           │
│            → S3/Cognito/KMS via VPC endpoints ; → NAT (egress)   │
└───────────────────────────────┬─────────────────────────────────┘
                                 ▼
┌──────────────────────────── DB-SG ─────────────────────────────┐
│  Inbound : FROM EKS-Node-SG only, on 5432 (PG) & 27017 (Mongo)  │
│            internet BLOCKED ; ALB BLOCKED                        │
│  Outbound: standard stateful responses                          │
└─────────────────────────────────────────────────────────────────┘
```

| Tier | Inbound | Outbound | Net effect |
| --- | --- | --- | --- |
| **ALB-SG** | `0.0.0.0/0` :80,:443 | only → EKS-Node-SG | the single front door |
| **EKS-Node-SG** | only from ALB-SG; intra-SG :8080 | → DB-SG, MSK, VPC endpoints, NAT | compute can't be reached from the internet |
| **DB-SG** | only from EKS-Node-SG :5432/:27017 | responses only | data is unreachable except by our pods |

**Subnet placement reinforces the SGs:** the ALB sits in **public** subnets; **everything else**
(EKS pods, RDS, DocumentDB, MSK) sits in **private** subnets with no route to an Internet Gateway.
Outbound internet (e.g., calling the ASR provider, YouTube API) goes through a **NAT Gateway**; inbound
internet simply has no path. SGs and subnets are two independent layers saying the same "no public
backend" — defense in depth.

> **Why SG references beat CIDR allow-lists (restated because it matters):** pod/node IPs churn
> constantly under autoscaling. `allow from EKS-Node-SG` stays correct forever; `allow from
> 10.0.3.0/24` rots and tempts you to widen it to `/16` "just to make it work" — which is how data
> tiers end up internet-reachable. SG references make the *secure* configuration the *stable* one.

---

## 2. Identity & authentication (Cognito + JWT)

Authentication is delegated to **Amazon Cognito** ([ADR-0004](./03-adr/0004-cognito-for-auth.md)); the
app enforces **authorization** itself.

```
Browser ──login──► Cognito ──issues──► JWT (id + access tokens, signed RS256)
   │
   └─ API call with  Authorization: Bearer <access JWT>
            │
            ▼
      Spring Cloud Gateway:
        1. fetch + cache Cognito JWKS (public keys)
        2. verify signature, issuer, audience, expiry
        3. extract claims (sub, email, groups)
        4. inject X-User-Sub / X-Trace-Id downstream; reject (401) if invalid
            │
            ▼
      Service: authorize the action
        - look up workspace_member(workspaceId, userId) → role
        - enforce role permits the operation (owner/editor/viewer)
```

- **AuthN vs AuthZ split (deliberate):** Cognito answers *"who are you?"*; **our** Postgres
  (`workspace_member.role`) answers *"what may you do here?"* ([`04` §2.1](./04-data-model.md)). This
  keeps a future identity-provider swap to one boundary and keeps authorization logic testable in our
  own code.
- **Token validation at the edge only:** services trust the Gateway-validated identity (the Gateway is
  the only thing that talks to Cognito JWKS), shrinking the surface where token crypto lives to one
  place ([ADR-0007](./03-adr/0007-spring-cloud-gateway.md)).
- **Token handling in the browser:** short-lived access tokens; refresh handled by the Cognito SDK.
  Tokens are kept in memory (not `localStorage`) where feasible to reduce XSS token theft; cookies, if
  used, are `HttpOnly`, `Secure`, `SameSite`.

---

## 3. AWS IAM & least privilege (IRSA)

Within the cluster, **each service gets its own IAM role** scoped to exactly what it needs — via
**IRSA (IAM Roles for Service Accounts)**, which maps a Kubernetes service account to an IAM role using
OIDC. **No node-wide credentials, no shared keys, no secrets baked into images.**

| Service | IAM permissions (illustrative, scoped to specific ARNs) |
| --- | --- |
| Media | `s3:PutObject/GetObject` on the media bucket prefix only; `kms:GenerateDataKey/Decrypt` on the media key |
| Transcription | `s3:GetObject` (read source) ; ASR provider creds via Secrets Manager |
| Analytics | Secrets Manager read for platform OAuth app creds; KMS decrypt for grant tokens |
| Gateway | none beyond Cognito JWKS (public) |

> **Why IRSA instead of an instance profile or a shared key in a config file:** an instance profile
> grants *every pod on the node* the same permissions — a compromised sidecar inherits the database
> keys. IRSA scopes credentials to the *pod's* service account, so the blast radius of a compromised
> pod is exactly that one service's narrow permissions. Combined with Fargate's per-pod micro-VM
> isolation ([ADR-0010](./03-adr/0010-fargate-compute.md)), one popped container reaches almost nothing.

**Principle:** every IAM policy names specific actions on specific resource ARNs. No `"Action": "*"`,
no `"Resource": "*"`. Wildcards in IAM are reviewed as defects.

---

## 4. Secrets management

| Secret | Where it lives | How it's accessed |
| --- | --- | --- |
| DB credentials | AWS Secrets Manager (rotated) | injected at runtime via External Secrets Operator → K8s secret → env; never in images or Git |
| ASR / platform OAuth app creds | Secrets Manager | IRSA-scoped read at startup |
| User platform OAuth tokens | Postgres, **envelope-encrypted** with a KMS data key | decrypted in-process only when needed ([`04` §2.4](./04-data-model.md)) |
| Cognito config | non-secret (pool id, JWKS url) | config / env |

**Rules:** secrets never enter source control (enforced by `gitleaks`/secret scanning in CI,
[`08`](./08-deployment-cicd.md)); never logged (log scrubbing, [`09`](./09-observability.md)); rotated
on a schedule; and **encryption keys are managed by KMS**, never hand-rolled.

---

## 5. Encryption everywhere

| State | Control |
| --- | --- |
| **In transit, edge** | TLS 1.2+ terminated at the ALB; HSTS; modern cipher suites only |
| **In transit, internal** | service-to-service over TLS (mTLS via mesh as a hardening step); MSK in-transit encryption; DB connections require TLS |
| **At rest** | RDS, DocumentDB, S3, MSK, EBS all encrypted with **KMS** customer-managed keys |
| **Application-level** | OAuth tokens envelope-encrypted *before* hitting the DB (defense beyond at-rest disk encryption) |

> **Why application-level encryption *on top of* at-rest disk encryption for OAuth tokens:** disk
> encryption protects against someone stealing a physical disk or snapshot. It does **not** protect
> against an attacker who can already query the database (e.g., via SQL injection or a leaked
> read-replica). Envelope-encrypting the tokens means a database dump yields ciphertext, not usable
> YouTube/X credentials. The most sensitive data gets the extra layer.

---

## 6. Kubernetes-layer controls

- **Network Policies** — even inside EKS-Node-SG, default-deny pod-to-pod traffic; explicitly allow
  only the flows that exist (Gateway→services, services→Kafka). The SG chain protects the cluster
  perimeter; network policies micro-segment *within* it.
- **Pod security** — non-root containers, read-only root filesystem, dropped Linux capabilities, no
  privileged pods. Enforced by admission policy (e.g., Kyverno/PSA).
- **Image provenance** — images scanned (Trivy) in CI and signed; only images from our registry run;
  base images pinned by digest. ([`08`](./08-deployment-cicd.md))
- **Secrets** — K8s secrets sourced from Secrets Manager via External Secrets; not stored in Git, not
  in plain ConfigMaps.

---

## 7. Application-layer security (OWASP Top 10 posture)

| Risk | Control |
| --- | --- |
| **Broken access control** | Every request authorized against `workspace_member` role; deny by default; tenant scoping (`workspaceId`) on every query ([`04`](./04-data-model.md)); IDOR-tested in CI. |
| **Injection (SQLi/NoSQLi)** | Parameterized queries / JPA + bound Mongo queries only; no string-built queries; input validation at the Gateway and service boundary. |
| **Cryptographic failures** | KMS-managed keys; TLS everywhere; no custom crypto; sensitive fields app-encrypted (§5). |
| **SSRF** | The presigned-upload model means we don't fetch user-supplied URLs server-side; outbound calls go only to allow-listed providers via NAT. |
| **Security misconfiguration** | 100% IaC ([`07`](./07-infrastructure-terraform.md)) + `tfsec`/`checkov` scanning; no click-ops; the SG chain is code-reviewed. |
| **Vulnerable dependencies** | Dependabot/Renovate + SCA in CI; images scanned (Trivy). |
| **Auth failures** | Delegated to Cognito (MFA available); short-lived tokens; lockout/anomaly handled by Cognito. |
| **SSRF/XXE/deserialization** | No untrusted deserialization; JSON only; XML disabled. |
| **Logging/monitoring failures** | Centralized logs (Splunk), audit trail, alerting ([`09`](./09-observability.md)). |
| **XSS (client)** | React's default escaping; CSP header; the rich-text/transcript editor sanitizes HTML; no `dangerouslySetInnerHTML` on user content. |

---

## 8. Securing user media (the presigned-URL model)

User video is private and potentially sensitive. Controls:

1. **Private bucket** — no public access; Block Public Access on; bucket policy denies non-TLS.
2. **Access only via short-lived presigned URLs** (NFR-SEC-04) — minted by the Media Service after
   authorizing the caller's workspace membership; TTL measured in minutes; scoped to one object + verb.
3. **Per-workspace key prefixing** (`ws/{workspaceId}/…`) + IAM/condition checks so one workspace can
   never address another's objects.
4. **Encryption at rest (KMS)** + **versioning** (accidental/malicious overwrite recovery,
   [`13`](./13-runbooks-and-dr.md)).
5. **Upload validation** — content-type + size constraints on the presign; post-upload probe; we do not
   execute or trust uploaded content beyond media processing.

> **Why presigned URLs instead of proxying downloads through the service:** it keeps large media off
> our compute (performance + cost, [`02` §6](./02-system-architecture.md)) *and* it means access is
> time-boxed and per-object — a leaked URL expires in minutes and unlocks exactly one file, versus a
> service endpoint that, if buggy, could leak the whole bucket.

---

## 9. Threat model (STRIDE, abbreviated)

| Threat | Example | Primary mitigation |
| --- | --- | --- |
| **S**poofing | Forged JWT | RS256 signature verified against Cognito JWKS at Gateway |
| **T**ampering | Modify another workspace's card | Tenant scoping + role check on every write; FKs |
| **R**epudiation | "I didn't publish that" | Audit logging with user + traceId on state changes |
| **I**nformation disclosure | Read another tenant's media/metrics | Per-workspace prefixing, presigned URLs, deny-by-default authz, app-encryption of tokens |
| **D**enial of service | Flood the API / WS | Rate limits at Gateway; per-plan quotas; ALB + autoscaling; WAF |
| **E**levation of privilege | Viewer performs owner action | Role enforced server-side per operation; never trust client role claims |

**Trust boundaries** (where data crosses from less- to more-trusted and must be validated): browser →
Gateway, Gateway → services, services → external APIs (ASR/YouTube/X), and the upload path browser →
S3. Each boundary validates inputs and authenticates the caller.

---

## 10. Compliance & privacy posture (forward-looking)

- **Data residency / DPA** — user audio is sent to a third-party ASR ([ADR-0005](./03-adr/0005-third-party-asr.md));
  this must be disclosed and covered by a DPA; prefer providers offering no-retention/zero-data modes.
- **Right to deletion** — workspace + media deletion cascades across Postgres, Mongo, and S3 (with
  versioned-object purge); covered operationally in [`13`](./13-runbooks-and-dr.md).
- **PII minimization** — we store email + display name; payment data is delegated to the billing
  provider (we keep only a customer reference, not card data).
- **Audit** — security-relevant events (login anomalies via Cognito, role changes, grants
  connect/revoke) are logged and retained.

---

## 11. Security in the SDLC (shift left)

Security is verified continuously, not at the end:

```
commit → secret scan (gitleaks) → SCA (deps) → SAST → build →
  image scan (Trivy) → IaC scan (tfsec/checkov) → deploy to ephemeral →
  DAST / auth & IDOR tests (Cucumber, 10) → promote
```

See [`08-deployment-cicd.md`](./08-deployment-cicd.md) for where each gate runs and
[`10-testing-qa.md`](./10-testing-qa.md) for the security test scenarios. The point: a misconfigured SG,
a leaked secret, or a vulnerable dependency **fails the pipeline**, long before it reaches users.
