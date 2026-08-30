# MicroRecouv — Blocs techniques détaillés

---

## Bloc 1 — Application mobile Flutter (collectes offline-first)

### Ce que fait ce bloc
L'agent de terrain utilise l'application pour enregistrer les collectes d'épargne même sans connexion Internet. L'application fonctionne entièrement hors ligne et synchronise les données dès que la connexion revient.

### Composants clés

```
mobile/lib/
├── core/
│   ├── models/collecte_locale.dart   → entité locale (UUID + SQLite)
│   ├── models/user.dart              → profil + avatarUrl
│   ├── services/api_service.dart     → client HTTP Dio (JWT auto, refresh, multipart)
│   ├── services/sync_service.dart    → logique de synchronisation batch
│   └── providers/auth_provider.dart  → état de session (Provider)
├── screens/
│   ├── collectes/
│   │   ├── nouvelle_collecte_screen.dart  → saisie d'une collecte
│   │   └── historique_jour_screen.dart    → liste du jour
│   ├── clients/clients_list_screen.dart   → liste des clients
│   └── profil/profil_screen.dart          → photo de profil, déconnexion
└── app.dart  → GoRouter (navigation déclarative)
```

### Flux d'une collecte offline

```
1. Agent ouvre NouvelleCollecteScreen
2. Sélectionne le client → montant → canal (MTN / Orange / ESPECES / WAVE)
3. _generateUuid() génère un UUID v4 RFC 4122 valide
4. CollecteLocale sauvegardée dans SQLite (`collectes_pending`) ; GPS en `gps_pending`
5. ...agent reprend la connexion...
6. SyncService.syncNow() est déclenché
7. Collecte les EN_ATTENTE, construit le payload :
   {
     syncId, deviceId, clientSyncTimestamp,
     items: [{ idCollecteMobile (UUID), clientId, montantCollecte, ... }]
   }
8. POST /api/v1/sync/collectes + flush GPS
9. SUCCESS / DOUBLON → journal `collectes_synced` ; CONFLIT / ERREUR → gardés
10. Bascule serveur (local / staging / prod) : outbox conservée, cache retéléchargé
```

### Génération de l'UUID v4 (format RFC 4122)

```dart
String _generateUuid() {
  final r = Random.secure();
  String seg(int len) =>
      List.generate(len, (_) => r.nextInt(16).toRadixString(16)).join();
  final v = (8 + r.nextInt(4)).toRadixString(16); // variant bits = 10xx
  return '${seg(8)}-${seg(4)}-4${seg(3)}-$v${seg(3)}-${seg(12)}';
}
// Exemple : a3f8c201-7b4d-4e2a-b91f-3d88c6f012aa
```

Le `4` fixe le version bit (UUID v4 = aléatoire). Le `v` fixe les variant bits (RFC 4122 = `10xx`).

---

## Bloc 2 — Backend Spring Boot (API centrale)

### Ce que fait ce bloc
Le backend est l'unique point d'entrée pour toutes les requêtes. Il applique la sécurité (JWT, rôles), la logique métier et persiste les données.

### Architecture interne

```
Request
  → JwtFilter (extrait imf_id, userId, role → TenantContext)
  → Controller (validation @Valid, mapping DTO)
  → Service (logique métier, @Transactional)
  → Repository (JPA + filtre automatique imf_id)
  → PostgreSQL (schéma app.*)
```

### Modules principaux

| Package | Rôle |
|---|---|
| `controller/` | Endpoints REST : /clients, /collectes, /sync, /users, /scoring… |
| `service/` | Logique métier : SyncService, ScoringService, UserService… |
| `security/` | JwtFilter, DataMaskingUtils, SecurityConfig |
| `dto/` | Request/Response (séparation modèle/API) |
| `event/` | SSE : ScoringUpdatedEvent → Angular dashboard |

### Synchronisation mobile (SyncController)

```
POST /api/v1/sync/collectes
  Body: SyncRequest {
    syncId, deviceId, clientSyncTimestamp,
    items: [CollecteRequest { idCollecteMobile, clientId,
                              dateCollecte, montantCollecte,
                              canalPaiement, pretId? }]
  }

Pour chaque item :
  ┌─ idCollecteMobile déjà en base ? → code = "DOUBLON"
  ├─ clientId invalide ?             → code = "ERREUR"
  └─ OK                             → INSERT + code = "SUCCESS"

Retour: SyncResponse {
  data: {
    stats: { total, succes, doublons, conflits, erreurs },
    resultats: [{ idCollecteMobile, code }]
  }
}
```

