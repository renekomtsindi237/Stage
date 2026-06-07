# 06 — Contraintes et Hypothèses

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Contraintes réglementaires

### CR-01 — Conformité COBAC
Le système doit implémenter la classification des créances en souffrance telle que définie par le Règlement COBAC EMF 01/02 de la CEMAC :
- Classe A : créances courantes (< 30 jours de retard) — aucune provision.
- Classe B : 30 à 89 jours de retard — provision 20% de l'encours.
- Classe C : 90 à 179 jours de retard — provision 50% de l'encours.
- Classe D : 180 à 359 jours de retard — provision 80% de l'encours.
- Classe E : 360 jours et plus de retard — provision 100% de l'encours (créance irrécouvrable).

### CR-02 — Reporting réglementaire
Les snapshots KPI archivés (`app.kpi_recouvrement_snapshots`) doivent permettre de produire les rapports périodiques exigés par la COBAC sans retraitement.

### CR-03 — Localisation des données
Les données des IMF camerounaises doivent être hébergées sur le territoire national ou dans un centre de données localisé en Afrique centrale (CEMAC), conformément aux dispositions applicables.

---

## 2. Contraintes techniques

### CT-01 — Absence de SIB (Système d'Information Bancaire) propre
Ce projet ne développe pas de CBS propre. Il consomme les exports fichiers produits par les CBS existants des IMF (format CSV/Excel), sans intégration temps réel avec ces systèmes.

### CT-02 — Connectivité terrain limitée
Les agents opèrent dans des zones à couverture réseau dégradée ou absente. L'application mobile doit fonctionner intégralement en mode offline et synchroniser de manière asynchrone. La conception du système ne doit pas dépendre d'une connexion permanente de l'appareil mobile.

### CT-03 — Ressources serveur limitées
Le déploiement cible est un serveur unique ou un petit cluster (2-4 nœuds) avec Docker Compose, sans infrastructure cloud élastique. Les DAGs Airflow doivent être optimisés pour des ressources contraintes.

### CT-04 — Données CBS non structurées
Les exports CBS varient selon les logiciels utilisés par les différentes IMF (FinancialEdge, Mambu, solutions locales). Le pipeline doit gérer la variabilité de format via une couche de mapping configurable dans `raw.journal_ingestions`.

### CT-05 — Prix produits génériques non temps réel
Les prix des produits génériques sur les marchés locaux ne sont pas disponibles en temps réel via une API officielle. Le pipeline doit accepter des sources multiples (relevés terrain manuels, APIs gouvernementales MINCOMMERCE, scraping) avec un score de fiabilité (1 à 5).

---

## 3. Contraintes de temps

### CTp-01 — Délai de mémoire
Le projet doit être finalisé et soutenu en juillet 2026. Les développements doivent être suffisamment avancés pour une démonstration fonctionnelle des modules principaux (pipeline, dashboard, scoring MCRS).

### CTp-02 — Données historiques
Le modèle MCRS nécessite au minimum **12 mois de données historiques** de collectes et de créances pour être entraîné de manière fiable. En absence de données réelles, un générateur de données synthétiques (respectant les distributions du secteur camerounais) est prévu dans le pipeline de développement.

---

## 4. Contraintes de ressources

### CRe-01 — Équipe
Ce projet est développé par un seul étudiant dans le cadre d'un mémoire de fin d'études. Les choix technologiques doivent tenir compte de la maintenabilité par une seule personne.

### CRe-02 — Open source
L'ensemble de la pile technologique doit être open source et sans coût de licence : PostgreSQL, Apache Airflow, dbt Core, XGBoost, SHAP, Spring Boot, Angular, Flutter.

---

## 5. Hypothèses de travail

### H-01 — Structure des exports CBS
Il est supposé que les IMF utilisatrices peuvent produire des exports CSV/Excel réguliers de leur portefeuille de créances, avec au minimum : identifiant du prêt, identifiant du client, montant décaissé, date de décaissement, date de dernière échéance, date de dernier paiement, montant en souffrance.

### H-02 — Profil client informel
Les clients des IMF ciblées exercent des activités économiques informelles et vendent ou produisent des biens pouvant appartenir au catalogue de produits génériques configuré. La corrélation entre le prix d'un produit et la solvabilité du client est une hypothèse centrale du modèle CSI.

### H-03 — Volumes de données
Le système est dimensionné pour :
- 50 000 clients actifs par IMF.
- 10 000 collectes terrain par jour par IMF.
- 50 000 créances en portefeuille par IMF.
- 5 à 20 IMF clientes simultanées sur la plateforme.

### H-04 — Qualité des données
Les données CBS des IMF présentent un taux d'erreur de saisie estimé à 5-10% (doublons, montants aberrants, dates incohérentes). La couche staging dbt doit détecter et flaguer ces anomalies sans les corriger automatiquement (signalement seulement).

### H-05 — Disponibilité des données externes
Les prix des produits génériques sont disponibles avec une granularité hebdomadaire ou mensuelle selon les sources. Les données météo Open-Meteo sont disponibles quotidiennement par zone GPS. Les indicateurs macro BEAC/INS sont disponibles mensuellement.

### H-06 — Représentativité du modèle ML
Le modèle MCRS est entraîné sur les données d'une ou plusieurs IMF camerounaises. Il est supposé que les patterns de défaut sont suffisamment stables pour permettre une validation walk-forward sur 5 folds de 3 mois chacun.
