# Guide Technique de Déploiement — MicroRecouv V0

**Auteur :** KOMTSINDI Réné Alban  
**Version :** V0 — Avril 2026  
**Structure :** Openxtech

---

## 1. Prérequis

| Outil | Version minimale | Rôle |
|---|---|---|
| Docker | 24.x | Conteneurisation |
| Docker Compose | 2.20.x | Orchestration multi-services |
| Java JDK | 21 | Build backend (local uniquement) |
| Maven | 3.9.x | Build backend (local uniquement) |
| Python | 3.11 | Pipeline ETL |
| Node.js | 20 LTS | Build Angular |
| Flutter SDK | 3.19.x | Build mobile |
| Make | GNU Make | Automatisation |

---

## 2. Environnements

Le projet dispose de trois environnements distincts gérés par des fichiers `.env` et des `docker-compose` dédiés.

| Environnement | Base de données | Fichier Compose | Fichier ENV |
|---|---|---|---|
| `dev` | PostgreSQL 15 Docker local | `docker-compose.dev.yml` | `.env.dev` |
| `staging` | Supabase PostgreSQL (cloud) | `docker-compose.staging.yml` | `.env.staging` |
| `prod` | Supabase PostgreSQL (cloud) | `docker-compose.prod.yml` | `.env.prod` |

---

## 3. Déploiement en développement

### 3.1 Cloner et configurer

```bash
git clone <repo-url> Stage
cd Stage
cp .env.dev .env
```

### 3.2 Démarrer tous les services

```bash
make up ENV=dev
```

Cette commande démarre :
- **PostgreSQL 15** (port 5432) — base locale
- **Adminer** (port 8888) — interface web base de données
- **Spring Boot** (port 8080) — API REST avec migrations Flyway automatiques
- **Airflow Webserver** (port 8090) — interface DAGs ETL
- **Airflow Scheduler** — exécuteur de tâches planifiées
- **Airflow Worker** — exécution des jobs Python

### 3.3 Vérifier le démarrage

```bash
make ps       # État des conteneurs
make logs     # Logs en temps réel (Ctrl+C pour quitter)
```

Attendre environ 60 secondes que Spring Boot termine les migrations Flyway avant de tester l'API.

### 3.4 Accès aux services

| Service | URL | Identifiants par défaut |
|---|---|---|
| API REST (Swagger) | http://localhost:8080/swagger-ui.html | `admin` / `Admin2026!` |
| Airflow UI | http://localhost:8090 | `airflow` / `airflow` |
| Adminer | http://localhost:8888 | `imf_user` / `imf_dev_pass_2024` |
| Application Web | http://localhost:4200 | (lancée séparément) |

> **Important :** Ces identifiants sont réservés au développement local. Ne jamais les utiliser en production.

---

## 4. Déploiement en staging / production

### 4.1 Configurer Supabase

