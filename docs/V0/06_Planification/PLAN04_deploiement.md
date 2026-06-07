# PLAN04 — Stratégie de Déploiement Progressif
## DEV → STAGING → PRODUCTION

---

| Champ | Valeur |
|---|---|
| **Document** | Stratégie de Déploiement (PLAN04) |
| **Version** | 1.0 |
| **Date** | 2026-04-01 |
| **Infrastructure** | 2 serveurs Ubuntu 22.04 + 1 poste dev |

---

## TABLE DES MATIÈRES

1. [Vue d'ensemble des environnements](#1-vue-densemble-des-environnements)
2. [Environnement DEV — Développement local](#2-environnement-dev--développement-local)
3. [Environnement STAGING — Recette](#3-environnement-staging--recette)
4. [Environnement PRODUCTION — Live](#4-environnement-production--live)
5. [Infrastructure réseau](#5-infrastructure-réseau)
6. [Sauvegardes & Reprise](#6-sauvegardes--reprise)
7. [Monitoring & Alertes](#7-monitoring--alertes)
8. [Runbook de déploiement](#8-runbook-de-déploiement)

---

## 1. Vue d'ensemble des environnements

| Critère | DEV | STAGING | PRODUCTION |
|---|---|---|---|
| **Hôte** | Poste développeur (localhost) | VPS Ubuntu — `192.168.1.50` | 2 serveurs — `192.168.1.10` / `.11` |
| **CPU / RAM** | Variable (poste dev) | 4 cœurs / 8 Go | 8 cœurs / 16 Go (×2) |
| **Données** | Fixtures + CSV de test | Dump CBS anonymisé | Données réelles IMF |
| **TLS** | Non (HTTP local) | Let's Encrypt staging | Let's Encrypt (cert valide) |
| **Hot reload** | Oui (tous les composants) | Non | Non |
| **Déclenchement** | Manuel (`docker compose up`) | Automatique (merge develop) | Manuel (approbation) |
| **Accès** | Développeur uniquement | Équipe + maître de stage | DSI + utilisateurs IMF |
| **Durée de rétention des logs** | Non configurée | 7 jours | 30 jours |
| **Backup** | Non | Hebdomadaire | Quotidien (23h00) |

---

## 2. Environnement DEV — Développement local

### Objectif
Permettre un développement rapide avec hot-reload, sans dépendance à une infrastructure distante.

### Fichier `docker-compose.dev.yml`

```yaml
services:
  postgres-dev:
    image: postgres:15
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: imf_dev
      POSTGRES_USER: imf_user
      POSTGRES_PASSWORD: imf_dev_pass
    volumes:
      - pgdata-dev:/var/lib/postgresql/data
      - ./sql/init:/docker-entrypoint-initdb.d

  airflow-webserver:
    image: apache/airflow:2.8
    ports: ["8080:8080"]
    environment:
      AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: postgresql+psycopg2://imf_user:imf_dev_pass@postgres-dev/imf_dev
    volumes:
      - ./dags:/opt/airflow/dags
      - ./dbt_project:/opt/airflow/dbt_project
    depends_on: [postgres-dev]

  airflow-scheduler:
    image: apache/airflow:2.8
    command: scheduler
    volumes:
      - ./dags:/opt/airflow/dags
    depends_on: [airflow-webserver]

  superset:
    image: apache/superset:3.0
    ports: ["8088:8088"]
    depends_on: [postgres-dev]

  springboot-dev:
    build: ./backend
    ports: ["8081:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-dev:5432/imf_dev
      SPRING_PROFILES_ACTIVE: dev
    volumes:
      - ./backend/target:/app/target
    depends_on: [postgres-dev]

  angular-dev:
    build:
      context: ./frontend
      target: dev
    ports: ["4200:4200"]
    volumes:
      - ./frontend/src:/app/src
    command: ng serve --host 0.0.0.0 --poll 1000
```

### URLs locales

| Service | URL | Credentials |
|---|---|---|
| Angular Web | http://localhost:4200 | admin / admin_dev |
| Spring Boot API | http://localhost:8081/swagger-ui.html | — |
| Airflow UI | http://localhost:8080 | admin / admin |
| Superset | http://localhost:8088 | admin / admin |
| PostgreSQL | localhost:5432 / imf_dev | imf_user / imf_dev_pass |

### Données de test

Les fixtures de développement sont dans `sql/fixtures/` :
- `fixtures_clients.sql` — 20 clients fictifs
- `fixtures_prets.sql` — 50 prêts avec historiques variés
- `fixtures_collectes.sql` — 200 collectes sur 6 mois
- `csv_test/mtn_sample.csv` — relevé MTN de test (100 lignes)
- `csv_test/orange_sample.csv` — relevé Orange de test (80 lignes)

---

## 3. Environnement STAGING — Recette

### Objectif
Valider les fonctionnalités dans des conditions proches de la production, avec des données anonymisées.

### Caractéristiques

- **Identique à la production** : mêmes images Docker, mêmes variables de config (sauf URLs et mots de passe)
- **Données anonymisées** : noms et numéros de téléphone masqués via script `anonymize.py`
- **Accessible** : équipe de développement + maître de stage via VPN ou LAN

### Fichier `docker-compose.staging.yml`

```yaml
services:
  postgres-staging:
    image: postgres:15
    restart: unless-stopped
    environment:
      POSTGRES_DB: imf_staging
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata-staging:/var/lib/postgresql/data

  springboot-staging:
    image: ghcr.io/org/imf-api:latest
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-staging:5432/imf_staging
      SPRING_PROFILES_ACTIVE: staging
      JWT_SECRET: ${JWT_SECRET_STAGING}
    depends_on: [postgres-staging]

  nginx-staging:
    image: nginx:1.25
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/staging.conf:/etc/nginx/conf.d/default.conf
      - ./ssl/staging:/etc/letsencrypt
      - ./frontend/dist:/usr/share/nginx/html
    restart: unless-stopped
    depends_on: [springboot-staging]
```

### Procédure d'anonymisation des données CBS

```bash
# Générer un dump anonymisé depuis la production (à faire avant staging deploy)
python scripts/anonymize.py \
  --source postgresql://prod_user@192.168.1.11/imf_prod \
  --output sql/staging_dump_anonymized.sql

# Charger sur staging
psql -h 192.168.1.50 -U imf_user imf_staging < sql/staging_dump_anonymized.sql
```

### Checklist de mise en service Staging

- [ ] `docker compose -f docker-compose.staging.yml up -d` sans erreur
- [ ] HTTPS accessible via `https://staging.imf-app.local`
- [ ] `GET /api/health` retourne `{"status":"UP"}`
- [ ] Login avec compte de test fonctionne
- [ ] Données anonymisées visibles dans le dashboard
- [ ] DAG Airflow déclenché manuellement avec succès
- [ ] Notification FCM reçue sur device de test

---

## 4. Environnement PRODUCTION — Live

### Architecture 2 serveurs

#### Serveur Pipeline — `192.168.1.10`

| Composant | Image Docker | Port exposé | Rôle |
|---|---|---|---|
| `airflow-webserver` | `apache/airflow:2.8` | `:8080` (LAN) | Interface de pilotage des DAGs |
| `airflow-scheduler` | `apache/airflow:2.8` | Interne | Exécution planifiée des DAGs |
| `postgres-pipeline` | `postgres:15` | `:5432` (interne) | Base de données pipeline + DW |
| `superset` | `apache/superset:3.0` | `:8088` (LAN) | Dashboards analytiques DSI |

**Volumes persistants :**
```
/opt/imf/pipeline/
├── dags/              ← DAGs Python Airflow
├── dbt_project/       ← Modèles dbt
├── data/sources/      ← CSV MTN/Orange en attente d'ingestion
├── data/archives/     ← CSV traités (conservés 90 jours)
└── pgdata-pipeline/   ← Données PostgreSQL
```

#### Serveur Application — `192.168.1.11`

| Composant | Image Docker | Port exposé | Rôle |
|---|---|---|---|
| `nginx` | `nginx:1.25` | `:80`, `:443` | Reverse proxy + TLS + fichiers statiques Angular |
| `springboot-api` | `openjdk:17-jre` | `:8080` (interne) | API REST sécurisée JWT |
| `postgres-app` | `postgres:15` | `:5432` (interne) | Base applicative (utilisateurs, alertes) |
| `redis` | `redis:7` | `:6379` (interne) | Cache KPIs (TTL 1h) |

**Volumes persistants :**
```
/opt/imf/app/
├── angular-dist/      ← Build Angular production
├── pgdata-app/        ← Données PostgreSQL applicative
└── logs/              ← Logs applicatifs (rotation 30 jours)
```

### Fichier `docker-compose.app.yml` (production)

```yaml
services:
  postgres-app:
    image: postgres:15
    restart: unless-stopped
    environment:
      POSTGRES_DB: imf_app
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - /opt/imf/app/pgdata-app:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "${POSTGRES_USER}"]
      interval: 10s
      retries: 5

  redis:
    image: redis:7
    restart: unless-stopped
    command: redis-server --appendonly yes

  springboot-api:
    image: ghcr.io/org/imf-api:${APP_VERSION}
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-app:5432/imf_app
      SPRING_PROFILES_ACTIVE: prod
      JWT_SECRET: ${JWT_SECRET_PROD}
      FIREBASE_CREDENTIALS: /app/firebase-key.json
      REDIS_HOST: redis
    volumes:
      - ./firebase-key.json:/app/firebase-key.json:ro
    depends_on:
      postgres-app:
        condition: service_healthy

  nginx:
    image: nginx:1.25
    ports: ["80:80", "443:443"]
    restart: unless-stopped
    volumes:
      - /opt/imf/app/angular-dist:/usr/share/nginx/html:ro
      - ./nginx/prod.conf:/etc/nginx/conf.d/default.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /opt/imf/app/logs/nginx:/var/log/nginx
    depends_on: [springboot-api]
    logging:
      driver: "json-file"
      options:
        max-size: "50m"
        max-file: "5"
```

### Configuration Nginx production (`nginx/prod.conf`)

```nginx
server {
    listen 80;
    server_name imf-app.local;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name imf-app.local;

    ssl_certificate     /etc/letsencrypt/live/imf-app.local/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/imf-app.local/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header Strict-Transport-Security "max-age=31536000" always;

    # Angular (fichiers statiques)
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # Spring Boot API
    location /api/ {
        proxy_pass http://springboot-api:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 5. Infrastructure réseau

```
Internet / Réseau 4G
        │
        │  HTTPS :443
        ▼
Routeur / Firewall IMF
  (NAT + SSL Termination)
  ┌───────────────────────────────────────────────────────┐
  │                    LAN IMF 192.168.1.0/24             │
  │                                                        │
  │  192.168.1.10 — Serveur Pipeline                      │
  │  ├─ Airflow UI :8080  (LAN uniquement)                │
  │  └─ Superset :8088    (LAN uniquement)                │
  │                                                        │
  │  192.168.1.11 — Serveur Application                   │
  │  ├─ Nginx :443 (HTTPS — Internet + LAN)               │
  │  └─ Spring Boot :8080 (interne Docker uniquement)      │
  │                                                        │
  │  192.168.1.20–50 — Postes clients (Angular Web)        │
  │  192.168.1.50    — Serveur Staging                     │
  └───────────────────────────────────────────────────────┘
        │
        │  HTTPS :443 (4G/WiFi)
        ▼
Smartphones agents terrain (Flutter App)
```

### Règles Firewall UFW (serveurs production)

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp    # SSH (restreint aux IPs autorisées si possible)
ufw allow 80/tcp    # HTTP (redirect vers HTTPS)
ufw allow 443/tcp   # HTTPS
ufw enable
```

---

## 6. Sauvegardes & Reprise

### Stratégie de backup

| Quoi | Fréquence | Heure | Destination | Rétention |
|---|---|---|---|---|
| PostgreSQL pipeline (`pg_dump`) | Quotidienne | 23h00 | `/backups/pipeline/` | 30 jours |
| PostgreSQL app (`pg_dump`) | Quotidienne | 23h15 | `/backups/app/` | 30 jours |
| Volumes dags + dbt_project | Hebdomadaire | Dimanche 02h00 | `/backups/volumes/` | 4 semaines |
| Images Docker en production | À chaque release | — | ghcr.io | Permanente |

### Script de backup quotidien (`/opt/imf/scripts/backup.sh`)

```bash
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M)
BACKUP_DIR=/backups

# Pipeline DB
docker exec imf-postgres-pipeline pg_dump -U imf_user imf_pipeline \
  | gzip > $BACKUP_DIR/pipeline/pipeline_$DATE.sql.gz

# App DB
docker exec imf-postgres-app pg_dump -U imf_user imf_app \
  | gzip > $BACKUP_DIR/app/app_$DATE.sql.gz

# Nettoyage (> 30 jours)
find $BACKUP_DIR -name "*.sql.gz" -mtime +30 -delete

echo "Backup $DATE terminé" | mail -s "IMF Backup OK" dsi@imf.cm
```

**Cron :**
```
0 23 * * * /opt/imf/scripts/backup.sh >> /var/log/imf-backup.log 2>&1
```

### Procédure de restauration

```bash
# Restaurer la base pipeline depuis le dernier backup
gunzip -c /backups/pipeline/pipeline_YYYYMMDD_2300.sql.gz \
  | docker exec -i imf-postgres-pipeline psql -U imf_user imf_pipeline

# Restaurer la base app
gunzip -c /backups/app/app_YYYYMMDD_2315.sql.gz \
  | docker exec -i imf-postgres-app psql -U imf_user imf_app
```

---

## 7. Monitoring & Alertes

### Netdata (métriques système temps réel)

**Installation :**
```bash
curl https://my-netdata.io/kickstart.sh | bash
```

**Métriques surveillées :**
- CPU : alerte si > 90 % pendant 5 min
- RAM : alerte si > 85 %
- Disque : alerte si > 80 %
- Réseau : débit entrant/sortant

### Uptime Kuma (disponibilité des services)

**Checks configurés :**

| Service | URL / Check | Intervalle | Alerte si down |
|---|---|---|---|
| API Spring Boot | `GET /api/health` | 60 s | Email DSI + SMS |
| Frontend Angular | `GET /` (HTTP 200) | 60 s | Email DSI |
| Airflow Scheduler | `airflow jobs check` | 5 min | Email DSI |
| PostgreSQL Pipeline | TCP `:5432` | 60 s | Email DSI + SMS |
| PostgreSQL App | TCP `:5432` | 60 s | Email DSI + SMS |
| Backup quotidien | Fichier log backup | 24 h | Email DSI |

### Contacts d'escalade

| Niveau | Délai | Contact | Action |
|---|---|---|---|
| P1 — Service down | 0–15 min | DSI IMF (SMS) | Redémarrer le service |
| P2 — Performance dégradée | 15–60 min | DSI IMF (email) | Analyser logs, redémarrer si nécessaire |
| P3 — Anomalie non critique | 24h | DSI IMF (email quotidien) | Traiter lors de la prochaine maintenance |

---

## 8. Runbook de déploiement

### Démarrage initial (première mise en production)

```bash
# 1. Cloner le dépôt sur les serveurs
git clone https://github.com/org/imf-platform.git /opt/imf
cd /opt/imf

# 2. Configurer les variables d'environnement
cp .env.example .env.prod
nano .env.prod  # Renseigner tous les secrets

# 3. Obtenir le certificat TLS
certbot certonly --standalone -d imf-app.local

# 4. Démarrer le serveur pipeline (192.168.1.10)
docker compose -f docker-compose.pipeline.yml up -d

# 5. Démarrer le serveur application (192.168.1.11)
docker compose -f docker-compose.app.yml up -d

# 6. Vérifier les migrations Flyway
docker logs imf-springboot-prod | grep -i flyway

# 7. Vérifier la santé
curl -f https://imf-app.local/api/health
```

### Mise à jour (releases suivantes)

```bash
# Définir la version à déployer
export APP_VERSION=v1.1.0

# Serveur Application
ssh deploy@192.168.1.11
cd /opt/imf
docker compose -f docker-compose.app.yml pull
docker compose -f docker-compose.app.yml up -d

# Serveur Pipeline
ssh deploy@192.168.1.10
cd /opt/imf
docker compose -f docker-compose.pipeline.yml pull
docker compose -f docker-compose.pipeline.yml up -d --no-deps airflow-scheduler
```

### Arrêt propre

```bash
# Serveur Application
docker compose -f docker-compose.app.yml stop

# Serveur Pipeline (laisser finir les DAGs en cours)
docker compose -f docker-compose.pipeline.yml stop airflow-webserver
sleep 30  # Attendre fin des tâches actives
docker compose -f docker-compose.pipeline.yml stop
```

### Commandes de diagnostic

```bash
# Statut de tous les conteneurs
docker compose -f docker-compose.app.yml ps

# Logs Spring Boot (30 dernières lignes)
docker logs --tail 30 imf-springboot-prod

# Connexions actives PostgreSQL
docker exec imf-postgres-app psql -U imf_user -c \
  "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# Utilisation disque des volumes Docker
docker system df -v

# Jobs Airflow actifs
docker exec imf-airflow-scheduler airflow jobs check
```
