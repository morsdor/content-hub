# 07 — Infrastructure as Code (Terraform)

**Audience:** builders + DevOps. Everything cloud is **Terraform** — no click-ops (NFR-O-01). This
chapter is the layout, the state strategy, how environments compose, and the **ephemeral apply/destroy
workflow** that is central to the cost story ([`12`](./12-cost-and-local-dev.md)).

> **Why 100% IaC is non-negotiable, not a nicety:** a hand-clicked resource is invisible, unreviewable,
> un-reproducible, and the first thing to drift. With everything in Terraform, the entire production
> environment is a `terraform apply` away from scratch — which is exactly what makes ephemeral
> environments (and disaster recovery, [`13`](./13-runbooks-and-dr.md)) possible. The infra *is* the
> code; the running cloud is just its current execution.

---

## 1. Repository & module layout

We use **composable modules** (reusable building blocks) consumed by thin **environment compositions**
(`dev` / `qa` / `prod`). Modules know *how* to build a thing; environments decide *how big* and *how
many*.

```
infra/
├── modules/                         # reusable, environment-agnostic building blocks
│   ├── network/                     # VPC, public/private subnets, NAT, IGW, route tables, VPC endpoints
│   ├── security-groups/             # the ALB-SG → EKS-Node-SG → DB-SG chain (06)
│   ├── eks/                         # EKS cluster, Fargate profiles, OIDC provider, IRSA roles
│   ├── data-postgres/               # RDS PostgreSQL (Multi-AZ, backups, KMS)
│   ├── data-mongo/                  # DocumentDB cluster
│   ├── kafka/                       # Amazon MSK
│   ├── storage/                     # S3 buckets (media, rendered) + lifecycle + policies
│   ├── cognito/                     # user pool, app clients, identity providers
│   ├── alb/                         # Application Load Balancer + listeners + WAF assoc
│   └── observability/               # log destinations, Datadog integration role, alarms
│
├── envs/
│   ├── dev/                         # (mostly local; thin cloud footprint if any)
│   ├── qa/                          # EPHEMERAL — apply, test, destroy
│   │   ├── main.tf                  # composes modules at qa scale
│   │   ├── backend.tf               # remote state (qa key)
│   │   ├── variables.tf
│   │   └── terraform.tfvars         # qa sizing (small instances, single-AZ ok)
│   └── prod/
│       ├── main.tf                  # composes modules at prod scale (Multi-AZ, larger)
│       ├── backend.tf
│       └── terraform.tfvars
│
└── global/
    └── state-bootstrap/             # the S3 bucket + DynamoDB lock table for remote state (run once)
```

> **Why modules + thin envs instead of copy-pasting per environment:** the SG chain, the VPC topology,
> the IRSA wiring — these must be **identical** across qa and prod or "tested in qa" means nothing.
> Putting them in modules guarantees qa and prod differ only in *sizing variables*, not *structure*.
> Copy-paste guarantees drift; modules guarantee parity.

---

## 2. Remote state & locking

State is the single most dangerous thing in Terraform — corrupt or concurrent-write it and you can
orphan or destroy real resources. So:

```hcl
# envs/qa/backend.tf
terraform {
  backend "s3" {
    bucket         = "contenthub-tfstate"          # created once by global/state-bootstrap
    key            = "qa/terraform.tfstate"        # per-environment key → isolated state
    region         = "us-east-1"
    dynamodb_table = "contenthub-tflock"           # state locking → no concurrent applies
    encrypt        = true                          # state contains secrets → KMS-encrypted at rest
  }
}
```

- **Remote state in S3** — shared, durable, versioned (recover a clobbered state).
- **DynamoDB lock table** — prevents two `apply`s racing (e.g., CI and a human at once).
- **Per-environment state keys** — qa and prod can never touch each other's resources.
- **State is sensitive** — it can contain secrets; the bucket is private, encrypted, access-logged.

---