### Masquage des données PII (Loi 2024/017)

```java
// DataMaskingUtils.peutVoirDonneesCompletes(role)
// Rôles autorisés à voir les données réelles :
AGENT, RESPONSABLE_RECOUVREMENT, DIRECTEUR, DSI, SUPER_ADMIN

// Rôles masqués (ex : ANALYSTE) :
"Abossolo François Atangana" → "A*** F*** A***"
```

### Scoring MCRS déclenché à la demande

Le backend appelle le service FastAPI ML :

```java
// MlScoringClient.java
POST http://ml-api:8090/score
  Body: { client_id_externe, imf_id, regularite_collecte_pct, ... (30 features) }

Réponse: { mcrs, crs, rps, csi, risque, alertes, cobac_classe, cobac_provision_taux }
```

---

## Bloc 3 — Base de données PostgreSQL (schémas)

### Ce que fait ce bloc
PostgreSQL contient l'intégralité des données, organisées en couches séparées par schéma SQL.

### Organisation des schémas

```
app.*         → données opérationnelles (source de vérité)
               clients_informels, collectes_terrain, creances,
               dossiers_recouvrement, utilisateurs, agences, imfs

raw.*         → données brutes ingérées par Airflow
               raw.collectes_mtn, raw.collectes_orange,
               raw.prix_produits, raw.donnees_meteo, raw.indicateurs_macro

staging.*     → données nettoyées par dbt (stg_clients, stg_creances, ...)

dw.*          → Data Warehouse en étoile
               dim_client, dim_agence, dim_agent, dim_date, dim_produit
               fact_collectes, fact_remboursements

ml.*          → feature store + résultats ML
               ml.features_client    → 30 features par client (quotidien)
               ml.client_scores      → score MCRS, CRS, RPS, CSI du jour
               ml.shap_explanations  → top 10 features SHAP par client
               ml.model_runs         → historique des entraînements
```

### Schéma en étoile (Data Warehouse)

```
        dim_date ←── fact_collectes ──→ dim_agent
                            │
                       dim_client ──→ dim_agence
                            │
                       dim_produit

        dim_date ←── fact_remboursements ──→ dim_client
```

Ce schéma permet des requêtes analytiques rapides type :
*"Total des collectes du mois par agence pour les clients café-arabica en période de récolte"*

---

## Bloc 4 — Apache Airflow (orchestration des DAGs)

### Ce que fait ce bloc
Airflow planifie et enchaîne tous les traitements automatiques. Chaque DAG est un graphe de tâches Python avec dépendances explicites.

### DAGs principaux

| DAG | Heure | Rôle |
|---|---|---|
| `dag_collectes` | 06h00 | Validation et agrégation des collectes du jour |
| `dag_ingestion_mtn` | 05h30 | Import relevés collectes MTN Mobile Money |
| `dag_ingestion_orange` | 05h30 | Import relevés collectes Orange Money |
| `dag_donnees_externes` | 06h30 | Prix marchés + météo ANAMET + macro BEAC/INS |
| `dag_recouvrement` | 07h00 | Calcul PAR COBAC + mise à jour dossiers |
| `dag_kpis_quotidien` | 07h15 | Calcul KPI dashboard (dbt run mart.*) |
| `dag_ml_scoring` | 07h30 | Score MCRS par client (XGBoost + CRS + CSI) |
| `dag_ml_training` | dim 02h00 | Réentraînement hebdomadaire du modèle |
| `dag_alertes_impayes` | 08h00 | Alertes PAR30/PAR90 → FCM/SSE |
| `dag_backup` | 02h00 | Sauvegarde PostgreSQL + R2 |

### Exemple — dag_ml_scoring (graphe de tâches)

```
debut
  ├── feat_comportemental (dbt: régularité collecte, historique)
  └── feat_externe (dbt: prix produit, météo, inflation)
        └── assembler_feature_store (dbt: ml.features_client)
              └── charger_modele (charge pkl champion)
                    └── scorer_clients (batch de 500, INSERT ml.client_scores)
                          ├── calculer_shap (top 10 features → ml.shap_explanations)
                          │     ├── generer_alertes_ml
                          │     ├── maj_priorites_dossiers
                          │     └── detecter_drift (PSI > 0.20 ?)
                          │           └── [brancher_retrain]
                          │                 ├── declencher_retrain (trigger dag_ml_training)
                          │                 └── skip_retrain
                          ├── notifier_responsables_sse
                          └── notifier_directeurs_fcm (alertes critiques)
                                └── log_journal
                                      └── fin
```

