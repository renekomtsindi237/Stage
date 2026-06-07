# CAHIER DES CHARGES
## Conception d'un Système Intégré de Pipeline de Données, Backend API, Application Web et Application Mobile pour l'Analyse et le Suivi des Collectes Digitales et Recouvrement de Créances dans les Institutions de Microfinance au Cameroun

---

| Champ | Valeur |
|---|---|
| **Document** | Cahier des Charges (CDC) |
| **Version** | 1.0 |
| **Auteur** | Étudiant Ingénieur 4 — Institut Universitaire Saint Jean |
| **Date** | 2026-03-31 |
| **Statut** | Draft — En attente de validation maître de stage |

---

## TABLE DES MATIÈRES

1. [Introduction et Contexte](#1-introduction-et-contexte)
2. [Problématique](#2-problématique)
3. [Objectifs du Projet](#3-objectifs-du-projet)
4. [Périmètre du Système](#4-périmètre-du-système)
5. [Acteurs du Système](#5-acteurs-du-système)
6. [Besoins Fonctionnels](#6-besoins-fonctionnels)
7. [Besoins Non Fonctionnels](#7-besoins-non-fonctionnels)
8. [Contraintes du Projet](#8-contraintes-du-projet)
9. [Glossaire Métier](#9-glossaire-métier)
10. [Annexes](#10-annexes)

---

## 1. Introduction et Contexte

### 1.1 Contexte général

Le secteur de la microfinance au Cameroun joue un rôle central dans l'inclusion financière des populations non bancarisées. Régulées par la **Commission Bancaire de l'Afrique Centrale (COBAC)** sous le cadre réglementaire établi par le Règlement COBAC EMF/2002/01 et ses révisions, les Institutions de Microfinance (IMF) camerounaises opèrent dans un environnement en pleine transformation numérique.

L'essor des services de **mobile money** — portés principalement par **MTN Mobile Money** et **Orange Money** — a modifié profondément les canaux de collecte des remboursements et des épargnes. Les clients peuvent désormais effectuer des paiements à distance, sans se déplacer en agence, via leurs téléphones mobiles.

Parallèlement, le **recouvrement de créances** reste un défi opérationnel majeur pour les IMF : le suivi des impayés, la gestion des portefeuilles à risque et l'efficacité des relances dépendent encore largement de processus manuels, fragmentés et peu fiables.

### 1.2 Contexte technologique

Les IMF camerounaises utilisent généralement :
- Un **Core Banking System (CBS)** local (parfois Mambu, parfois un logiciel propriétaire ou sur Excel)
- Des **relevés d'opérations** fournis par MTN Mobile Money ou Orange Money sous format CSV ou Excel
- Des **registres papier ou Excel** tenus par les agents de terrain pour les collectes en zones reculées
- Des outils de reporting limités (Excel, rapports PDF manuels)

Il n'existe pas de pipeline automatisé permettant d'agréger, nettoyer, transformer et analyser ces données de manière cohérente et en temps quasi-réel.

### 1.3 Contexte de réalisation

Ce projet est développé au sein d'une **startup technologique** dont la vocation est de fournir des solutions logicielles SaaS aux institutions de microfinance camerounaises. La startup conçoit une plateforme complète qu'elle proposera à plusieurs IMF clientes.

Le stagiaire intervient sur la conception et le développement de la plateforme complète, incluant le pipeline de données, le backend API, l'application web de gestion et l'application mobile pour les agents terrain.

### 1.4 Présentation du projet

Ce projet vise à concevoir et implémenter un **système intégré** composé de quatre couches complémentaires :

| Couche | Technologie | Rôle |
|---|---|---|
| **Pipeline de données** | Python · Airflow · dbt · PostgreSQL | Ingestion, transformation, calcul KPIs, alertes |
| **Backend API** | Spring Boot (Java) | API REST sécurisée (JWT), point d'entrée unique pour web et mobile |
| **Application Web** | Angular | Interface de gestion pour les responsables, analystes et directeurs des IMF |
| **Application Mobile** | Flutter (Dart) | Saisie terrain pour les agents, consultation KPIs, réception des alertes push |

Les quatre couches travaillent ensemble : le pipeline alimente l'entrepôt de données PostgreSQL, que le backend Spring Boot expose via des APIs REST consommées par Angular (web) et Flutter (mobile).

---

## 2. Problématique

### 2.1 Problème central

> **Comment automatiser la collecte, le traitement et l'analyse des données de remboursement et de collectes digitales dans une IMF camerounaise, afin de réduire les délais de détection des impayés et d'améliorer le taux de recouvrement ?**

### 2.2 Problèmes opérationnels identifiés

| # | Problème | Impact |
|---|---|---|
| P01 | Données de collectes dispersées (CBS, Excel, mobile money) | Vision fragmentée, calculs manuels erronés |
| P02 | Absence d'indicateurs PAR automatisés | Détection tardive du portefeuille à risque |
| P03 | Reporting manuel hebdomadaire chronophage | Retard dans la prise de décision |
| P04 | Aucune alerte automatique sur impayés | Relances tardives, pertes financières |
| P05 | Doublons et erreurs dans les transactions mobile money | Mauvaise comptabilité des collectes |
| P06 | Pas de traçabilité des actions de recouvrement | Impossibilité d'évaluer l'efficacité des agents |

---

## 3. Objectifs du Projet

### 3.1 Objectif général

Concevoir, développer et déployer un pipeline de données robuste permettant l'analyse automatisée et le suivi en temps différé des collectes digitales et du recouvrement de créances dans une institution de microfinance camerounaise.

### 3.2 Objectifs spécifiques

| Code | Objectif Spécifique |
|---|---|
| OS01 | Centraliser les données issues du CBS, des plateformes mobile money (MTN, Orange) et des fichiers agents terrain dans un entrepôt de données unique |
| OS02 | Automatiser le calcul quotidien des indicateurs PAR30 et PAR90 (Portefeuille à Risque) |
| OS03 | Mettre en place un système d'alertes automatiques pour les créances dépassant les seuils définis |
| OS04 | Développer des tableaux de bord interactifs pour le suivi des collectes digitales (volume, canal, zone, agent) |
| OS05 | Développer des tableaux de bord pour le recouvrement (PAR, taux de remboursement, relances) |
| OS06 | Documenter complètement l'architecture technique, les flux de données et les algorithmes mis en œuvre |
| OS07 | Garantir la reproductibilité et la maintenabilité du pipeline via la containerisation (Docker) |

---

## 4. Périmètre du Système

### 4.1 Ce qui est INCLUS dans le périmètre

- Ingestion automatisée des données de transactions mobile money (MTN, Orange)
- Ingestion des données de prêts et remboursements depuis le CBS (via export CSV/API)
- Ingestion des collectes terrain (fichiers Excel/CSV fournis par les agents)
- Nettoyage, déduplication et transformation des données (ETL)
- Construction d'un entrepôt de données en schéma étoile
- Calcul des KPIs : PAR30, PAR90, taux de recouvrement, taux de défaut, volume de collecte
- Système d'alertes sur impayés dépassant les seuils
- Tableaux de bord analytiques (3 dashboards minimum)
- Orchestration automatique des traitements (planification quotidienne)
- Documentation technique complète

### 4.2 Ce qui est EXCLU du périmètre

- Intégration en temps réel avec les API mobile money (batch uniquement, latence ~24h)
- Développement d'une application mobile pour les agents de terrain
- Modification du Core Banking System existant
- Mise en production sur infrastructure cloud publique (déploiement local uniquement)
- Gestion des accès utilisateurs avancée (SSO, LDAP)
- Module de comptabilité ou rapports réglementaires COBAC formels

---

## 5. Acteurs du Système

### 5.1 Acteurs principaux (utilisateurs directs)

| Acteur | Rôle | Canal d'accès |
|---|---|---|
| **Directeur Général** | Pilotage stratégique de l'IMF | Application Web (Angular) + Application Mobile (Flutter) |
| **Responsable Recouvrement** | Suivi et gestion des impayés | Application Web (Angular) |
| **Analyste Data / Contrôleur de gestion** | Production des rapports et analyses | Application Web (Angular) + Superset |
| **Directeur des Systèmes d'Information (DSI)** | Administration technique | Interface Airflow + Application Web (admin) |
| **Agent de Terrain** | Collecte des remboursements en zone | Application Mobile Flutter (saisie + notifications) |

### 5.2 Acteurs secondaires (systèmes externes)

| Acteur | Type | Rôle |
|---|---|---|
| **Core Banking System (CBS)** | Système source | Export des données de prêts et remboursements |
| **MTN Mobile Money Gateway** | Système source | Relevés de transactions (CSV quotidien) |
| **Orange Money Gateway** | Système source | Relevés de transactions (CSV quotidien) |
| **Serveur de messagerie** | Système cible | Envoi des alertes email sur impayés |

---

## 6. Besoins Fonctionnels

### 6.1 Module Ingestion des Données

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF01 | Le système doit pouvoir ingérer des fichiers CSV/Excel de transactions mobile money (MTN, Orange) | HAUTE |
| BF02 | Le système doit pouvoir se connecter au CBS pour extraire les données de prêts et remboursements via export fichier | HAUTE |
| BF03 | Le système doit détecter et rejeter les transactions en doublon (même référence, même montant, même date) | HAUTE |
| BF04 | Le système doit journaliser chaque ingestion (source, volume, erreurs, timestamp) | MOYENNE |
| BF05 | Le système doit notifier le DSI en cas d'échec d'ingestion | MOYENNE |
| BF06 | Le système doit permettre la réingestion manuelle d'un fichier source en cas d'erreur | BASSE |

### 6.2 Module Transformation & Calcul des KPIs

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF07 | Le système doit calculer quotidiennement le PAR30 (créances échues depuis plus de 30 jours) | HAUTE |
| BF08 | Le système doit calculer quotidiennement le PAR90 (créances échues depuis plus de 90 jours) | HAUTE |
| BF09 | Le système doit calculer le taux de recouvrement par agent, par zone géographique et par produit de crédit | HAUTE |
| BF10 | Le système doit agréger le volume de collectes digitales par canal (MTN, Orange), par jour et par zone | HAUTE |
| BF11 | Le système doit calculer le taux de défaut (write-off ratio) mensuel | MOYENNE |
| BF12 | Le système doit calculer un score de risque simple par client (basé sur l'historique de remboursement) | MOYENNE |

### 6.3 Module Alertes

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF13 | Le système doit générer une alerte automatique pour toute créance dépassant 30 jours d'impayé | HAUTE |
| BF14 | Le système doit envoyer les alertes par email au responsable recouvrement | HAUTE |
| BF15 | Le système doit permettre de configurer le seuil d'alerte (30, 60, 90 jours) | MOYENNE |
| BF16 | Le système doit conserver un historique des alertes générées | MOYENNE |

### 6.4 Module Reporting & Tableaux de Bord

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF17 | Le système doit proposer un dashboard "Collectes Digitales" (volume, canal, zone, agent, tendance) | HAUTE |
| BF18 | Le système doit proposer un dashboard "Recouvrement" (PAR30/90, taux, alertes actives, top débiteurs) | HAUTE |
| BF19 | Le système doit proposer un dashboard "Exécutif" (KPIs synthèse sur 1 page) | HAUTE |
| BF20 | Les dashboards doivent permettre le filtrage par période, zone, agent et produit | MOYENNE |
| BF21 | Le système doit permettre l'export des données en CSV depuis les dashboards | BASSE |

### 6.5 Module Application Mobile (Flutter)

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF26 | L'agent de terrain doit pouvoir saisir une collecte depuis l'application mobile (client, montant, mode, référence prêt) | HAUTE |
| BF27 | L'agent doit pouvoir consulter la liste de ses clients et créances assignées | HAUTE |
| BF28 | L'application mobile doit fonctionner en mode hors-ligne et synchroniser les saisies dès reconnexion réseau | HAUTE |
| BF29 | Le directeur doit pouvoir consulter les KPIs clés (PAR30, PAR90, volume collectes) depuis l'application mobile | HAUTE |
| BF30 | L'application mobile doit recevoir des notifications push (Firebase FCM) lors d'une nouvelle alerte impayé | HAUTE |
| BF31 | L'agent doit pouvoir s'authentifier avec un identifiant / mot de passe depuis l'application mobile | HAUTE |
| BF32 | L'application mobile doit afficher l'historique des collectes effectuées par l'agent connecté | MOYENNE |

### 6.6 Module Backend API (Spring Boot)

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF33 | Le backend doit exposer une API REST sécurisée (JWT Bearer Token) consommée par Angular et Flutter | HAUTE |
| BF34 | Le backend doit gérer l'authentification et les rôles (DIRECTEUR, RESPONSABLE_RECOUVREMENT, ANALYSTE, DSI, AGENT) | HAUTE |
| BF35 | Le backend doit exposer des endpoints de lecture des KPIs (PAR, collectes, recouvrement) depuis le Data Warehouse | HAUTE |
| BF36 | Le backend doit exposer un endpoint de soumission de collecte terrain (depuis l'app mobile) | HAUTE |
| BF37 | Le backend doit exposer un endpoint de gestion des alertes (liste, clôture, escalade) | HAUTE |
| BF38 | Le backend doit envoyer des notifications push via Firebase Cloud Messaging (FCM) | HAUTE |
| BF39 | Le backend doit exposer une documentation API auto-générée (Swagger / OpenAPI 3.0) | MOYENNE |
| BF40 | Le backend doit journaliser tous les appels API (timestamp, endpoint, utilisateur, code réponse) | MOYENNE |

### 6.7 Module Application Web (Angular)

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF41 | L'application web doit proposer un module de connexion sécurisé (JWT, refresh token) | HAUTE |
| BF42 | L'application web doit afficher le dashboard Collectes Digitales (graphiques, filtres, tendances) | HAUTE |
| BF43 | L'application web doit afficher le dashboard Recouvrement (PAR30/90, alertes actives, liste impayés) | HAUTE |
| BF44 | L'application web doit afficher le dashboard Exécutif (KPIs synthèse sur une page) | HAUTE |
| BF45 | L'application web doit permettre la gestion des utilisateurs (création, rôles, désactivation) | MOYENNE |
| BF46 | L'application web doit permettre l'export des données en CSV depuis chaque dashboard | MOYENNE |
| BF47 | L'application web doit être responsive (mobile-friendly pour consultation rapide) | BASSE |

### 6.8 Module Administration du Pipeline

| Code | Besoin Fonctionnel | Priorité |
|---|---|---|
| BF22 | Le DSI doit pouvoir lancer manuellement un DAG (traitement) depuis l'interface Airflow | HAUTE |
| BF23 | Le DSI doit pouvoir surveiller l'état des traitements (succès, échec, en cours) | HAUTE |
| BF24 | Le DSI doit pouvoir rejouer un traitement échoué sur une date passée (backfill) | MOYENNE |
| BF25 | Le système doit journaliser toutes les exécutions de traitements | MOYENNE |

---

## 7. Besoins Non Fonctionnels

### 7.1 Performance

| Code | Exigence | Critère de mesure |
|---|---|---|
| BNF01 | Le pipeline de traitement quotidien doit s'exécuter en moins de 2 heures | Mesure de la durée d'exécution du DAG principal |
| BNF02 | Les dashboards doivent se charger en moins de 5 secondes | Mesure du temps de chargement avec 10 000 transactions |
| BNF03 | Le système doit supporter jusqu'à 50 000 transactions par mois | Tests de charge avec données simulées |

### 7.2 Sécurité

| Code | Exigence |
|---|---|
| BNF04 | Les connexions à la base de données doivent être authentifiées (utilisateur/mot de passe) |
| BNF05 | Les mots de passe et clés API doivent être stockés dans des variables d'environnement (jamais en clair dans le code) |
| BNF06 | L'accès aux dashboards doit être protégé par authentification (login/mot de passe) |
| BNF07 | Les données client (noms, numéros de téléphone) doivent être masquées dans les logs |

### 7.3 Disponibilité

| Code | Exigence |
|---|---|
| BNF08 | Le pipeline doit être opérationnel 6 jours sur 7 (hors dimanche) |
| BNF09 | En cas de panne, le système doit être redémarrable sans perte de données via Docker |

### 7.4 Maintenabilité

| Code | Exigence |
|---|---|
| BNF10 | Tout le code source doit être versionné sur Git avec des messages de commit clairs |
| BNF11 | La documentation doit permettre à un nouveau développeur de déployer le système sans assistance en moins d'une journée |
| BNF12 | Les modèles de transformation (dbt) doivent avoir un taux de couverture de tests supérieur à 90% |

### 7.5 Scalabilité

| Code | Exigence |
|---|---|
| BNF13 | L'architecture doit permettre l'ajout de nouvelles sources de données sans refonte majeure |
| BNF14 | L'entrepôt de données doit supporter l'ajout de nouvelles dimensions et métriques sans migration destructive |

---

## 8. Contraintes du Projet

### 8.1 Contraintes techniques

| Contrainte | Description |
|---|---|
| CT01 | Le système doit fonctionner sur un serveur local (pas de dépendance obligatoire au cloud) |
| CT02 | La stack technologique doit être entièrement open source (budget limité) |
| CT03 | Python est le langage principal du pipeline |
| CT04 | Docker est obligatoire pour la containerisation (reproductibilité) |
| CT05 | Les données réelles étant sensibles, un générateur de données simulées sera utilisé pour les phases de développement et test |

### 8.2 Contraintes réglementaires

| Contrainte | Description |
|---|---|
| CR01 | Les données traitées sont soumises à la réglementation COBAC sur la protection des données clients des EMF |
| CR02 | Les indicateurs PAR doivent respecter la définition standard COBAC/CGAP (encours de prêts avec au moins un remboursement en retard de X jours / encours total) |
| CR03 | La conservation des données de transactions doit couvrir au minimum 5 ans (archivage) |

### 8.3 Contraintes de délai

| Contrainte | Description |
|---|---|
| CD01 | Phase documentation : 2 mois (Semaines 1–8) |
| CD02 | Phase implémentation : 1 mois (Semaines 9–12) |
| CD03 | Phase tests et finalisation : 4 semaines maximum (Semaines 13–16) |
| CD04 | Remise du rapport de stage : fin du mois 4 |

### 8.4 Contraintes humaines

| Contrainte | Description |
|---|---|
| CH01 | Le projet est réalisé par un seul développeur (stagiaire) |
| CH02 | L'accès aux données réelles de production nécessite une validation préalable du maître de stage |
| CH03 | Les formations des utilisateurs finaux sur les dashboards ne font pas partie du périmètre du stage |

---

## 9. Glossaire Métier

| Terme | Définition |
|---|---|
| **IMF** | Institution de Microfinance — établissement financier accordant des microcrédits aux populations à faibles revenus |
| **CBS** | Core Banking System — logiciel central de gestion des opérations bancaires (prêts, épargne, clients) |
| **COBAC** | Commission Bancaire de l'Afrique Centrale — autorité de régulation des établissements de crédit en zone CEMAC |
| **CEMAC** | Communauté Économique et Monétaire de l'Afrique Centrale |
| **Mobile Money** | Service de paiement et transfert d'argent via téléphone mobile (MTN Mobile Money, Orange Money) |
| **Collecte Digitale** | Remboursement ou paiement effectué via un canal numérique (mobile money, virement) — par opposition à la collecte en espèces |
| **Créance** | Somme d'argent due par un client (débiteur) à l'IMF suite à un prêt accordé |
| **Recouvrement** | Processus visant à récupérer les sommes dues par les clients en situation d'impayé |
| **PAR (Portfolio at Risk)** | Indicateur mesurant la part du portefeuille de prêts présentant un risque de non-remboursement. Formule : Encours des prêts avec retard > N jours / Encours total des prêts |
| **PAR30** | Portefeuille à Risque à 30 jours — prêts avec au moins un remboursement en retard de plus de 30 jours |
| **PAR90** | Portefeuille à Risque à 90 jours — indicateur de risque élevé, souvent précurseur d'une perte définitive |
| **Taux de recouvrement** | Ratio montants effectivement recouvrés / montants dus sur une période donnée |
| **Taux de défaut** | Proportion de prêts considérés comme irrécouvrables (write-off) sur l'encours total |
| **Impayé** | Remboursement non effectué à la date d'échéance prévue |
| **Encours** | Montant total des prêts en cours (capital restant dû) à une date donnée |
| **Write-off** | Passage en perte d'une créance jugée irrécouvrable (comptabilisation définitive de la perte) |
| **ETL** | Extract, Transform, Load — processus d'extraction, transformation et chargement des données |
| **ELT** | Extract, Load, Transform — variante où les données sont d'abord chargées puis transformées |
| **DAG** | Directed Acyclic Graph — représentation d'un pipeline de traitement Airflow sous forme de graphe orienté acyclique |
| **Data Warehouse (DW)** | Entrepôt de données — base de données structurée pour l'analyse, distincte des bases opérationnelles |
| **Schéma en étoile** | Modèle de données analytique avec une table de faits centrale et des tables de dimensions |
| **KPI** | Key Performance Indicator — indicateur clé de performance |
| **dbt** | Data Build Tool — outil de transformation de données SQL avec gestion de la documentation et des tests |
| **Agent de terrain** | Employé de l'IMF chargé de collecter les remboursements directement auprès des clients |
| **Scoring de risque** | Calcul d'un score numérique évaluant la probabilité de défaut d'un client |

---

## 10. Annexes

### 10.1 Cadre réglementaire de référence

- Règlement COBAC EMF/2002/01 relatif aux conditions d'exercice et de contrôle des activités de microfinance en zone CEMAC
- Instructions COBAC sur les ratios prudentiels des EMF (PAR, taux de créances douteuses)
- Loi n°2003/004 du 21 avril 2003 portant définition des établissements de crédit au Cameroun

### 10.2 Standards de référence pour les indicateurs

- **CGAP** (Consultative Group to Assist the Poor) — définitions standard des indicateurs de microfinance
- **MIX Market** — référentiel de reporting des performances des IMF à l'échelle mondiale

### 10.3 Validation et signatures

| Rôle | Nom | Date | Signature |
|---|---|---|---|
| Auteur (Stagiaire) | | | |
| Maître de stage | | | |
| Tuteur pédagogique | | | |
