# ADR-0004 — Amazon Cognito for identity & auth

**Status:** Accepted · 2026-05-27

## Context

We need authentication (login, signup, social/OAuth logins), token issuance (JWTs the API Gateway can
validate), and user management — for a multi-tenant SaaS. Auth is **high-risk, undifferentiated, and
easy to get subtly wrong** (token handling, password storage, account recovery, MFA). The master plan
specifies **Amazon Cognito**.

## Decision

Use **Amazon Cognito** user pools for identity, JWT issuance, and OAuth social logins. The API Gateway
validates Cognito-issued JWTs against the pool's JWKS; services trust the validated claims.

## Consequences

**Positive**
- We do **not build auth** — no password hashing, no token rotation logic, no MFA implementation to
  get wrong. This is the single biggest risk-reduction in the security posture ([`06`](../06-security-architecture.md)).
- Native **JWT + JWKS** integration with Spring Cloud Gateway / Spring Security resource-server.
- Built-in social login (Google, etc.), hosted UI option, MFA, and user-pool management.
- Stays inside AWS IAM/KMS trust boundary; no third-party identity vendor.

**Negative (accepted)**
- **Cognito lock-in** — migrating identity providers later is real work (users must be exported/
  re-registered). Mitigated by keeping our app's authorization logic (workspace roles) in *our*
  Postgres, so only authentication is coupled to Cognito, not authorization.
- Cognito's developer experience and customization limits are well-known friction points.
- Local dev needs a Cognito substitute — handled via LocalStack / a mock issuer in the `dev` profile
  ([`12`](../12-cost-and-local-dev.md)).

## Alternatives considered

- **Auth0 / Okta.** Excellent DX, but another vendor + cost, and outside the AWS trust boundary.
  Rejected for v1 to keep the security model within IAM/KMS.
- **Keycloak (self-hosted).** Powerful and open-source, but now *we* operate an identity provider —
  exactly the undifferentiated, high-risk work we're avoiding.
- **Roll our own.** Rejected emphatically. Building auth is how small teams get breached.

> **Boundary we deliberately drew:** Cognito answers *"who are you?"* (authentication). It does **not**
> answer *"what may you do in this workspace?"* (authorization) — that lives in our domain (workspace
> membership + role in Postgres). Keeping authorization in our control means a future identity-provider
> swap touches one boundary, not the whole app.