### Communication entre tâches : XCom

```python
# Tâche A pousse une valeur
ti.xcom_push(key="dataset_summary", value={"n_rows": 2000, "taux_defaut": 16.0})

# Tâche B la récupère
summary = ti.xcom_pull(task_ids="preparer_dataset", key="dataset_summary")
```

---

## Bloc 5 — dbt (transformations SQL)

### Ce que fait ce bloc
dbt transforme les données brutes en passant par des couches successives, avec tests de qualité automatiques à chaque étape.

### Chaîne de transformation

```
raw.collectes_mtn         ┐
raw.collectes_orange      ├── stg_collectes_terrain  ──→ int_comportement_collecte
raw.prix_produits         ├── stg_prix_produits      ──→ int_contexte_externe
raw.donnees_meteo         ├── stg_meteo              ┘
raw.indicateurs_macro     └── stg_indicateurs_macro  ──→ int_risque_credit
                                                            │
app.collectes_terrain ──→ stg_collectes_epargne             │
app.creances          ──→ stg_creances                      │
app.clients_informels ──→ stg_clients                       │
                               └────────────────────────────┼───→ ml.features_client (30 features)
                                                            │
                               ┌────────────────────────────┘
                               ↓
                          dw.fact_collectes
                          dw.fact_remboursements
                          mart.kpi_collecte
                          mart.kpi_recouvrement
                          mart.benchmarks_agences
```

### Exemple — int_comportement_collecte

Ce modèle calcule les features CRS sur une fenêtre glissante de 90 jours :

```sql
-- Régularité : semaines avec au moins une collecte / 13 semaines
ROUND(semaines_avec_collecte::numeric / 13, 4)  AS regularite_collecte_pct

-- Coefficient de variation (instabilité des montants)
ROUND(COALESCE(ecart_type_montant, 0) / NULLIF(montant_moyen_collecte, 0), 4)
    AS coefficient_variation_collecte

-- Tendance : ratio montant 30 derniers jours / 30 jours précédents - 1
ROUND(montant_30j_recent / NULLIF(montant_30j_precedent, 1), 4) - 1
    AS tendance_collecte_30j

-- Rang dans l'agence (percentile collecte)
PERCENT_RANK() OVER (PARTITION BY agence_id ORDER BY montant_total_90j)
    AS rang_collecte_agence
```

---

## Bloc 6 — Service ML FastAPI (port 8090)

### Ce que fait ce bloc
Expose le modèle MCRS entraîné comme un microservice HTTP interne. Le backend Java l'appelle pour scorer un client ou un batch.

### Endpoints

| Méthode | URL | Description |
|---|---|---|
| GET | `/model/health` | Santé + modèle chargé ? |
| GET | `/model/info` | Version, AUC, nombre de features |
| POST | `/score` | Score MCRS d'un client (30 features en JSON) |
| POST | `/score/batch` | Score batch (liste de clients) |
| GET | `/config` | Configuration MCRS actuelle |
| PUT | `/config/weights` | Mise à jour des poids à chaud (DSI) |
| PUT | `/config/thresholds` | Mise à jour des seuils à chaud (DSI) |

### Cycle de vie du modèle

```
Démarrage FastAPI
    → load_model() charge champion/mcrs_model.pkl
    → MCRSScorer initialisé (XGBoost + scoring_config.json)

POST /score
    → MCRSScorer.score(row_dict)
    → Retourne { mcrs, crs, rps, csi, risque, alertes, cobac_classe, cobac_provision_taux }

PUT /config/weights
    → MCRSScorer.update_weights(w_crs, w_rps, w_csi)
    → scoring_config.json mis à jour sur disque
    → Prochain score utilise les nouveaux poids (sans redémarrage)

dag_ml_training (dimanche 02h00 ou drift PSI > 0.20)
    → Nouveau modèle challenger entraîné
    → Comparaison AUC champion vs challenger
    → Si challenger meilleur → copié dans champion/
    → Symlink mcrs_model.pkl mis à jour
    → POST /model/reload → FastAPI recharge le modèle en mémoire
```

---

## Bloc 7 — Frontend Angular (tableaux de bord)

### Ce que fait ce bloc
Interface web pour les directeurs, responsables recouvrement, analystes et administrateurs (DSI).

### Structure des modules

