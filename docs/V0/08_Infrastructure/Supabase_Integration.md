# Intégration Supabase — MicroRecouv

> Document destiné à un étudiant ISI 4e année.
> Niveau prérequis : connaissance de PostgreSQL, Docker, variables d'environnement.

---

## 1. Pourquoi Supabase ?

MicroRecouv utilisait initialement un conteneur PostgreSQL Docker local pour tous les environnements. Cette approche convient au développement, mais présente des limites en staging/production :

| Problème Docker local | Solution Supabase |
|---|---|
| Backups manuels | Backups automatiques quotidiens (PITR sur plan Pro) |
| SSL absent par défaut | SSL/TLS obligatoire, certificat géré par Supabase |
| Gestion des credentials manuelle | Dashboard centralisé, rotation de clés |
| Haute disponibilité non garantie | Infrastructure PostgreSQL managée (99,9 % SLA) |
| Pas de Realtime intégré | Supabase Realtime (WebSockets) pour les événements live |

Supabase est open-source (basé sur PostgreSQL 15+). La connexion se fait via le driver PostgreSQL standard — aucune modification du code SQL ou des migrations Flyway n'est nécessaire.

---

## 2. Architecture par environnement

```
dev       →  PostgreSQL Docker local (localhost:5432)
              OU Supabase dev (section commentée dans .env.dev)

staging   →  Supabase projet "staging" (db.STAGING_PROJECT_ID.supabase.co)

prod      →  Supabase projet "prod"    (db.PROD_PROJECT_ID.supabase.co)
```

Chaque environnement a son propre projet Supabase isolé (base de données séparée, clés séparées).

---

## 3. Créer un projet Supabase

