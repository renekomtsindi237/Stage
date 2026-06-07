# 02 — Benchmark des Solutions Existantes

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Périmètre du benchmark

Ce benchmark analyse les solutions existantes sur deux axes :
1. **Pipelines de données pour la microfinance** : solutions sectorielles ou génériques.
2. **Modèles de scoring de risque** : approches académiques et pratiques pour la microfinance africaine.

L'objectif est d'identifier les lacunes auxquelles répond la contribution de ce mémoire.

---

## 2. Solutions sectorielles de gestion de la microfinance

### 2.1 Mambu (Cloud CBS)
- **Type :** Core Banking System cloud (SaaS).
- **Forces :** API REST native, déploiement rapide, reporting COBAC partiel.
- **Faiblesses pour ce projet :** Pas de pipeline de données intégré, pas de scoring ML, pas de collecte terrain offline-first, coût de licence prohibitif pour les EMF camerounaises.
- **Verdict :** Solution CBS uniquement, ne couvre pas l'analytique avancée ni le pipeline.

### 2.2 FinancialEdge (Craft Silicon)
- **Type :** CBS installé, largement déployé en Afrique centrale.
- **Forces :** Adapté au contexte africain, gestion des EMF catégorie 1.
- **Faiblesses :** Exports uniquement CSV/Excel, pas d'API, pas de Data Warehouse, pas de scoring, interface vieillissante.
- **Verdict :** Système source (CBS), traité comme boîte noire par ce projet.

### 2.3 MFI Solutions (CGAP / BM)
- **Type :** Toolkit open source pour EMF (spécification, pas implémentation complète).
- **Forces :** Alignement sur les standards CGAP, métriques PAR standardisées.
- **Faiblesses :** Pas de pipeline opérationnel, pas d'application mobile, documentation académique sans code exécutable.
- **Verdict :** Inspiration pour les définitions KPI, non utilisé comme base logicielle.

### 2.4 Apache Fineract
- **Type :** Plateforme open source CBS (Apache Foundation).
- **Forces :** Open source, complet, modulaire, API REST native, standard MIF/CGAP.
- **Faiblesses :** Complexité d'installation, pas de pipeline analytique intégré, pas de scoring ML, pas de mode collecte terrain offline-first.
- **Verdict :** Solution CBS alternative intéressante mais hors périmètre de ce projet.

---

## 3. Plateformes de pipeline de données

### 3.1 Apache Airflow + dbt (approche retenue)
- **Forces :** Open source, écosystème mature, DAGs Python (flexibilité ML), dbt pour transformations SQL versionnées, large adoption en data engineering.
- **Adaptation au contexte :** Idéal pour un pipeline ELT avec orchestration complexe (DAGs différenciés par domaine).
- **Verdict :** Solution retenue — rapport puissance/maintenabilité optimal pour un projet académique.

### 3.2 Apache Kafka + Spark Streaming
- **Forces :** Traitement en temps réel, haute volumétrie.
- **Faiblesses pour ce projet :** Sur-dimensionné pour les volumes ciblés (50K clients), complexité opérationnelle importante, ressources serveur excessives.
- **Verdict :** Non retenu — les volumes ne justifient pas le temps réel pur.

### 3.3 Meltano (ELT open source)
- **Forces :** Connecteurs pré-construits, intègre dbt natif.
- **Faiblesses :** Connecteurs génériques non adaptés aux exports CBS africains, moins flexible pour les collectes offline mobiles.
- **Verdict :** Non retenu — trop générique pour les sources de données hétérogènes du contexte camerounais.

---

## 4. Modèles de scoring de risque crédit — Microfinance

### 4.1 Logistic Regression (modèle de référence)
- **Forces :** Interprétable, peu de données nécessaires.
- **Faiblesses :** Capture mal les non-linéarités des comportements de collecte, performance AUC limitée (typ. 0.65-0.70).
- **Positionnement MCRS :** Utilisé comme baseline de comparaison dans la validation walk-forward.

### 4.2 Random Forest (Berge et al., 2015 — microfinance africaine)
- **Forces :** Capture les non-linéarités, robuste aux valeurs aberrantes.
- **Faiblesses :** Moins performant que XGBoost sur données tabulaires, peu d'outils d'explicabilité intégrés.
- **Positionnement MCRS :** Composante RPS utilise XGBoost (supérieur au RF sur benchmarks tabulaires).

### 4.3 XGBoost + SHAP (approche retenue pour RPS)
- **Forces :** État de l'art sur données tabulaires, SHAP natif via TreeExplainer, calibration Platt disponible.
- **Adaptation :** Walk-forward temporal adapté aux données de crédit, déséquilibre classes géré par `scale_pos_weight`.
- **Références :** Chen & Guestrin (2016), Lundberg & Lee (2017), Diallo et al. (2020 — microfinance Afrique sub-saharienne).

### 4.4 Scores mono-critère (PAR uniquement)
- **Pratique actuelle** dans les EMF camerounaises : classement des clients uniquement par ancienneté de retard.
- **Limite majeure :** Ignore la dynamique des collectes (un client régulier dans l'épargne mais récemment en retard a un profil très différent d'un mauvais payeur récidiviste).
- **Contribution MCRS :** L'intégration du CRS (collecte) dans le score composite corrige cette limite.

---

## 5. Intégration des facteurs externes — Revue de littérature

| Étude | Facteurs utilisés | Résultats |
|---|---|---|
| Mwangi & Murigi (2014) — Kenya | Prix du maïs, précipitations | R² = 0.62 sur défaut agriculteurs |
| Kinda & Tiendrebeogo (2018) — UEMOA | Inflation, taux de change | Corrélation PAR-inflation r=0.71 |
| Diallo et al. (2020) — Afrique sub-saharienne | Prix café, PIB, précipitations | AUC +0.04 vs modèle sans facteurs externes |
| Bédécarrats et al. (2019) — Cameroun | Saisons agricoles, événements locaux | Impact saisonnalité sur remboursements : +23% en période récolte |

**Conclusion de la revue :** L'intégration de facteurs externes améliore systématiquement la performance des modèles de scoring, mais la plupart des études hardcodent des produits spécifiques (café, cacao). La contribution de ce mémoire est de **généraliser** cette intégration via un catalogue de produits génériques configurable.

---

## 6. Synthèse comparative et positionnement

| Dimension | Solutions existantes | Ce projet (contribution) |
|---|---|---|
| Pipeline collectes terrain | Absent | DAG toutes les 2h, offline-first, UUID dedup |
| Calcul PAR COBAC automatisé | Manuel / partiel dans CBS | Snapshots quotidiens automatiques V19-V24 |
| Scoring risque | PAR uniquement (mono-critère) | MCRS composite (CRS+RPS+CSI) |
| Facteurs externes | Hardcodés ou absents | Catalogue générique configurable |
| Explicabilité ML | Absente | SHAP top 10 features par client |
| Benchmarks inter-agences | Absents | Z-scores automatisés quotidiens |
| Détection dérive modèle | Absente | PSI avec retraining automatique |
| Mode offline mobile | Rare en microfinance africaine | Offline-first Flutter + UUID v4 |
| Open source | Variable | 100% open source (pas de licence) |