```
frontend/src/app/
├── core/
│   ├── auth/auth.service.ts          → JWT, session, uploadAvatar(), removeAvatar()
│   ├── models/user.model.ts          → User, Role, AuthResponse
│   └── http/api.service.ts           → intercepteurs HTTP, token auto
├── features/
│   ├── directeur/
│   │   ├── dir-dashboard/            → PAR, collectes, MCRS agrégé
│   │   └── dir-clients/              → liste clients avec scores
│   ├── recouvrement/                 → dossiers priorisés par MCRS
│   ├── agent/                        → saisie web des collectes
│   ├── profile/                      → photo de profil (upload R2)
│   └── dsi/                          → gestion utilisateurs, logo IMF
└── shared/
    ├── topbar/                        → navigation + avatar temps réel
    └── notification-panel/            → alertes SSE (PAR, MCRS critique)
```

### Temps réel via SSE

```typescript
// Le backend pousse des événements sans que le client n'ait besoin de poller
EventSource: GET /api/v1/sse/stream (avec token JWT)

Événements reçus :
  "scoring_updated"  → dashboard recalcule les scores à l'écran
  "alerte_par"       → notification PAR90 dépassé
  "collecte_sync"    → nouvelle collecte confirmée depuis mobile
```

### Signals Angular 18 (réactivité)

```typescript
// auth.service.ts
readonly currentUser = signal<User | null>(null);
readonly avatarUrl   = computed(() => this.currentUser()?.avatarUrl ?? null);

// Dans la topbar — se met à jour automatiquement quand avatarUrl change
<img [src]="avatarSrc" />   // avatarSrc = getter calculé depuis avatarUrl signal
```

---

## Bloc 8 — Client bureau Tauri (Windows)

### Ce que fait ce bloc
Le même frontend Angular est empaqueté dans une fenêtre native. Les postes d’agence (directeur, recouvrement, DSI, analyste) installent un Setup.exe comme une application bureautique. L’API reste `https://imf.rene.it.com`.

### Organisation

```
desktop/
├── package.json                  → scripts tauri (dev / build)
├── src-tauri/
│   ├── tauri.conf.json           → fenêtre, CSP, installeur NSIS
│   ├── src/lib.rs                → point d’entrée Rust
│   └── icons/                    → icon.ico depuis MicroRecouv.png
frontend/src/environments/environment.desktop.ts
                                  → apiUrl + navigation hash
```

### Installation

Fichier livré : `desktop/dist/MicroRecouv_1.0.0_x64-setup.exe`.

Menu Démarrer (dossier MicroRecouv), raccourci Bureau optionnel, désinstallation depuis Paramètres Windows. Icône : logo `MicroRecouv.png` (Setup + application). Auth : JWT Bearer (pas les cookies `SameSite=Strict`). CORS : origines `https://tauri.localhost` fusionnées côté Spring.

Guide : `docs/desktop.md`. Régénérer les icônes : `cd desktop && npm run icons`.

---

## Bloc 9 — Kafka + Flink (streaming temps réel)

### Ce que fait ce bloc
Traitement des événements en flux continu pour les alertes qui ne peuvent pas attendre le prochain batch Airflow.

### Topologie Kafka

```
Topics :
  collectes-terrain-events   → producteur : backend (après chaque sync)
  scoring-alerts             → producteur : Flink (alertes MCRS critique)
  kpi-realtime               → producteur : Flink (PAR glissant)
  notifications              → consommateur : backend (push FCM/SSE)
```

### Job Flink — alerte MCRS temps réel

```python
# flink/alerte_mcrs_stream_job.py
# Flux : collectes-terrain-events → calcul CRS fenêtre 5 min → alerte si dégradation

stream
  .key_by("client_id")
  .window(TumblingEventTimeWindows.of(Time.minutes(5)))
  .aggregate(CRSAggregate())
  .filter(lambda r: r.crs_delta < -0.15)  # dégradation rapide
  .add_sink(KafkaSink("scoring-alerts"))
```

---

## Récapitulatif des interactions entre blocs

```
Flutter ──(sync batch)──→ Spring Boot ──(INSERT)──→ PostgreSQL
                               │                         │
                               │ (appel scoring)         │ (Airflow lit)
                               ↓                         ↓
                           FastAPI ML              dbt transformations
                               ↑                         │
                               │ (features 30j)          ↓
                        Airflow dag_ml_scoring    ml.features_client
                               │                         │
                               └─────────────────────────┘
                                       │
                               ml.client_scores (MCRS)
                                       │
                               Spring Boot SSE ──→ Angular dashboard
                               Spring Boot FCM ──→ Flutter notifications
```
