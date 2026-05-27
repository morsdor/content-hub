# ADR-0001 — Use Amazon EKS (Kubernetes), not ECS, for orchestration

**Status:** Accepted · 2026-05-27

## Context

ContentHub runs multiple long-lived services plus queue-driven workers that must autoscale
independently (API on request rate; transcription/render on Kafka lag; WebSocket hub on connection
count). We need container orchestration with health management, rolling deploys, autoscaling, and a
rich ecosystem for the operational add-ons we depend on (KEDA for queue-based scaling, the Datadog
agent, cert/secret operators). The master plan explicitly calls for **EKS replacing basic ECS**.

Two realistic options on AWS: **ECS (Fargate)** — AWS-native, simpler, less to learn — versus **EKS
(Kubernetes)** — the industry-standard, portable, vastly larger ecosystem.

## Decision

Use **Amazon EKS** with the Kubernetes API as our orchestration substrate.

## Consequences

**Positive**
- Access to the Kubernetes ecosystem we actually need: **KEDA** (scale workers on Kafka consumer lag —
  central to our cost & scaling story, see [`11`](../11-scalability-resilience.md)), HPA, Karpenter,
  the Datadog operator, External Secrets, cert-manager.
- **Portability / no lock-in** at the orchestration layer — the same manifests run on local Minikube
  ([`12`](../12-cost-and-local-dev.md)), giving true local/prod parity. ECS has no local equivalent.
- Skills and patterns transfer to any employer/cloud — explicitly valuable given the "learn to build
  production systems" goal.
- Declarative, GitOps-friendly (ArgoCD) — see [`08`](../08-deployment-cicd.md).

**Negative (accepted)**
- **Higher operational and cognitive complexity** than ECS. Kubernetes has a real learning curve.
- EKS has a **control-plane cost** (~$0.10/hr per cluster) that ECS lacks — mitigated by the ephemeral
  environment strategy (cluster only exists during cloud test windows) ([`12`](../12-cost-and-local-dev.md)).
- More moving parts to secure and patch.

## Alternatives considered

- **ECS on Fargate** — simpler and cheaper to idle, but no local-parity story, weaker ecosystem (no
  KEDA-equivalent for Kafka-lag scaling), and AWS-specific skills. Rejected: the queue-based
  autoscaling and local/prod parity are core requirements EKS serves and ECS does not.
- **Self-managed Kubernetes (kops/kubeadm)** — maximal control, maximal toil. Rejected: managing the
  control plane is undifferentiated work for a small team; EKS managed control plane is worth its cost.
- **Lambda for everything (serverless-first)** — great for the bursty workers, poor for long-lived
  WebSocket connections and the 15-minute render jobs (timeout limits). Rejected as the *primary*
  model, though Lambda remains an option for isolated glue tasks.

> **Honest note:** for a true MVP with one or two services, EKS is arguably over-kill, and
> [ADR-0009](./0009-modular-monolith-first.md) addresses that by letting us deploy a modular monolith
> onto the same EKS substrate first. EKS is the destination's orchestrator; we don't pay its full
> complexity cost until we actually run multiple independently-scaling services.
