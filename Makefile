# ══════════════════════════════════════════════════════════════════
# MicroRecouv — Makefile principal
# Usage : make <target> [ENV=dev|staging|prod]
# ══════════════════════════════════════════════════════════════════

ENV ?= dev
COMPOSE_FILE = docker-compose.$(ENV).yml
DOCKER_REGISTRY ?= ghcr.io/microrecouv
IMAGE_TAG ?= latest

.PHONY: help up down restart logs ps clean \
        up-pipeline up-app build-backend build-web \
        dbt-run migrate test-backend test-web lint

help:
	@echo ""
	@echo "  MicroRecouv — Commandes disponibles"
	@echo "  ──────────────────────────────────────"
	@echo "  make up            Démarrer tous les services (ENV=dev par défaut)"
	@echo "  make down          Arrêter tous les services"
	@echo "  make restart       Redémarrer"
	@echo "  make logs          Afficher les logs en temps réel"
	@echo "  make ps            Lister les conteneurs"
	@echo ""
	@echo "  make build-backend Construire l'image Docker backend"
	@echo "  make build-web     Construire l'image Docker web"
	@echo ""
	@echo "  make up-pipeline   Démarrer profil pipeline uniquement (prod)"
	@echo "  make up-app        Démarrer profil app uniquement (prod)"
	@echo ""
	@echo "  make dbt-run       Lancer les transformations dbt"
	@echo "  make migrate       Appliquer les migrations Flyway"
	@echo ""
	@echo "  make test-backend  Tests unitaires Spring Boot"
	@echo "  make test-web      Tests Angular"
	@echo "  make lint          Lint backend + web"
	@echo ""
	@echo "  Exemples :"
	@echo "    make up ENV=dev"
	@echo "    make up ENV=staging"
	@echo "    make up-pipeline ENV=prod"
	@echo ""

# ── Environnement ──────────────────────────────────────────────────────────
.env:
	@test -f .env || cp .env.$(ENV) .env

# ── Services ───────────────────────────────────────────────────────────────
up: .env
	docker compose -f $(COMPOSE_FILE) up -d

down:
	docker compose -f $(COMPOSE_FILE) down

restart: down up

logs:
	docker compose -f $(COMPOSE_FILE) logs -f

ps:
	docker compose -f $(COMPOSE_FILE) ps

clean:
	docker compose -f $(COMPOSE_FILE) down -v --remove-orphans
	docker system prune -f

# ── Profils prod ───────────────────────────────────────────────────────────
up-pipeline: .env
	docker compose -f docker-compose.prod.yml --profile pipeline up -d

up-app: .env
	docker compose -f docker-compose.prod.yml --profile app up -d

# ── Build images ───────────────────────────────────────────────────────────
build-backend:
	docker build -t $(DOCKER_REGISTRY)/backend:$(IMAGE_TAG) ./backend

build-web:
	docker build --no-cache-filter=builder --target production -t $(DOCKER_REGISTRY)/web:$(IMAGE_TAG) ./web

push-backend:
	docker push $(DOCKER_REGISTRY)/backend:$(IMAGE_TAG)

push-web:
	docker push $(DOCKER_REGISTRY)/web:$(IMAGE_TAG)

# ── dbt ───────────────────────────────────────────────────────────────────
dbt-run:
	docker compose -f $(COMPOSE_FILE) --profile tools run --rm dbt run

dbt-test:
	docker compose -f $(COMPOSE_FILE) --profile tools run --rm dbt test

dbt-docs:
	docker compose -f $(COMPOSE_FILE) --profile tools run --rm dbt docs generate

# ── Backend ───────────────────────────────────────────────────────────────
migrate:
	cd backend && mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/${POSTGRES_DB:-imf_db}

test-backend:
	cd backend && mvn test

build-backend-jar:
	cd backend && mvn clean package -DskipTests

# ── Web ───────────────────────────────────────────────────────────────────
install-web:
	cd web && npm ci

test-web:
	cd web && npm test

lint:
	cd backend && mvn checkstyle:check || true
	cd web && npm run lint || true

# ── Pipeline Python ───────────────────────────────────────────────────────
pipeline-install:
	cd pipeline/src && pip install -r requirements.txt

pipeline-test:
	cd pipeline/src && python -m pytest tests/ -v

pipeline-lint:
	cd pipeline/src && ruff check . && mypy . --ignore-missing-imports
