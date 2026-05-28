# ContentHub — local development convenience targets
# See docs/12-cost-and-local-dev.md for the full dev workflow.
#
# Quick start:
#   make doctor    ← verify prerequisites
#   make dev-up    ← bring up the full local stack
#   make logs      ← tail all container output
#   make dev-down  ← stop (volumes preserved)

.PHONY: help doctor dev-up dev-down dev-restart logs status migrate seed clean clean-force

COMPOSE  := docker compose
ENV_FILE := .env.local

# Load .env.local so Make can reference variables in echo output.
# The dash suppresses "file not found" — if missing, run: cp .env.local.example .env.local
-include $(ENV_FILE)
export

# ── Help ─────────────────────────────────────────────────────────────────────

help:
	@echo ""
	@echo "ContentHub local dev  (docs/12-cost-and-local-dev.md)"
	@echo ""
	@echo "  make doctor         Check all prerequisites"
	@echo "  make dev-up         Start the full local stack (detached)"
	@echo "  make dev-down       Stop containers  (data preserved in volumes)"
	@echo "  make dev-restart    Restart all containers  (make dev-restart svc=kafka)"
	@echo "  make logs           Tail all logs           (make logs svc=postgres)"
	@echo "  make status         Show running containers"
	@echo "  make migrate        Run Flyway DB migrations against local Postgres"
	@echo "  make seed           Load demo fixtures (workspace, user, sample media)"
	@echo "  make clean          Stop containers, print data-wipe reminder"
	@echo "  make clean-force    Stop + DELETE all volumes  [destructive]"
	@echo ""

# ── Prerequisites ─────────────────────────────────────────────────────────────

doctor:
	@echo "Checking prerequisites..."
	@command -v docker >/dev/null 2>&1 \
		&& printf "  [ok] docker   %s\n" "$$(docker --version | cut -d' ' -f3 | tr -d ',')" \
		|| { echo "  [!!] docker not found — install Docker Desktop"; exit 1; }
	@$(COMPOSE) version >/dev/null 2>&1 \
		&& printf "  [ok] docker compose v2\n" \
		|| { echo "  [!!] docker compose v2 not found — update Docker Desktop"; exit 1; }
	@command -v java >/dev/null 2>&1 \
		&& printf "  [ok] java     %s\n" "$$(java -version 2>&1 | head -1 | cut -d'"' -f2)" \
		|| echo "  [--] java not found  (need JDK 21 — brew install temurin@21)"
	@command -v node >/dev/null 2>&1 \
		&& printf "  [ok] node     %s\n" "$$(node --version)" \
		|| echo "  [--] node not found  (need Node 20+ — brew install node@20)"
	@command -v mvn >/dev/null 2>&1 \
		&& printf "  [ok] mvn      %s\n" "$$(mvn --version 2>/dev/null | head -1 | cut -d' ' -f3)" \
		|| echo "  [--] mvn not found   (need Maven 3.9+ — brew install maven)"
	@echo ""
	@echo "Run 'make dev-up' to start the local stack."

# ── Stack lifecycle ───────────────────────────────────────────────────────────

dev-up:
	@test -f $(ENV_FILE) || { echo "$(ENV_FILE) missing — run: cp .env.local.example .env.local"; exit 1; }
	$(COMPOSE) --env-file $(ENV_FILE) up -d --remove-orphans
	@echo ""
	@echo "Local stack running. Endpoints:"
	@echo "  Postgres       localhost:${POSTGRES_PORT:-5432}"
	@echo "  MongoDB        localhost:${MONGO_PORT:-27017}"
	@echo "  Kafka          localhost:${KAFKA_HOST_PORT:-29092}  (29092 = host listener)"
	@echo "  Schema Reg.    http://localhost:${SCHEMA_REGISTRY_PORT:-8081}/subjects"
	@echo "  LocalStack     http://localhost:${LOCALSTACK_PORT:-4566}/_localstack/health"
	@echo "  OIDC (mock)    http://localhost:${MOCK_OAUTH2_PORT:-8090}/contenthub"
	@echo "  Jaeger UI      http://localhost:${JAEGER_UI_PORT:-16686}"
	@echo "  Grafana        http://localhost:${GRAFANA_PORT:-3001}  (admin / admin)"
	@echo "  Prometheus     http://localhost:${PROMETHEUS_PORT:-9090}"
	@echo ""
	@echo "Next steps: make migrate && make seed"

dev-down:
	$(COMPOSE) --env-file $(ENV_FILE) down

dev-restart:
	$(COMPOSE) --env-file $(ENV_FILE) restart $(svc)

logs:
	$(COMPOSE) --env-file $(ENV_FILE) logs -f $(svc)

status:
	$(COMPOSE) --env-file $(ENV_FILE) ps

# ── App lifecycle (placeholders — implemented with the app skeleton) ───────────

migrate:
	@echo "Flyway runs automatically at startup (spring.flyway.enabled=true in the dev profile)."
	@echo "Manual override: mvn -pl backend flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-contenthub}"

seed:
	@echo "Seed script not yet implemented — coming with the app skeleton."
	@echo "Target: a demo workspace, one user (dev@contenthub.local), and a sample media clip."

# ── Cleanup ───────────────────────────────────────────────────────────────────

clean:
	$(COMPOSE) --env-file $(ENV_FILE) down
	@echo ""
	@echo "Containers stopped. Volumes are preserved."
	@echo "To DELETE all local data run: make clean-force"

clean-force:
	$(COMPOSE) --env-file $(ENV_FILE) down -v --remove-orphans
	@echo "All volumes deleted. Run 'make dev-up' for a clean slate."
