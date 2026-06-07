# 01 — Contexte et Problématique

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Contexte général

Le secteur de la microfinance au Cameroun compte plus de 400 Établissements de Microfinance (EMF) agréés par la COBAC (Commission Bancaire de l'Afrique Centrale), regroupant plusieurs millions d'épargnants et emprunteurs issus principalement des secteurs informels : agriculture, petit commerce, artisanat, élevage. Ces institutions constituent le principal mécanisme d'inclusion financière pour les populations non bancarisées des zones rurales et périurbaines camerounaises.

Les EMF exercent deux activités opérationnelles centrales interdépendantes :

1. **La collecte d'épargne terrain** : des agents se déplacent physiquement chez les clients pour collecter des dépôts d'épargne selon des cycles réguliers (hebdomadaires, bihebdomadaires, mensuels). Cette activité mobilise l'essentiel des ressources de financement de l'IMF.
2. **Le recouvrement de créances** : le suivi et la récupération des prêts accordés en souffrance, soumis à la classification réglementaire COBAC (classes A à E) et aux taux de provisionnement correspondants.

Ces deux activités génèrent des données opérationnelles volumineuses, hétérogènes et dispersées — enregistrées manuellement sur des carnets, dans des applications mobiles sans connectivité permanente, ou dans des systèmes CBS (Core Banking System) cloisonnés par agence — sans mécanisme d'analyse intégrée ni de pilotage décisionnel en temps réel.

## 2. Problématique

> **Comment concevoir un pipeline de données capable d'intégrer, de transformer et d'analyser de manière fiable et en quasi-temps-réel les données de collectes d'épargne terrain et de recouvrement de créances dans les institutions de microfinance camerounaises, tout en produisant des indicateurs de pilotage conformes aux exigences COBAC et un scoring prédictif multi-critères des risques clients ?**

### 2.1 Défis liés aux collectes d'épargne

- **Déconnexion terrain-agence** : les collectes réalisées par les agents en zone à faible connectivité ne remontent en agence qu'avec un délai de plusieurs heures ou jours, rendant impossible toute analyse en temps réel.
- **Absence de suivi des objectifs** : les responsables ne peuvent pas mesurer le taux de réalisation des objectifs de collecte par agent, par cycle ou par agence en cours de période.
- **Risque de doublons et fraudes** : sans mécanisme de déduplication (identifiant unique mobile, UUID v4), un agent peut re-saisir une collecte déjà enregistrée en mode offline.
- **Granularité inexploitée** : les données par agent, par zone géographique et par canal de collecte ne sont pas agrégées pour benchmarker les performances inter-agences.

### 2.2 Défis liés au recouvrement de créances

- **Calcul PAR manuel et tardif** : le calcul du Portfolio at Risk à 30, 60, 90 et 180 jours est souvent effectué manuellement avec des délais qui rendent les décisions de provisionnement réactives au lieu d'être préventives.
- **Classification COBAC non automatisée** : la classification des créances en classes A à E et les provisions réglementaires associées ne sont pas appliquées automatiquement à chaque snapshot journalier.
- **Absence de priorisation des dossiers** : les gestionnaires de recouvrement ne disposent d'aucun outil de priorisation basé sur la probabilité de défaut à 90 jours.
- **Facteurs externes non intégrés** : l'impact du prix d'un produit générique vendu par le client (maïs, manioc, arachide, etc.) sur les marchés locaux, des conditions météorologiques saisonnières, et des indicateurs macroéconomiques (BEAC, INS) sur la solvabilité client n'est jamais pris en compte.

### 2.3 Questions de recherche

1. Quelle architecture de pipeline de données permet d'ingérer de manière fiable des données de collectes terrain (offline-first, UUID dedup) et des exports CBS, tout en garantissant l'idempotence et la traçabilité ?
2. Comment modéliser un Data Warehouse (schéma en étoile) adapté à la dualité collectes épargne / recouvrement de créances dans un contexte multi-agences et multi-IMF ?
3. Comment concevoir un modèle de scoring prédictif multi-critères (MCRS) intégrant comportements de collecte, probabilité de défaut XGBoost et facteurs externes génériques, avec explicabilité SHAP ?
4. Dans quelle mesure l'automatisation du calcul des indicateurs COBAC (PAR, provisions, classification A-E) améliore-t-elle la conformité réglementaire et la réactivité opérationnelle des EMF ?

## 3. Périmètre d'étude

Ce projet porte **exclusivement** sur :

| Domaine | Ce qui est inclus |
|---|---|
| Collecte d'épargne | Saisie mobile offline-first, déduplication UUID, synchronisation batch, validation, objectifs par cycle/agent |
| Recouvrement | Calcul PAR COBAC automatisé, gestion dossiers, promesses de paiement, scoring MCRS |
| Données externes | Prix de produits génériques (agricoles/commerciaux), météo par zone, indicateurs macro BEAC/INS |
| Dashboard | KPI collecte + PAR COBAC + scores MCRS + benchmarks inter-agences |

**Hors périmètre :** octroi de prêts, gestion du cycle de crédit (instruction, déblocage, remboursement courant), comptabilité générale, gestion RH.

## 4. Cadre réglementaire

Le **Règlement COBAC EMF 01/02 de la CEMAC** définit :
- La **classification des créances en souffrance** : A (courantes), B (30-89 jours → 20% provision), C (90-179 jours → 50%), D (180-359 jours → 80%), E (360 jours et plus → 100%).
- Les **ratios prudentiels** PAR30, PAR90, taux de couverture des créances douteuses.
- Les **obligations de reporting** périodique aux autorités de tutelle COBAC/CEMAC.

Ce pipeline automatise le calcul de ces indicateurs et les rend traçables dans le temps via des snapshots journaliers.
