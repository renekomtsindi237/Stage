# CAHIER D'ANALYSE
## Pipeline de Données — Collectes Digitales & Recouvrement de Créances — IMF Cameroun

---

| Champ | Valeur |
|---|---|
| **Document** | Cahier d'Analyse (CA) |
| **Version** | 1.0 |
| **Auteur** | Étudiant Ingénieur 4 — Institut Universitaire Saint Jean |
| **Date** | 2026-03-31 |
| **Statut** | Draft |

---

## TABLE DES MATIÈRES

1. [Étude de l'existant](#1-étude-de-lexistant)
2. [Analyse des flux de données](#2-analyse-des-flux-de-données)
3. [Modélisation des processus métier](#3-modélisation-des-processus-métier)
4. [Diagrammes Use Case](#4-diagrammes-use-case)
5. [Matrice de traçabilité](#5-matrice-de-traçabilité)

---

## 1. Étude de l'existant

### 1.1 Processus actuels (avant le projet)

#### Collecte des données de remboursement

Le processus actuel de collecte et de suivi des remboursements dans une IMF camerounaise typique fonctionne comme suit :

1. **Les agents de terrain** collectent les remboursements en espèces ou via mobile money (MTN/Orange) lors des visites aux groupes solidaires ou aux clients individuels.
2. **Les collectes en espèces** sont saisies manuellement dans le CBS par les caissiers en agence, parfois avec un délai de 1 à 3 jours.
3. **Les collectes via mobile money** sont effectuées par les clients directement sur leur téléphone. L'IMF reçoit un relevé CSV/Excel quotidien ou hebdomadaire de MTN/Orange Money.
4. **Le rapprochement** entre le relevé mobile money et le CBS est réalisé manuellement par un comptable, en comparant les références de transaction une par une dans Excel.
5. **Les rapports PAR** (PAR30, PAR90) sont calculés manuellement par l'analyste de gestion une fois par semaine ou par mois, en exportant les données du CBS vers Excel et en appliquant des formules.

#### Recouvrement de créances

1. Un responsable recouvrement consulte le CBS pour identifier les impayés.
2. Il produit manuellement une liste d'impayés par agence dans Excel.
3. Il appelle ou envoie des SMS manuellement aux agents de terrain pour déclencher les relances.
4. Les résultats des relances sont notés dans un registre papier ou un fichier Excel partagé.
5. Les indicateurs de recouvrement sont produits mensuellement dans un rapport Word/PDF.

### 1.2 Points de douleur identifiés

| # | Point de douleur | Cause | Impact métier |
|---|---|---|---|
| PD01 | Rapprochement mobile money / CBS manuel | Absence d'automatisation | 2–4 heures/jour de travail comptable |
| PD02 | Calcul PAR hebdomadaire au mieux | Processus manuel long | Détection tardive des impayés |
| PD03 | Doublons dans les relevés mobile money | Pas de déduplication automatique | Sur-enregistrement de collectes |
| PD04 | Fichiers Excel non versionnés et partagés | Absence de gouvernance des données | Erreurs de calcul, écrasements |
| PD05 | Pas de tableau de bord centralisé | Outils dispersés | Réunions de pilotage basées sur des données obsolètes |
| PD06 | Historique des relances non structuré | Registre papier | Impossibilité de mesurer l'efficacité des agents |
| PD07 | Pas d'alerte automatique | Processus réactif | Passage en PAR90 non détecté à temps |

### 1.3 Cartographie des outils existants

| Outil | Usage actuel | Limites |
|---|---|---|
| Core Banking System (CBS) | Gestion des prêts, remboursements, clients | Export limité, pas d'API REST native |
| Microsoft Excel | Rapports, calcul PAR, registre de relances | Non scalable, erreur humaine, pas de traçabilité |
| Email | Transmission des relevés mobile money | Pas d'automatisation de l'ingestion |
| MTN/Orange Money Portal | Consultation des transactions | Pas d'API en temps réel pour les PME |
| WhatsApp | Communication agent ↔ responsable | Hors système, non traçable |

---

## 2. Analyse des flux de données

### 2.1 Sources de données

| Source | Format | Fréquence | Volume estimé | Mode d'accès |
|---|---|---|---|---|
| Core Banking System | CSV export ou XLSX | Quotidien | 200–500 lignes/jour | Export manuel ou scheduled |
| MTN Mobile Money | CSV | Quotidien | 100–300 lignes/jour | Email ou portail web |
| Orange Money | CSV | Quotidien | 50–150 lignes/jour | Email ou portail web |
| Agents de terrain | Excel | Hebdomadaire | 50–200 lignes/semaine | Dépôt sur dossier partagé |

### 2.2 Description des champs par source

#### Source : Core Banking System (CBS)
```
id_pret, id_client, nom_client, telephone_client, zone, produit,
montant_pret, date_decaissement, date_echeance, montant_echeance,
montant_rembourse_cumul, statut_pret, agent_id, agence_id
```

#### Source : MTN Mobile Money (relevé)
```
reference_transaction, date_transaction, heure_transaction,
numero_expediteur, montant, frais, statut_transaction,
description, reference_externe
```

#### Source : Orange Money (relevé)
```
id_operation, date_valeur, type_operation, montant_credit,
montant_debit, solde, libelle, numero_client
```

#### Source : Agents terrain (Excel)
```
date_collecte, nom_client, telephone_client, montant_collecte,
mode_paiement, agent_id, zone_id, reference_pret, observation
```

### 2.3 Flux de données cible (pipeline)

```
[CBS Export]          ──────────────────────────────┐
[MTN Mobile Money]    ──────────────────────────────┤
[Orange Money]        ──── Ingestion (Python/Airflow)──► [Schéma RAW]
[Agents Terrain]      ──────────────────────────────┘
                                                        │
                                              [Staging + Déduplication]
                                                        │
                                              [Data Warehouse (Étoile)]
                                                        │
                              ┌─────────────────────────┼──────────────────┐
                     [KPIs + Alertes]         [Dashboards]          [Export CSV]
                     (Airflow DAG)             (Superset)
```

---

## 3. Modélisation des processus métier

> Les diagrammes ci-dessous sont décrits en notation PlantUML. Ils sont à générer avec un outil UML (PlantUML, Draw.io, StarUML, Lucidchart).

### 3.1 Diagramme d'activité — Processus de collecte digitale (AS-IS vs TO-BE)

#### AS-IS (situation actuelle)

```plantuml
@startuml activite_collecte_ASIS
title Processus de collecte digitale — Situation actuelle (AS-IS)

start
:Client effectue paiement\nvia MTN/Orange Money;
:MTN/Orange génère relevé CSV\n(quotidien ou hebdomadaire);
:Comptable reçoit relevé\npar email;
:Comptable ouvre Excel\net CBS;
fork
  :Recherche manuelle\nde la référence pret dans CBS;
fork again
  :Lecture ligne par ligne\ndu relevé mobile money;
end fork
:Rapprochement manuel\n(copier-coller, VLOOKUP);
if (Transaction identifiée ?) then (Oui)
  :Saisie manuelle\ndans CBS;
  :Mise à jour\ndu registre Excel;
else (Non)
  :Signalement au\nresponsable agence;
  :Suspension en attente\nde clarification;
endif
:Rapport hebdomadaire\nmanuel;
stop
@enduml
```

#### TO-BE (situation cible avec le pipeline)

```plantuml
@startuml activite_collecte_TOBE
title Processus de collecte digitale — Situation cible (TO-BE)

start
:Client effectue paiement\nvia MTN/Orange Money;
:MTN/Orange génère relevé CSV\n(quotidien automatique);
:Airflow DAG déclenché\nà 06h00 chaque matin;
:Téléchargement automatique\ndes relevés CSV;
:Chargement dans schéma RAW\n(PostgreSQL);
:Déduplication automatique\n(hashing + fenêtre temporelle);
:Transformation dbt\n(staging → data warehouse);
:Calcul KPIs collecte\n(volume, canal, zone, agent);
if (Anomalie détectée ?) then (Oui)
  :Alerte email\nenvoyée au DSI;
  :Enregistrement\ndans log erreurs;
else (Non)
  :Dashboards mis à jour\nautomatiquement;
endif
:Données disponibles\npour analyse à 08h00;
stop
@enduml
```

---

### 3.2 Diagramme d'activité — Processus de recouvrement

#### AS-IS

```plantuml
@startuml activite_recouvrement_ASIS
title Processus de recouvrement — Situation actuelle (AS-IS)

start
:Responsable recouvrement\nconsulte CBS (hebdomadaire);
:Export manuel des impayés\nvers Excel;
:Tri et identification\ndes clients PAR30+;
:Appel téléphonique\nmanuel aux agents;
:Agent contacte\nle client;
if (Paiement obtenu ?) then (Oui)
  :Agent collecte\nle paiement;
  :Caissier saisit\ndans CBS (délai 1-3j);
  :Mise à jour manuelle\nregistre Excel;
else (Non)
  :Note dans\nregistre papier;
  :Réunion mensuelle\npour décision de write-off;
endif
:Rapport mensuel\nmanuel (Word/Excel);
stop
@enduml
```

#### TO-BE

```plantuml
@startuml activite_recouvrement_TOBE
title Processus de recouvrement — Situation cible (TO-BE)

start
:Airflow DAG exécute\ncalcul PAR quotidien à 08h00;
:PAR30 et PAR90\ncalculés automatiquement;
if (Nouvelles créances PAR30 ?) then (Oui)
  :Alerte email générée\nautomatiquement;
  :Créance enregistrée\n dans table alertes_actives;
  :Dashboard recouvrement\nmis à jour;
  :Responsable recouvrement\nconsulte dashboard;
  :Déclenchement relance\n(appel/SMS agent terrain);
  :Agent contacte client;
  if (Paiement obtenu ?) then (Oui)
    :Transaction mobile money\nou espèces collectée;
    :Pipeline ingère\lla transaction J+1;
    :Alerte clôturée\nautomatiquement;
  else (Non)
    if (Retard > 90 jours ?) then (Oui)
      :Escalade au directeur;
      :Décision write-off;
    else (Non)
      :Alerte maintenue\nactive;
    endif
  endif
else (Non)
  :Aucune action requise;
endif
:Tableaux de bord disponibles\nen temps différé (J-1);
stop
@enduml
```

---

### 3.3 Diagramme d'activité — Pipeline d'ingestion quotidien

```plantuml
@startuml activite_pipeline_ingestion
title Pipeline d'ingestion quotidien — Vue d'ensemble

start
:Déclenchement automatique\nAirflow à 06h00;
fork
  :Téléchargement relevé\nMTN Mobile Money;
fork again
  :Téléchargement relevé\nOrange Money;
fork again
  :Export CBS\n(prêts + remboursements);
fork again
  :Détection nouveaux fichiers\nagents terrain;
end fork

:Chargement dans\nschéma RAW (PostgreSQL);

:Déduplication\n(hashing SHA-256 des transactions);

:Validation des données\n(types, montants positifs, dates);

if (Erreurs critiques ?) then (Oui)
  :Rejet des lignes erronées;
  :Enregistrement dans\ntable error_log;
  :Notification DSI;
else (Non)
endif

:Transformation dbt\n(raw → staging);
:Transformation dbt\n(staging → data warehouse);
:Calcul des KPIs\n(PAR30, PAR90, taux recouvrement);
:Détection alertes\nimpayés;
:Rafraîchissement\ncaches Superset;
:Notification succès\npar email;
stop
@enduml
```

---

## 4. Diagrammes Use Case

### 4.1 Use Case Global (vue d'ensemble)

```plantuml
@startuml usecase_global
title Système Pipeline IMF — Vue d'ensemble des Use Cases

left to right direction

actor "Directeur Général" as DG
actor "Responsable Recouvrement" as RR
actor "Analyste Data" as AD
actor "DSI / Admin" as DSI
actor "Core Banking System" as CBS <<système>>
actor "MTN Mobile Money" as MTN <<système>>
actor "Orange Money" as OM <<système>>
actor "Agent Terrain" as AT

rectangle "Système Pipeline IMF" {
  usecase "UC01 Gérer les collectes digitales" as UC01
  usecase "UC02 Suivre le portefeuille de créances" as UC02
  usecase "UC03 Gérer le recouvrement et les relances" as UC03
  usecase "UC04 Consulter les tableaux de bord" as UC04
  usecase "UC05 Administrer le pipeline" as UC05
}

DG --> UC04
RR --> UC02
RR --> UC03
RR --> UC04
AD --> UC01
AD --> UC02
AD --> UC04
DSI --> UC05
AT --> UC01
CBS --> UC01
CBS --> UC02
MTN --> UC01
OM --> UC01
@enduml
```

---

### 4.2 UC01 — Gestion des collectes digitales (détaillé)

```plantuml
@startuml usecase_UC01
title UC01 — Gestion des collectes digitales

left to right direction

actor "Analyste Data" as AD
actor "Agent Terrain" as AT
actor "MTN Mobile Money" as MTN <<système>>
actor "Orange Money" as OM <<système>>
actor "Core Banking System" as CBS <<système>>

rectangle "UC01 — Gestion des Collectes Digitales" {
  usecase "UC01.1 Ingérer relevé MTN Mobile Money" as UC0101
  usecase "UC01.2 Ingérer relevé Orange Money" as UC0102
  usecase "UC01.3 Ingérer fichier agent terrain" as UC0103
  usecase "UC01.4 Dédupliquer les transactions" as UC0104
  usecase "UC01.5 Rapprocher avec données CBS" as UC0105
  usecase "UC01.6 Consulter volume collectes" as UC0106
  usecase "UC01.7 Exporter données collectes" as UC0107
  usecase "UC01.8 Réingérer fichier en erreur" as UC0108
}

MTN --> UC0101
OM --> UC0102
AT --> UC0103
UC0101 ..> UC0104 : <<include>>
UC0102 ..> UC0104 : <<include>>
UC0103 ..> UC0104 : <<include>>
UC0104 ..> UC0105 : <<include>>
AD --> UC0106
AD --> UC0107
AD --> UC0108
UC0106 ..> UC0105 : <<include>>
@enduml
```

---

### 4.3 UC02 — Suivi du portefeuille de créances (détaillé)

```plantuml
@startuml usecase_UC02
title UC02 — Suivi du portefeuille de créances

left to right direction

actor "Responsable Recouvrement" as RR
actor "Analyste Data" as AD
actor "Core Banking System" as CBS <<système>>

rectangle "UC02 — Suivi du portefeuille de créances" {
  usecase "UC02.1 Calculer PAR30" as UC0201
  usecase "UC02.2 Calculer PAR90" as UC0202
  usecase "UC02.3 Calculer taux de recouvrement" as UC0203
  usecase "UC02.4 Calculer score de risque client" as UC0204
  usecase "UC02.5 Visualiser évolution du PAR" as UC0205
  usecase "UC02.6 Filtrer par zone / agent / produit" as UC0206
  usecase "UC02.7 Exporter liste créances à risque" as UC0207
}

CBS --> UC0201
CBS --> UC0202
CBS --> UC0203
UC0201 ..> UC0205 : <<include>>
UC0202 ..> UC0205 : <<include>>
RR --> UC0205
RR --> UC0206
RR --> UC0207
AD --> UC0203
AD --> UC0204
UC0205 ..> UC0206 : <<extend>>
@enduml
```

---

### 4.4 UC03 — Recouvrement et relances (détaillé)

```plantuml
@startuml usecase_UC03
title UC03 — Recouvrement et relances

left to right direction

actor "Responsable Recouvrement" as RR
actor "Serveur Email" as EMAIL <<système>>

rectangle "UC03 — Recouvrement et Relances" {
  usecase "UC03.1 Détecter créances échues" as UC0301
  usecase "UC03.2 Générer alerte impayé" as UC0302
  usecase "UC03.3 Envoyer notification email" as UC0303
  usecase "UC03.4 Consulter liste alertes actives" as UC0304
  usecase "UC03.5 Configurer seuil d'alerte" as UC0305
  usecase "UC03.6 Clôturer une alerte" as UC0306
  usecase "UC03.7 Consulter historique des relances" as UC0307
}

UC0301 ..> UC0302 : <<include>>
UC0302 ..> UC0303 : <<include>>
EMAIL <-- UC0303
RR --> UC0304
RR --> UC0305
RR --> UC0306
RR --> UC0307
UC0304 ..> UC0306 : <<extend>>
@enduml
```

---

### 4.5 UC04 — Reporting et tableaux de bord (détaillé)

```plantuml
@startuml usecase_UC04
title UC04 — Reporting et tableaux de bord

left to right direction

actor "Directeur Général" as DG
actor "Responsable Recouvrement" as RR
actor "Analyste Data" as AD

rectangle "UC04 — Reporting et tableaux de bord" {
  usecase "UC04.1 Consulter dashboard Collectes" as UC0401
  usecase "UC04.2 Consulter dashboard Recouvrement" as UC0402
  usecase "UC04.3 Consulter dashboard Exécutif" as UC0403
  usecase "UC04.4 Filtrer par période" as UC0404
  usecase "UC04.5 Filtrer par zone / agent" as UC0405
  usecase "UC04.6 Exporter données en CSV" as UC0406
  usecase "UC04.7 S'authentifier" as UC0407
}

DG --> UC0403
RR --> UC0401
RR --> UC0402
AD --> UC0401
AD --> UC0402
AD --> UC0406
UC0401 ..> UC0407 : <<include>>
UC0402 ..> UC0407 : <<include>>
UC0403 ..> UC0407 : <<include>>
UC0401 ..> UC0404 : <<extend>>
UC0401 ..> UC0405 : <<extend>>
UC0402 ..> UC0404 : <<extend>>
UC0402 ..> UC0405 : <<extend>>
@enduml
```

---

### 4.6 UC05 — Administration du pipeline (détaillé)

```plantuml
@startuml usecase_UC05
title UC05 — Administration du pipeline

left to right direction

actor "DSI / Admin" as DSI

rectangle "UC05 — Administration du Pipeline" {
  usecase "UC05.1 Surveiller l'état des DAGs" as UC0501
  usecase "UC05.2 Lancer manuellement un DAG" as UC0502
  usecase "UC05.3 Rejouer un DAG échoué (backfill)" as UC0503
  usecase "UC05.4 Consulter les logs d'exécution" as UC0504
  usecase "UC05.5 Configurer les connexions sources" as UC0505
  usecase "UC05.6 Gérer les utilisateurs Superset" as UC0506
  usecase "UC05.7 Sauvegarder la base de données" as UC0507
}

DSI --> UC0501
DSI --> UC0502
DSI --> UC0503
DSI --> UC0504
DSI --> UC0505
DSI --> UC0506
DSI --> UC0507
UC0503 ..> UC0502 : <<include>>
UC0501 ..> UC0504 : <<extend>>
@enduml
```

---

## 5. Matrice de traçabilité

### Besoins fonctionnels ↔ Use Cases

| Besoin fonctionnel | UC01 | UC02 | UC03 | UC04 | UC05 |
|---|:---:|:---:|:---:|:---:|:---:|
| BF01 — Ingérer CSV MTN/Orange | ✓ | | | | |
| BF02 — Connexion CBS | ✓ | ✓ | | | |
| BF03 — Déduplication transactions | ✓ | | | | |
| BF04 — Journaliser ingestions | ✓ | | | | ✓ |
| BF05 — Notifier DSI en cas d'échec | | | | | ✓ |
| BF06 — Réingestion manuelle | | | | | ✓ |
| BF07 — Calcul PAR30 | | ✓ | | | |
| BF08 — Calcul PAR90 | | ✓ | | | |
| BF09 — Taux recouvrement | | ✓ | | | |
| BF10 — Volume collectes par canal | ✓ | | | ✓ | |
| BF11 — Taux de défaut mensuel | | ✓ | | ✓ | |
| BF12 — Score de risque client | | ✓ | ✓ | | |
| BF13 — Alerte créance > 30j | | | ✓ | | |
| BF14 — Email alerte recouvrement | | | ✓ | | |
| BF15 — Configuration seuil alerte | | | ✓ | | |
| BF16 — Historique alertes | | | ✓ | ✓ | |
| BF17 — Dashboard Collectes | | | | ✓ | |
| BF18 — Dashboard Recouvrement | | | | ✓ | |
| BF19 — Dashboard Exécutif | | | | ✓ | |
| BF20 — Filtrage dashboards | | | | ✓ | |
| BF21 — Export CSV | | | | ✓ | |
| BF22 — Lancement manuel DAG | | | | | ✓ |
| BF23 — Surveillance état traitements | | | | | ✓ |
| BF24 — Backfill / Replay | | | | | ✓ |
| BF25 — Journalisation exécutions | | | | | ✓ |

### Acteurs ↔ Use Cases

| Acteur | UC01 | UC02 | UC03 | UC04 | UC05 |
|---|:---:|:---:|:---:|:---:|:---:|
| Directeur Général | | | | ✓ | |
| Responsable Recouvrement | | ✓ | ✓ | ✓ | |
| Analyste Data | ✓ | ✓ | | ✓ | |
| DSI / Admin | | | | | ✓ |
| Agent Terrain | ✓ | | | | |
| CBS (système) | ✓ | ✓ | | | |
| MTN Mobile Money (système) | ✓ | | | | |
| Orange Money (système) | ✓ | | | | |
