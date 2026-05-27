# ADR-0007 — Spring Cloud Gateway as the API edge

**Status:** Accepted · 2026-05-27

## Context

We need a single entry point behind the ALB that does cross-cutting edge concerns: **JWT validation**
(against Cognito JWKS), **routing** to the right service, **rate limiting**, **CORS**, and
**trace-ID injection**. Doing these in every service duplicates security-critical code; doing them
once at the edge is the standard. The master plan specifies a **Spring Cloud API Gateway**, and our
services are Java/Spring Boot.

## Decision

Use **Spring Cloud Gateway** as the application-level API gateway, deployed as a service in the
EKS-Node-SG tier behind the ALB. It validates Cognito JWTs, enforces coarse rate limits, injects/
propagates the `traceId`, and routes by path to the Workspace, Media, Analytics, and Transcription
services. WebSocket upgrade requests are routed through to the Workspace Service.

## Consequences

**Positive**
- **Auth in one place** — services trust the gateway's validated claims and need only fine-grained
  authorization (workspace role), not token cryptography. Smaller attack surface.
- **Same language/stack** as the services (Spring) — one ecosystem, shared filters/libraries, easy for
  a Java team. Reactive (Netty) core handles high concurrency and WS well.
- Natural home for rate limiting, request logging, and trace propagation (NFR-O-03).

**Negative (accepted)**
- The gateway is a **critical path / potential SPOF** — must be run with ≥2 replicas and its own HPA;
  if it's down, everything is down. Treated as a tier-0 service in observability ([`09`](../09-observability.md)).
- It is **not** the AWS "API Gateway" managed service — naming overlap can confuse; we mean the Spring
  component running in our cluster.
- Adds one network hop. Acceptable for the cross-cutting value it provides.

## Alternatives considered

- **AWS API Gateway (managed).** Great for REST + Cognito authorizers and scales itself, but awkward
  for our long-lived WebSocket collaboration model and per-request cost at scale; also pulls edge logic
  out of our Spring ecosystem. Rejected as primary; could front specific public REST endpoints later.
- **Kubernetes Ingress / NGINX / Envoy / API gateway (Kong).** Capable, but introduces a non-Spring
  component for the team to learn for the JWT/routing logic we can express natively in Spring Cloud
  Gateway. Rejected for v1 to minimize stacks.
- **No gateway; ALB → services directly, auth in each service.** Rejected — duplicates security code
  across services, the highest-risk place to duplicate anything.
