# 01 — Analyse de l'Existant

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Le secteur de la microfinance au Cameroun

### 1.1 Panorama du secteur

Le Cameroun compte, selon les données COBAC 2023, **427 Établissements de Microfinance (EMF)** agréés, dont :
- **Catégorie 1** : EMF collectant l'épargne du public et accordant des prêts (modèle dominant).
- **Catégorie 2** : EMF accordant des prêts uniquement.
- **Catégorie 3** : EMF sous forme coopérative (SACCOs, mutuelles).

Le total des dépôts collectés dépasse **500 milliards FCFA**, avec un portefeuille de créances de **650 milliards FCFA**. Le PAR90 moyen du secteur oscille entre **15% et 25%** selon les exercices, nettement supérieur aux seuils prudentiels recommandés par la COBAC (< 5% pour les EMF de catégorie 1).

### 1.2 Clientèle cible

La clientèle des EMF camerounaises est principalement composée de :
- **Agriculteurs** des zones rurales (Centre, Adamaoua, Sud, Est) cultivant des produits vivriers (maïs, manioc, plantain) et des cultures de rente (cacao, café, coton).
- **Commerçants informels** des marchés urbains et périurbains (Yaoundé, Douala, Bafoussam, Garoua).
- **Artisans** (menuisiers, maçons, tailleurs, mécaniciens).
- **Éleveurs** (poulet, porc, petits ruminants).

Cette clientèle exerce des activités plurielles et saisonnières, avec des revenus fortement corrélés aux prix des produits sur les marchés locaux et aux conditions climatiques.

---

## 2. Processus existants — État actuel (AS-IS)

### 2.1 Collecte d'épargne terrain — Processus AS-IS

**Acteurs impliqués :** Agent de collecte, Caissier agence, Responsable agence.

**Flux actuel :**
1. L'agent se déplace chez le client avec un **carnet papier** ou une application mobile basique.
2. Il enregistre la collecte sur le carnet ou l'application.
3. En fin de journée, il rentre à l'agence et saisit (ou re-saisit) les collectes dans le système CBS — si une application mobile est utilisée, la synchronisation est souvent manuelle.
4. Le caissier enregistre les montants physiques reçus.
5. Le responsable de l'agence consolidate les totaux en fin de semaine/mois.

**Dysfonctionnements identifiés :**
- **Double saisie** : carnet puis CBS → source d'erreurs et de doublons.
- **Délai de remontée** : les données ne sont disponibles au niveau du siège qu'avec 1 à 5 jours de délai.
- **Absence de suivi en temps réel** : le responsable ne sait pas en cours de journée si les objectifs seront atteints.
- **Pas de détection de fraude** : il n'existe pas de mécanisme automatique pour détecter si un agent détourne des fonds collectés.
- **Perte de données** : en cas de perte du carnet ou de panne de l'appareil mobile, les collectes non synchronisées sont perdues.

### 2.2 Recouvrement de créances — Processus AS-IS

**Acteurs impliqués :** Gestionnaire de recouvrement, Directeur agence, Juriste.

**Flux actuel :**
1. Le CBS produit un état des impayés à une fréquence variable (quotidienne ou hebdomadaire).
2. Le gestionnaire trie manuellement les créances par ancienneté.
3. Il classe les créances en catégories (souvent différentes des classes COBAC A-E) selon son jugement.
4. Le calcul des provisions est effectué manuellement par le comptable avec des tableurs Excel.
5. Les actions de recouvrement (appels, visites, courriers) sont enregistrées dans Excel ou sur papier.
6. Le rapport COBAC est produit manuellement en fin de trimestre ou d'exercice.

**Dysfonctionnements identifiés :**
- **Classification COBAC non automatisée** : le calcul des provisions classes B/C/D/E est souvent inexact ou tardif.
- **Absence de priorisation objective** : les dossiers sont traités selon l'intuition du gestionnaire, sans scoring.
- **Pas d'intégration des facteurs de risque externes** : un client dont la récolte a été détruite par la sécheresse est traité de la même façon qu'un mauvais payeur délibéré.
- **Silos agences** : les données de recouvrement ne sont pas consolidées au niveau du siège en temps réel.
- **Traçabilité insuffisante** : les actions de recouvrement et les promesses de paiement ne sont pas systématiquement enregistrées.

---

## 3. Systèmes d'information existants

### 3.1 Core Banking Systems utilisés au Cameroun

Les EMF camerounaises utilisent une variété de CBS :
- **FinancialEdge** (Craft Silicon) : solution répandue parmi les grandes EMF.
- **Mambu** (cloud) : adopté par certaines structures plus modernes.
- **Solutions locales** : développements maison, souvent peu documentés.
- **Tableurs Excel** : encore utilisés comme système principal dans de nombreuses petites EMF.

**Caractéristiques communes :**
- Exports de données disponibles en CSV ou Excel (pas de WebService standardisé).
- Pas d'API REST native accessible sans coût de licence élevé.
- Schémas de données hétérogènes selon les versions et les EMF.

### 3.2 Outils de reporting existants

- Rapports COBAC produits manuellement avec Excel.
- Tableaux de bord basiques dans les CBS (agrégats mensuels, peu personnalisables).
- Aucun outil d'analyse prédictive ou de scoring de risque.

---

## 4. Synthèse des lacunes

| Dimension | Situation actuelle | Besoin identifié |
|---|---|---|
| Collecte terrain | Papier/app sans sync temps réel | Offline-first avec sync batch automatique |
| Déduplication | Inexistante | UUID v4 + déduplication serveur |
| Suivi objectifs | Consolidation manuelle hebdomadaire | KPI temps réel par agent/agence |
| Classification COBAC | Manuelle, souvent incorrecte | Calcul automatique quotidien snapshot |
| Scoring risque | Inexistant | MCRS composite (CRS+RPS+CSI) |
| Facteurs externes | Non pris en compte | Prix produits génériques + météo + macro |
| Benchmarks | Inexistants | Z-scores inter-agences calculés par pipeline |
| Traçabilité | Partielle (Excel) | Journal d'ingestion + historique complet |
| Reporting COBAC | Trimestriel manuel | Snapshots quotidiens automatisés |

---

## 5. Opportunités et points d'appui

- Pénétration croissante des smartphones Android en zone rurale → viabilité du mode offline mobile.
- Initiatives COBAC/CEMAC de digitalisation du reporting → alignement réglementaire favorable.
- Disponibilité croissante des données de prix agricoles via MINCOMMERCE et APIs.
- Adoption progressive d'Open-Meteo pour les données météo Afrique sub-saharienne.
- Communauté locale de développeurs Java/Spring Boot et Angular au Cameroun.