## 3. Example module — the Security Group chain (the heart of [`06`](./06-security-architecture.md))

Modules turn the architecture's prose into enforced reality. The SG chain, in code:

```hcl
# modules/security-groups/main.tf  — the least-privilege chain, by SG reference (not CIDR)

resource "aws_security_group" "alb" {
  name_prefix = "${var.env}-alb-"
  vpc_id      = var.vpc_id
  description = "Public edge: internet → ALB on 80/443 only"
}

resource "aws_security_group" "eks_node" {
  name_prefix = "${var.env}-eks-node-"
  vpc_id      = var.vpc_id
  description = "Private compute: accepts only ALB-SG"
}

resource "aws_security_group" "db" {
  name_prefix = "${var.env}-db-"
  vpc_id      = var.vpc_id
  description = "Data tier: accepts only EKS-Node-SG"
}

# --- ALB-SG: the ONLY public ingress -------------------------------------------------
resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
# (port 80 rule omitted for brevity — redirects to 443)

# --- EKS-Node-SG: accepts ONLY from ALB-SG (by reference, not IP) --------------------
resource "aws_vpc_security_group_ingress_rule" "node_from_alb" {
  security_group_id            = aws_security_group.eks_node.id
  referenced_security_group_id = aws_security_group.alb.id          # ← the chain link
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# --- DB-SG: accepts ONLY from EKS-Node-SG, on the two DB ports -----------------------
resource "aws_vpc_security_group_ingress_rule" "pg_from_nodes" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.eks_node.id     # ← the chain link
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_vpc_security_group_ingress_rule" "mongo_from_nodes" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.eks_node.id
  ip_protocol                  = "tcp"
  from_port                    = 27017
  to_port                      = 27017
}
```

> **Read the `referenced_security_group_id` lines as the architecture diagram, executed.** The DB tier
> literally cannot be addressed by anything that isn't in `eks_node`'s SG — there is no IP to widen, no
> CIDR to fat-finger. The diagram in [`06` §1](./06-security-architecture.md) and this code are the
> same statement in two languages.

---

## 4. Example module — IRSA (least-privilege per service, [`06` §3](./06-security-architecture.md))

```hcl
# modules/eks/irsa.tf  — map a K8s service account to a tightly-scoped IAM role

data "aws_iam_policy_document" "media_service" {
  statement {                                   # ONLY the media bucket prefix, ONLY these verbs
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${var.media_bucket_arn}/*"]
  }
  statement {
    actions   = ["kms:GenerateDataKey", "kms:Decrypt"]
    resources = [var.media_kms_key_arn]
  }
}

module "media_irsa" {
  source                = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  role_name             = "${var.env}-media-svc"
  oidc_providers = {
    main = {
      provider_arn               = var.eks_oidc_provider_arn
      namespace_service_accounts = ["contenthub:media-service"]   # binds to THIS sa only
    }
  }
  role_policy_arns = { policy = aws_iam_policy.media_service.arn }
}
```

The Kubernetes Deployment then references the service account; the pod assumes *only* this role. No
wildcards, no shared node credentials — the principle from [`06` §3](./06-security-architecture.md) made
concrete.

---

## 5. Environment composition (sizing is the only difference)

```hcl
# envs/prod/main.tf  (excerpt) — same modules, prod-scale variables
module "network"          { source = "../../modules/network"          azs = ["us-east-1a","us-east-1b","us-east-1c"] }
module "security_groups"  { source = "../../modules/security-groups"  vpc_id = module.network.vpc_id  env = "prod" }
module "eks"              { source = "../../modules/eks"               env = "prod"  fargate = true }
module "postgres"         { source = "../../modules/data-postgres"     multi_az = true   instance_class = "db.r6g.large" }
module "mongo"            { source = "../../modules/data-mongo"        instances = 3 }
module "kafka"            { source = "../../modules/kafka"             broker_count = 3 }
```

