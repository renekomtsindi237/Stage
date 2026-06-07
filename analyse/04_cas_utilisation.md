# 04 — Cas d'Utilisation

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

> Voir le diagramme UML complet : `docs/uml/01_use_case.puml`

---

## 1. Acteurs du système

| Acteur | Type | Description |
|---|---|---|
| AGENT | Humain primaire | Agent de collecte terrain |
| RESPONSABLE_RECOUVREMENT | Humain primaire | Gestionnaire de portefeuille créances |
| DIRECTEUR | Humain primaire | Directeur d'agence ou DG |
| ANALYSTE | Humain primaire | Data/risk analyst |
| DSI | Humain primaire | Administrateur technique IMF |
| SUPER_ADMIN | Humain primaire | Administrateur plateforme SaaS |
| APPLICATION_MOBILE | Système | App Flutter de l'agent |
| PIPELINE | Système | Airflow DAGs + dbt + ML |
| CBS | Système externe | Core Banking System de l'IMF |
| SOURCES_EXTERNES | Système externe | Open-Meteo, MINCOMMERCE, BEAC/INS |

---

## 2. Cas d'utilisation — Domaine Collectes d'Épargne

### UC-CE01 — Saisir une collecte (mobile offline)
**Acteur principal :** AGENT (via APPLICATION_MOBILE)
**Précondition :** Agent authentifié sur l'application mobile.
**Scénario nominal :**
1. L'agent sélectionne le client dans sa liste.
2. Il saisit le montant, le canal (ESPECES/MOBILE_MONEY), la date.
3. L'application génère un UUID v4 et enregistre la collecte localement (SQLite, statut PENDING).
4. L'application confirme l'enregistrement local à l'agent.
**Scénario alternatif (en zone connectée) :** La synchronisation est déclenchée immédiatement après la saisie.
**Postcondition :** Collecte stockée localement avec UUID unique.

### UC-CE02 — Synchroniser les collectes
**Acteur principal :** APPLICATION_MOBILE, AGENT
**Précondition :** Agent connecté au réseau ; collectes PENDING en attente.
**Scénario nominal :**
1. L'application envoie le batch de collectes via `POST /api/collectes-epargne/sync`.
2. Le backend déduplique par UUID, valide les formats, enregistre dans `app.collectes_epargne`.
3. L'application reçoit la réponse : acceptées, doublons, rejetées.
4. Les collectes acceptées passent en statut SYNCHRONISEE côté mobile.
**Postcondition :** Collectes disponibles dans le système central pour validation.

### UC-CE03 — Valider les collectes d'une agence
**Acteur principal :** RESPONSABLE_RECOUVREMENT
**Précondition :** Collectes SOUMISE disponibles.
**Scénario nominal :**
1. Le responsable consulte la liste des collectes en attente de validation.
2. Il sélectionne une ou plusieurs collectes.
3. Il valide (statut → VALIDEE) ou rejette (statut → REJETEE, motif obligatoire).
**Postcondition :** Collectes validées intégrées dans les KPI officiels du prochain calcul DAG.

### UC-CE04 — Consulter son KPI journalier
**Acteur principal :** AGENT
**Scénario nominal :**
1. L'agent consulte `GET /api/collectes-epargne/mon-kpi-jour`.
2. Il voit : montant collecté du jour, objectif du cycle, taux de réalisation, trend vs semaine précédente.

### UC-CE05 — Consulter le dashboard collectes (DIRECTEUR)
**Acteur principal :** DIRECTEUR
**Scénario nominal :**
1. Le directeur ouvre le dashboard.
2. Il voit les KPI collectes consolidés (montant jour, taux objectif IMF, alertes ML).
3. Les données se mettent à jour en temps réel via SSE.

---

## 3. Cas d'utilisation — Domaine Recouvrement de Créances

### UC-RC01 — Ingérer un export CBS
**Acteur principal :** PIPELINE (dag_recouvrement), CBS
**Scénario nominal :**
1. Le CBS dépose un export CSV dans la zone de transfert (SFTP ou volume partagé).
2. Le DAG détecte le fichier, valide le format, charge dans `raw.export_cbs`.
3. dbt calcule les jours de retard, la classification COBAC, les provisions.
4. `app.creances` est mis à jour.
**Postcondition :** Créances avec classification COBAC à jour ; snapshot KPI archivé.

### UC-RC02 — Consulter le dashboard recouvrement
**Acteur principal :** RESPONSABLE_RECOUVREMENT
**Scénario nominal :**
1. Le responsable ouvre le dashboard recouvrement.
2. Il voit : PAR30, PAR90, dossiers prioritaires (triés par score MCRS), promesses à suivre.
3. Il peut filtrer par agence et par classe COBAC.