1. Aller sur [https://supabase.com](https://supabase.com) et créer un compte.
2. Cliquer **New project**, choisir une région proche du Cameroun (Europe West ou US East).
3. Donner un nom : `microrecouv-staging` ou `microrecouv-prod`.
4. Définir un mot de passe de base de données fort (le noter — il ne sera plus affiché).
5. Attendre ~2 minutes que la base se provisionne.

---

## 4. Variables d'environnement à configurer

Dans le dashboard Supabase : **Settings > API** et **Settings > Database**.

### 4.1 Récupérer les valeurs

| Variable | Où la trouver dans Supabase |
|---|---|
| `SUPABASE_URL` | Settings > API > Project URL |
| `SUPABASE_ANON_KEY` | Settings > API > Project API keys > anon public |
| `SUPABASE_SERVICE_ROLE_KEY` | Settings > API > Project API keys > service_role |
| `POSTGRES_HOST` | Settings > Database > Connection string > Host (ex: `db.xxxx.supabase.co`) |
| `POSTGRES_PASSWORD` | Le mot de passe défini à la création du projet |
| `POSTGRES_DB` | Toujours `postgres` sur Supabase |
| `POSTGRES_USER` | Toujours `postgres` sur Supabase |

### 4.2 Exemple pour staging (`.env.staging`)

```env
SUPABASE_URL=https://abcdefghij.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
POSTGRES_HOST=db.abcdefghij.supabase.co
POSTGRES_PORT=5432
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=mon_mot_de_passe_fort
POSTGRES_SSL_MODE=require
```

> **Important** : ne jamais committer ces valeurs dans Git. Utiliser GitHub Secrets ou un vault pour les environnements CI/CD.

---

## 5. Setup dev vs staging vs prod

### Développement local (défaut — Docker)

```bash
# Lancer la stack complète en local
cp .env.dev .env
docker compose up -d
```

PostgreSQL tourne dans Docker, aucun changement nécessaire.

### Passer en mode Supabase dev (optionnel)

Dans `.env.dev`, commenter la ligne `POSTGRES_HOST=localhost` et décommenter la section Supabase :

```env
# POSTGRES_HOST=localhost    # <- commenter cette ligne
# ...
# Décommenter :
POSTGRES_HOST=db.xxxxxxxxxxxxx.supabase.co
POSTGRES_SSL_MODE=require
# etc.
```

### Staging

```bash
# Sur le VPS staging
cp .env.staging .env
docker compose -f docker-compose.staging.yml up -d
```

Le service `postgres` local n'existe plus dans `docker-compose.staging.yml` — Supabase le remplace. Les services Airflow utilisent la connection string Supabase avec `?sslmode=require`.

### Production

```bash
# Sur le serveur app
cp .env.prod .env
docker compose -f docker-compose.prod.yml --profile app up -d

# Sur le serveur pipeline
docker compose -f docker-compose.prod.yml --profile pipeline up -d
```

---

## 6. Migration depuis PostgreSQL local vers Supabase

### Étape 1 — Exporter la base locale

```bash
# Depuis le conteneur Docker ou en local
pg_dump \
  -h localhost -p 5432 \
  -U imf_user -d imf_db \
  --no-owner --no-acl \
  -F c -f backup_imf.dump
```

### Étape 2 — Créer les schémas sur Supabase

Supabase crée uniquement le schéma `public` par défaut. MicroRecouv utilise `app`, `staging`, `dw` :

```sql
-- Exécuter dans l'éditeur SQL du dashboard Supabase
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS staging;
CREATE SCHEMA IF NOT EXISTS dw;
```

### Étape 3 — Importer le dump

```bash
pg_restore \
  -h db.VOTRE_PROJECT_ID.supabase.co \
  -p 5432 \
  -U postgres \
  -d postgres \
  --no-owner --no-acl \
  -F c backup_imf.dump
```

Le mot de passe Supabase sera demandé.

### Étape 4 — Vérifier les migrations Flyway

Au premier démarrage du backend Spring avec le profil staging/prod, Flyway détecte la base vide et applique toutes les migrations depuis `classpath:db/migration`. C'est le comportement normal grâce à `baseline-on-migrate: true`.

---

## 7. SSL — Détails techniques

### Pourquoi SSL est obligatoire sur Supabase

Supabase refuse les connexions non-chiffrées depuis l'extérieur. Le driver PostgreSQL JDBC et psycopg2 doivent déclarer `sslmode=require`.

### Configuration Spring Boot (application.yml)

```yaml
datasource:
  url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}?sslmode=${POSTGRES_SSL_MODE:disable}
  hikari:
    data-source-properties:
      ssl: ${POSTGRES_SSL_ENABLED:false}
      sslmode: ${POSTGRES_SSL_MODE:disable}
```

En staging/prod, `POSTGRES_SSL_MODE=require` active le chiffrement.

### Configuration Python pipeline (config.py)

```python
ssl_mode: str = Field(default="disable", alias="POSTGRES_SSL_MODE")

@property
def dsn(self) -> str:
    base = f"host={self.host} port={self.port} dbname={self.db} user={self.user} password={self.password}"
    if self.ssl_mode and self.ssl_mode != "disable":
        base += f" sslmode={self.ssl_mode}"
    return base
```

---

## 8. Supabase Realtime — Alertes (futur)

Supabase Realtime permet de recevoir des événements en temps réel via WebSockets lorsque des lignes sont insérées/modifiées dans une table PostgreSQL.

### Cas d'usage prévu

Quand le pipeline ETL insère une nouvelle alerte dans la table `app.alertes`, l'application mobile Flutter peut être notifiée instantanément sans polling.

### Architecture envisagée

```
Pipeline Python
   → INSERT INTO app.alertes
        ↓
   Supabase Realtime (CDC sur WAL PostgreSQL)
        ↓
   Flutter app (supabase_flutter ^2.5.0)
        ↓
   Affichage notification in-app
```

### Activer Realtime sur la table alertes

Dans le dashboard Supabase : **Database > Replication > Tables** — activer `app.alertes` pour les events `INSERT`.

### Code Flutter (exemple futur)

```dart
// Dans un widget ou service Flutter
final supabase = Supabase.instance.client;

supabase
  .from('alertes')
  .stream(primaryKey: ['id'])
  .listen((List<Map<String, dynamic>> data) {
    // Traiter les nouvelles alertes
  });
```

Le package `supabase_flutter: ^2.5.0` est déjà ajouté dans `mobile/pubspec.yaml`.

### Initialisation Supabase Flutter

```dart
// Dans main.dart, avant runApp()
await Supabase.initialize(
  url: const String.fromEnvironment('SUPABASE_URL'),
  anonKey: const String.fromEnvironment('SUPABASE_ANON_KEY'),
);
```

> Note : En attendant l'implémentation Realtime, les alertes sont diffusées via l'endpoint SSE du backend Spring (`/api/sse/alertes`). Les deux approches coexisteront pendant la transition.

---

## 9. Sécurité — Bonnes pratiques

- **Ne jamais exposer** la `SERVICE_ROLE_KEY` côté client Flutter. Cette clé contourne Row Level Security (RLS).
- La `ANON_KEY` est sûre pour le client mobile — elle respecte les politiques RLS.
- Activer RLS sur toutes les tables si Supabase est utilisé directement depuis Flutter (pas seulement via le backend Spring).
- Régénérer les clés JWT depuis le dashboard si elles sont compromises : **Settings > API > JWT Settings > Generate new secret**.
- Les credentials Supabase en production doivent transiter uniquement via GitHub Secrets ou HashiCorp Vault, jamais en clair dans les fichiers `.env.prod`.

---

## 10. Références

- [Supabase Documentation](https://supabase.com/docs)
- [Supabase Flutter SDK](https://supabase.com/docs/reference/dart/introduction)
- [PostgreSQL JDBC SSL](https://jdbc.postgresql.org/documentation/ssl/)
- [psycopg2 SSL](https://www.psycopg.org/docs/module.html#psycopg2.connect)
- [Supabase Realtime](https://supabase.com/docs/guides/realtime)
