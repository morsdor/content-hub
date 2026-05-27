# ADR-0010 — AWS Fargate for EKS compute (no managed node groups)

**Status:** Accepted · 2026-05-27

## Context

EKS ([ADR-0001](./0001-eks-over-ecs.md)) needs compute to run pods. Two models: **managed node groups**
(we run EC2 instances, the cluster schedules pods onto them — we patch/scale/secure the nodes) or
**Fargate** (serverless — AWS runs each pod in its own micro-VM; no nodes to manage). The master plan
specifies **Fargate (serverless nodes running the Kubernetes pods)**.

## Decision

Run workloads on **AWS Fargate** profiles by default — no EC2 node groups to operate. Each pod gets
right-sized vCPU/memory; we pay per pod-second.

## Consequences

**Positive**
- **No node ops** — no patching, no node scaling, no SSH, no node-level CVE treadmill. For a small
  team this removes a large operational burden and shrinks the security surface ([`06`](../06-security-architecture.md)).
- **Scales toward zero cleanly** — idle workers cost ~nothing because there are no always-on nodes
  waiting for pods. This is core to the cost story ([`12`](../12-cost-and-local-dev.md)) and pairs
  perfectly with KEDA scaling transcription/render on Kafka lag ([`11`](../11-scalability-resilience.md)).
- **Pod-level isolation** (each pod in its own micro-VM) is a security plus.

**Negative (accepted)**
- **Per-unit compute is pricier** than well-utilized EC2/Spot, and Fargate has no GPU support and
  per-pod resource ceilings. If we later self-host GPU ASR/render ([ADR-0005](./0005-third-party-asr.md)),
  those specific workloads need a **GPU managed node group or Karpenter-provisioned EC2** — Fargate and
  node groups can coexist in one cluster, so this is additive, not a reversal.
- Slightly slower pod cold-starts than warm EC2 nodes; mitigated with minimum replicas on latency-
  sensitive services (Gateway, Workspace).
- Some DaemonSet-based tooling doesn't run on Fargate (no nodes); the Datadog integration uses the
  Fargate-compatible model ([`09`](../09-observability.md)).

## Alternatives considered

- **Managed node groups (+ Cluster Autoscaler / Karpenter).** Cheaper at steady high utilization and
  required for GPU/large pods; more to operate and doesn't idle to zero as cleanly. **Likely added
  later** specifically for GPU render/ASR or cost-optimized steady workloads via **Karpenter + Spot** —
  at which point this ADR is amended, not replaced.
- **Pure EC2, self-managed.** Maximal control, maximal toil. Rejected for the same reason as
  self-managed Kubernetes in ADR-0001.

> **The throughline across ADR-0001/0010/0009:** default to *managed + scales-to-zero + minimal ops*
> while small, and introduce cheaper-but-heavier options (node groups, Spot, GPU, service extraction)
> precisely when a workload's economics or scale demands it — never preemptively.