```hcl
# envs/qa/terraform.tfvars — same structure, minimal cost
multi_az       = false              # single-AZ is fine for ephemeral test runs
instance_class = "db.t4g.medium"    # smallest viable
broker_count   = 1                  # one MSK broker for a short-lived test
```

| Knob | dev (local) | qa (ephemeral) | prod |
| --- | --- | --- | --- |
| Where | laptop | AWS, short-lived | AWS, always-on |
| Multi-AZ | n/a | no | **yes** |
| DB size | container | smallest | right-sized + read replica |
| MSK brokers | local image | 1 | 3 (multi-AZ) |
| Lifespan | always | minutes–hours | continuous |

---

## 6. The ephemeral apply/destroy workflow (the cost lever)

This is the operational heartbeat of the whole cost strategy ([`00` §8](./00-overview.md),
[`12`](./12-cost-and-local-dev.md)):

```bash
# Stand up a full production-fidelity environment on demand
cd infra/envs/qa
terraform init
terraform plan  -out=tf.plan          # review what will be created
terraform apply tf.plan               # ~entire VPC/EKS/SG/data/MSK stack comes up

# ... run BlazeMeter load tests + Cucumber BDD against the live cloud (10) ...

terraform destroy -auto-approve       # every billable asset is torn down → cost stops
```

- **`plan` before `apply`, always** — Terraform shows the exact diff; in CI this is posted to the PR
  for review. You never apply blind.
- **`destroy` is the point** — qa exists only for the test window. Leaving an idle EKS+MSK cluster
  running is the expensive mistake this workflow exists to prevent.
- **Guardrails so `destroy` can't hit prod:** prod state is a separate key (§2), prod has
  `prevent_destroy` lifecycle blocks on the data stores, and destructive applies to prod require manual
  approval in the pipeline ([`08`](./08-deployment-cicd.md)).

> **The thing to internalize:** `terraform apply` / `terraform destroy` turns a production-grade cloud
> environment into something you rent **by the hour, on demand**. That is what lets a solo builder
> integration-test a real EKS+Kafka+RDS system for the price of a coffee, and it only works because
> 100% of it is code with no click-ops drift.

---

## 7. Tagging, drift, and governance

- **Mandatory tags** on every resource (via provider `default_tags`): `Environment`, `Project=contenthub`,
  `Owner`, `CostCenter`, `ManagedBy=terraform`. These power cost allocation and the FinOps reports in
  [`12`](./12-cost-and-local-dev.md), and make orphaned resources obvious.
- **Drift detection** — a scheduled `terraform plan` in CI flags any out-of-band change (someone
  clicked something). Drift is treated as an incident: reconcile via code, never by clicking.
- **Policy as code** — `tfsec` / `checkov` run on every plan ([`06` §11](./06-security-architecture.md));
  a public S3 bucket or an open SG fails the pipeline before apply.
- **Module versioning** — modules are pinned by version/ref so an env upgrade is deliberate, never
  accidental.

---

## 8. What Terraform owns vs. what it doesn't

| Terraform owns | Owned elsewhere |
| --- | --- |
| VPC, subnets, NAT, endpoints | App config (Spring profiles → ConfigMaps) |
| SGs, IAM/IRSA, KMS keys | Application deploys (Helm/ArgoCD — [`08`](./08-deployment-cicd.md)) |
| EKS cluster + Fargate profiles | DB *schema* (Flyway migrations — [`08`](./08-deployment-cicd.md)) |
| RDS, DocumentDB, MSK, S3, Cognito, ALB | Kafka *topics* (created by app/CI, versioned with schemas — [`05`](./05-api-and-event-contracts.md)) |

> **The boundary line:** Terraform provisions **infrastructure** (things with a cloud API and a
> lifecycle measured in days). It does **not** deploy application code or run DB migrations — those
> change far more often and belong to the CD pipeline. Mixing them couples a code deploy to an infra
> apply and is a common, painful anti-pattern. Keep the slow-changing substrate (Terraform) separate
> from the fast-changing app (CD).