1. Créer un projet sur [supabase.com](https://supabase.com)
2. Récupérer : Project URL, anon key, service_role key, DB password
3. Renseigner dans `.env.staging` ou `.env.prod` :

```env
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhb...
SUPABASE_SERVICE_ROLE_KEY=eyJhb...
POSTGRES_HOST=db.xxxxx.supabase.co
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<votre-mdp-db>
POSTGRES_PORT=5432
POSTGRES_SSL_MODE=require
```

4. Générer des secrets forts pour staging/prod :

```bash
# Générer JWT_SECRET (min 32 chars)
openssl rand -hex 32

# Générer SPRING_API_KEY
openssl rand -hex 24
```

### 4.2 Démarrer en staging

```bash
cp .env.staging .env
make up ENV=staging
```

En staging, **aucun conteneur PostgreSQL local n'est démarré** — la base est entièrement gérée par Supabase.

Flyway applique automatiquement les migrations V1 à V4 lors du premier démarrage du backend :
- `V1__init_schema.sql` — Schémas et tables principales
- `V2__seed_admin_user.sql` — Compte administrateur initial
- `V3__sync_logs.sql` — Table de logs de synchronisation
- `V4__journal_audit_echeances.sql` — Journal d'audit des échéances

### 4.3 Construire les images Docker

```bash
make build-backend    # Image Spring Boot
make build-web        # Image Angular (Nginx)
```

---

## 5. Déploiement de l'application Web Angular

### 5.1 Développement local

```bash
cd web
npm install
ng serve
# Accessible sur http://localhost:4200
```

### 5.2 Build de production

```bash
make build-web
# Ou manuellement :
cd web && ng build --configuration=production
```

L'application est servie par **Nginx** (configuré dans `docker/nginx.conf`) en production. Le `Dockerfile` de l'image web réalise un build multi-stage : compilation Angular puis copie des assets statiques vers Nginx.

---

## 6. Déploiement de l'application Mobile Flutter

### 6.1 Développement

```bash
cd mobile
flutter pub get
flutter run
```

L'URL de l'API est configurée dans `lib/core/services/api_service.dart` via la constante `baseUrl` (pointe vers `http://10.0.2.2:8080` pour l'émulateur Android, ou `http://localhost:8080` pour iOS Simulator).

### 6.2 Build APK (Android)

```bash
flutter build apk --release
# Sortie : build/app/outputs/flutter-apk/app-release.apk
```

### 6.3 Build iOS

```bash
flutter build ios --release
# Nécessite Xcode et un compte développeur Apple
```

---

## 7. Pipeline ETL (Airflow)

### 7.1 DAGs disponibles

| DAG | Planification | Description |
|---|---|---|
| `dag_collectes` | Quotidien 06h00 | Extraction collectes MTN/Orange/Espèces |
| `dag_prets` | Quotidien 06h30 | Calcul PAR et mise à jour risque |
| `dag_alertes` | Quotidien 07h00 | Génération automatique des alertes |
| `dag_reporting` | Hebdomadaire | Agrégation KPI pour le tableau de bord |
| `dag_sync` | Quotidien 05h30 | Synchronisation staging → DW |

### 7.2 Exécuter les tests du pipeline

```bash
make pipeline-test    # pytest avec couverture
make pipeline-lint    # ruff + mypy
```

### 7.3 Exécuter les modèles dbt

```bash
make dbt-run          # Transformations staging → DW
```

---

## 8. Migrations de base de données

Flyway gère les migrations automatiquement au démarrage du backend. Pour les appliquer manuellement :

```bash
make migrate
```

Pour corriger la table `flyway_schema_history` en cas de conflit (environnement existant) :

```sql
-- Se connecter à la base et exécuter :
DELETE FROM flyway_schema_history WHERE version IN ('2','3') AND success = false;
```

---

## 9. Commandes Makefile de référence

```bash
make up ENV=dev|staging|prod    # Démarrer l'environnement
make down                        # Arrêter tous les services
make restart                     # Redémarrer
make logs                        # Logs en temps réel
make ps                          # Lister les conteneurs
make build-backend               # Build image Docker backend
make build-web                   # Build image Docker web
make test-backend                # Tests Spring Boot (Maven)
make test-web                    # Tests Angular (Karma + Jest)
make pipeline-test               # Tests Python (pytest)
make pipeline-lint               # Lint Python (ruff + mypy)
make dbt-run                     # Exécuter les modèles dbt
make migrate                     # Appliquer migrations Flyway
```

---

## 10. Résolution des problèmes courants

### Le backend ne démarre pas (`FlywayValidateException`)

Vérifier que les fichiers de migration sont numérotés sans doublon :

```bash
ls backend/src/main/resources/db/migration/
# Attendu : V1__, V2__, V3__, V4__ (un seul fichier par version)
```

### Connexion Supabase impossible

1. Vérifier `POSTGRES_SSL_MODE=require` dans le `.env`
2. Vérifier que l'IP de déploiement est dans la liste blanche Supabase (Dashboard > Settings > Database > Connection Pooling)
3. Tester la connexion : `psql "postgresql://postgres:<password>@db.<project>.supabase.co:5432/postgres?sslmode=require"`

### Erreur 401 sur tous les endpoints

Le token JWT a expiré (15 minutes par défaut) ou le `JWT_SECRET` diffère entre environnements. Vérifier la variable `JWT_SECRET` dans le `.env`.

### Airflow en état `failed` au démarrage

Airflow nécessite que la base de données soit accessible **avant** son démarrage. Utiliser `healthcheck` dans docker-compose ou attendre 30 secondes après le démarrage de PostgreSQL.

---

*Document rédigé par KOMTSINDI Réné Alban — Institut Universitaire Saint Jean, Yaoundé — 2026*
