# 05 — Exigences Non Fonctionnelles

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Performance

### ENF-P01 — Latence API
- Les endpoints REST doivent répondre en moins de **200 ms** (p95) pour les requêtes de lecture sur les dashboards.
- Le endpoint de synchronisation batch (`POST /api/collectes-epargne/sync`) doit traiter un lot de 200 collectes en moins de **2 secondes**.

### ENF-P02 — Débit pipeline
- Le DAG `dag_collecte_epargne` (toutes les 2 heures) doit terminer son exécution complète en moins de **15 minutes** pour un volume de 10 000 collectes journalières.
- Le DAG `dag_recouvrement` (quotidien 06h00) doit calculer les PAR COBAC pour un portefeuille de 50 000 créances en moins de **30 minutes**.

### ENF-P03 — Scoring ML
- Le DAG `dag_ml_scoring` (quotidien 07h30) doit scorer un portefeuille de 10 000 clients actifs en moins de **20 minutes** (batch de 500 clients).

### ENF-P04 — Dashboard temps réel
- Les mises à jour SSE (Server-Sent Events) doivent être transmises au frontend dans les **5 secondes** suivant la fin d'exécution d'un DAG de mise à jour KPI.

---

## 2. Disponibilité et fiabilité

### ENF-D01 — Disponibilité de l'API
- L'API REST doit être disponible **99.5%** du temps (hors maintenance planifiée).
- Le pipeline Airflow doit réessayer automatiquement les tâches en échec (retries = 3, delay = 5 min).

### ENF-D02 — Idempotence du pipeline
- Toutes les tâches du pipeline doivent être idempotentes : la re-exécution d'un DAG pour une même date ne doit pas produire de doublons dans le Data Warehouse.
- Mise en œuvre : tables partitionnées par date, DELETE/INSERT dans les tables de faits pour la fenêtre concernée.

### ENF-D03 — Mode offline mobile
- L'application mobile doit fonctionner sans connectivité pendant **72 heures minimum**, avec stockage local des collectes et synchronisation automatique au retour en zone connectée.

### ENF-D04 — Sauvegardes
- La base de données PostgreSQL doit être sauvegardée quotidiennement (backup complet) avec rétention de **30 jours**.
- Les modèles ML entraînés (pickle + métadonnées JSON) doivent être versionnés et stockés de manière persistante.

---

## 3. Sécurité

### ENF-S01 — Authentification
- L'authentification est assurée par des JWT httpOnly cookies (`imf_access` : 900 secondes, `imf_refresh` : 7 jours).
- Les cookies sont `Secure` et `SameSite=Strict` en production pour prévenir les attaques CSRF.

### ENF-S02 — Isolation multi-tenant
- Toutes les tables applicatives incluent un champ `imf_id` (NOT NULL).
- Le filtre tenant est appliqué automatiquement au niveau Spring Security via `TenantContext` ; aucune requête ne peut retourner des données d'une autre IMF.

### ENF-S03 — Autorisation par rôle (RBAC)
- Chaque endpoint est sécurisé par une annotation `@PreAuthorize` vérifiant le rôle de l'utilisateur.
- Les rôles sont : `SUPER_ADMIN`, `DIRECTEUR`, `RESPONSABLE_RECOUVREMENT`, `ANALYSTE`, `DSI`, `AGENT`.

### ENF-S04 — Protection des données sensibles
- Les données clients (noms, GPS, revenus) doivent être stockées de manière chiffrée au repos (PostgreSQL TDE ou chiffrement applicatif).
- Les logs du pipeline ne doivent pas contenir de données personnelles identifiables.

### ENF-S05 — Audit
- Toute action de validation/rejet de collecte ou de modification de statut de créance doit être tracée avec l'utilisateur, l'horodatage et le motif.

---

## 4. Maintenabilité et évolutivité

### ENF-M01 — Architecture en couches dbt
- Le pipeline dbt doit être organisé en couches séparées (staging, intermediate, mart, ml) avec des dépendances explicites.
- Chaque modèle dbt doit avoir un fichier YAML de documentation et des tests (not_null, unique, accepted_values).

### ENF-M02 — Extensibilité du catalogue produits
- L'ajout d'un nouveau produit générique dans `app.produits_generiques` doit automatiquement le rendre disponible dans le pipeline de données externes et les features ML, sans modification de code.

### ENF-M03 — Modularité des DAGs
- Chaque DAG Airflow doit être autonome et n'avoir aucune dépendance directe sur le code des autres DAGs.
- Les tâches communes (connexion DB, logging) doivent être factorisées dans des modules partagés (`pipeline/src/utils/`).

### ENF-M04 — Migrations de schéma
- Toutes les modifications de schéma de base de données doivent être gérées via des migrations Flyway numérotées et versionnées (V1 à V24+).
- Les migrations doivent être répétables sans erreur sur une base vierge.

---

## 5. Observabilité

### ENF-O01 — Logs structurés
- Le backend Spring Boot doit produire des logs structurés (JSON) avec les champs : timestamp, level, service, imf_id, user_id, action, durée.
- Le pipeline Airflow doit logger chaque tâche avec : dag_id, task_id, run_date, statut, lignes_traitées, erreurs.

### ENF-O02 — Journal d'ingestion
- Chaque exécution de DAG d'ingestion doit écrire une ligne dans `raw.journal_ingestions` : source, statut, lignes_lues, lignes_valides, lignes_rejetées, durée_ms.

### ENF-O03 — Métriques ML
- Chaque entraînement de modèle doit logger ses métriques dans `ml.model_runs` : AUC, précision, rappel, KS, métriques walk-forward par fold.
- Le PSI (Population Stability Index) doit être calculé quotidiennement et archivé.

---

## 6. Compatibilité

### ENF-C01 — Navigateurs
- L'application web Angular doit fonctionner sur Chrome 120+, Firefox 115+, Edge 120+.

### ENF-C02 — Mobile
- L'application Flutter doit fonctionner sur Android 8.0+ (API 26+).

### ENF-C03 — Internationalisation
- L'application web doit supporter le français (langue principale) et l'anglais (secondaire) via `ngx-translate`.