### UC-RC03 — Consulter le score MCRS d'un client
**Acteur principal :** RESPONSABLE_RECOUVREMENT, ANALYSTE
**Précondition :** Le scoring MCRS du jour a été calculé.
**Scénario nominal :**
1. L'utilisateur accède à la fiche du client (`GET /api/creances/client/{id}/score-mcrs`).
2. Il voit : score MCRS global, décomposition CRS/RPS/CSI, classification CRITIQUE/ÉLEVÉ/MODÉRÉ/FAIBLE.
3. Il consulte le top 10 SHAP : "Les 3 principales raisons du risque élevé sont : jours_retard (impact +0.18), baisse_prix_manioc (-0.12), indice_sécheresse (+0.09)."
4. Il voit l'action recommandée par le modèle.

### UC-RC04 — Enregistrer une promesse de paiement
**Acteur principal :** RESPONSABLE_RECOUVREMENT
**Scénario nominal :**
1. Lors d'un contact client, le responsable enregistre la promesse : montant, date, modalité.
2. La promesse est enregistrée dans `app.promesses_paiement` (statut EN_ATTENTE).
3. À la date prévue, le pipeline vérifie automatiquement si la promesse a été honorée (via CBS).
4. Statut mis à jour : RESPECTEE / ROMPUE → alerte si ROMPUE.

### UC-RC05 — Déclencher une action de recouvrement
**Acteur principal :** RESPONSABLE_RECOUVREMENT
**Scénario nominal :**
1. Le responsable sélectionne un dossier prioritaire.
2. Il enregistre l'action : RELANCE_AMIABLE, MISE_EN_DEMEURE, CONTENTIEUX, REECHELONNEMENT.
3. L'action est tracée dans `app.dossiers_recouvrement`.

---

## 4. Cas d'utilisation — Domaine Données Externes

### UC-DE01 — Ingérer les prix des produits génériques
**Acteur principal :** PIPELINE (dag_donnees_externes)
**Scénario nominal :**
1. Le DAG fetch les prix depuis MINCOMMERCE, APIs, ou charge des relevés manuels.
2. Les prix sont stockés dans `app.prix_produits` avec score de fiabilité.
3. Les modèles dbt calculent les moyennes mobiles et variations.
4. Le feature store ML `feat_client_externe` est mis à jour.

### UC-DE02 — Consulter les tendances prix produits
**Acteur principal :** DIRECTEUR, ANALYSTE
**Scénario nominal :**
1. L'utilisateur consulte le dashboard directeur.
2. Il voit les 6 derniers prix de produits génériques avec variations sur 30 jours.
3. Une flèche rouge/verte indique la tendance (hausse = risque de solvabilité accru pour les producteurs du produit concerné).

---

## 5. Cas d'utilisation — Domaine ML et Alertes

### UC-ML01 — Scorer le portefeuille (quotidien)
**Acteur principal :** PIPELINE (dag_ml_scoring)
**Scénario nominal :**
1. Le DAG assemble les 43 features par client.
2. Le modèle MCRS champion est chargé et appliqué par batch de 500.
3. Les scores CRS/RPS/CSI/MCRS et les SHAP values sont stockés.
4. Les alertes prédictives sont générées pour les clients CRITIQUE et ÉLEVÉ.
5. Si PSI > 0.20 (dérive détectée), `dag_ml_training` est déclenché.

### UC-ML02 — Recevoir une alerte prédictive
**Acteur principal :** RESPONSABLE_RECOUVREMENT (via notification push ou SSE)
**Scénario nominal :**
1. Le pipeline détecte un client avec MCRS passant de ÉLEVÉ à CRITIQUE.
2. Une alerte `RISQUE_DEFAUT_IMMINENT` est générée.
3. Le responsable reçoit une notification push (FCM) et/ou SSE sur le dashboard.
4. Il peut cliquer directement vers la fiche du client.

### UC-ML03 — Réentraîner le modèle MCRS
**Acteur principal :** PIPELINE (dag_ml_training)
**Déclencheur :** PSI > 0.20 ou schedule hebdomadaire (dimanche 02h00).
**Scénario nominal :**
1. Le dataset 2 ans est préparé.
2. Walk-forward 5 folds : entraînement + validation.
3. Calibration Platt.
4. Si AUC challenger > AUC champion + 0.005 : promotion du nouveau modèle.
5. Métriques loggées dans `ml.model_runs`.

---

## 6. Cas d'utilisation — Administration

### UC-AD01 — Créer une nouvelle IMF
**Acteur principal :** SUPER_ADMIN
**Scénario :** Création du tenant IMF avec configuration initiale (agences, objectifs, catalogue produits).

### UC-AD02 — Configurer les objectifs de collecte
**Acteur principal :** DIRECTEUR, DSI
**Scénario :** Définir les objectifs par agent/agence/cycle via l'interface admin.

### UC-AD03 — Gérer les utilisateurs
**Acteur principal :** DSI
**Scénario :** Créer/modifier/désactiver les comptes utilisateurs avec attribution de rôle et d'agence.
