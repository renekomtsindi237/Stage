# ══════════════════════════════════════════════════════════════════
# MicroRecouv — Makefile principal
# Usage : make <target> [ENV=dev|staging|prod]
# ══════════════════════════════════════════════════════════════════

ENV ?= dev
COMPOSE_FILE = docker-compose.$(ENV).yml
DOCKER_REGISTRY ?= ghcr.io/microrecouv
IMAGE_TAG ?= latest

.PHONY: help up down restart logs ps clean pull \
        up-pipeline up-app \
        build build-backend build-frontend build-pipeline build-web \
        push-backend push-frontend push-web \
        dbt-run migrate test-backend test-web lint \
        infra backend-local desktop-dev desktop-build mobile-apk

help:
	@echo ""
	@echo "  MicroRecouv — Commandes disponibles"
	@echo "  ──────────────────────────────────────"
	@echo "  make pull          Télécharger les images publiques (postgres, redis, nginx...)"
	@echo "  make up            Démarrer tous les services sans construire ni puller"
	@echo "  make down          Arrêter tous les services"
	@echo "  make restart       Redémarrer"
	@echo "  make logs          Afficher les logs en temps réel"
	@echo "  make ps            Lister les conteneurs"
	@echo ""
	@echo "  make infra         Démarrer seulement postgres + redis (dev local)"
	@echo "  make backend-local Lancer le backend Spring Boot en local (port 8080)"
	@echo ""
	@echo "  make build         Construire les 3 images : backend + frontend + pipeline"
	@echo "  make build-backend  Construire uniquement l'image backend Spring Boot"
	@echo "  make build-frontend Construire uniquement l'image frontend Angular (nginx)"
	@echo "  make build-pipeline Construire les images pipeline Python (Airflow + ML API)"
	@echo ""
	@echo "  make up-pipeline   Démarrer profil pipeline uniquement (prod)"
	@echo "  make up-app        Démarrer profil app uniquement (prod)"
	@echo ""
	@echo "  make dbt-run       Lancer les transformations dbt"
	@echo "  make migrate       Appliquer les migrations Flyway"
	@echo ""
	@echo "  make desktop-dev   Lancer le client bureau Tauri (dev)"
	@echo "  make desktop-build Construire l'installeur Windows"
	@echo "  make mobile-apk    Construire l'APK Android (API prod)"
	@echo ""
	@echo "  make test-backend  Tests unitaires Spring Boot"
	@echo "  make test-web      Tests Angular"
	@echo "  make lint          Lint backend + web"
	@echo ""
	@echo "  Workflow dev local :"
	@echo "    1. make infra"
	@echo "    2. make backend-local   (dans un 2e terminal)"
	@echo "    3. cd frontend && ng serve  (dans un 3e terminal)"
	@echo ""

# ── Environnement ──────────────────────────────────────────────────────────
.env:
	@test -f .env || cp .env.$(ENV) .env

# ── Dev local (ng serve + backend Maven) ───────────────────────────────────
infra: .env
	docker compose -f docker-compose.dev.yml up -d postgres redis
	@echo "PostgreSQL et Redis démarrés. Lancez ensuite : make backend-local"

backend-local: .env
	@set -a && . ./.env && set +a && \
	cd backend && mvn spring-boot:run \
	  -Dspring-boot.run.jvmArguments="-DPOSTGRES_HOST=localhost -DREDIS_HOST=localhost -DFIREBASE_ENABLED=false"

# ── Services ───────────────────────────────────────────────────────────────
pull: .env
	docker compose -f $(COMPOSE_FILE) pull --ignore-buildable

up: .env
	docker compose -f $(COMPOSE_FILE) up -d --no-build --pull never

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
build: build-backend build-frontend build-pipeline
	@echo ""
	@echo "  Toutes les images construites avec succès :"
	@echo "    $(DOCKER_REGISTRY)/backend:$(IMAGE_TAG)"
	@echo "    $(DOCKER_REGISTRY)/frontend:$(IMAGE_TAG)"
	@echo "    $(DOCKER_REGISTRY)/airflow:$(IMAGE_TAG)"
	@echo "    $(DOCKER_REGISTRY)/ml-api:$(IMAGE_TAG)"

build-backend:
	docker build -t $(DOCKER_REGISTRY)/backend:$(IMAGE_TAG) -f backend/Dockerfile .

build-frontend:
	docker build -t $(DOCKER_REGISTRY)/frontend:$(IMAGE_TAG) ./frontend

build-pipeline:
	docker build -t $(DOCKER_REGISTRY)/airflow:$(IMAGE_TAG) -t imf-airflow-ml:2.8.4 -f pipeline/Dockerfile.airflow ./pipeline
	docker build -t $(DOCKER_REGISTRY)/ml-api:$(IMAGE_TAG) -f pipeline/Dockerfile.ml ./pipeline

# Alias pour compatibilité (ancien nom)
build-web: build-frontend

push-backend:
	docker push $(DOCKER_REGISTRY)/backend:$(IMAGE_TAG)

push-frontend:
	docker push $(DOCKER_REGISTRY)/frontend:$(IMAGE_TAG)
	docker push $(DOCKER_REGISTRY)/airflow:$(IMAGE_TAG)
	docker push $(DOCKER_REGISTRY)/ml-api:$(IMAGE_TAG)

# Alias pour compatibilité (ancien nom)
push-web: push-frontend

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

# ── Desktop (Tauri) ───────────────────────────────────────────────────────
desktop-dev:
	cd desktop && npm run dev

desktop-build:
	cd desktop && npm run build

mobile-apk:
	cd mobile && flutter pub get && flutter build apk --release \
	  --dart-define=API_BASE_URL=https://imf.rene.it.com
	mkdir -p mobile/dist
	cp mobile/build/app/outputs/flutter-apk/app-release.apk \
	  mobile/dist/MicroRecouv-1.0.4.apk

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
