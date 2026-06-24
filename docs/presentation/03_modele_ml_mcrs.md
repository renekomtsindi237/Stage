# MicroRecouv — Modèle ML : MCRS (Multi-Criteria Recovery Scoring)

---

## 1. Définition et objectif

Le **MCRS** (Multi-Criteria Recovery Scoring) est un score composite $\in [0, 1]$ qui mesure le **risque de non-recouvrement** d'un client à horizon 90 jours. Plus le score est proche de 1, plus le risque est élevé.

Il combine trois composantes indépendantes qui capturent trois dimensions différentes du risque :

| Composante | Nom complet | Ce qu'elle mesure | Poids par défaut |
|---|---|---|---|
| **CRS** | Collection Reliability Score | Régularité des collectes terrain | 35 % |
| **RPS** | Recovery Prediction Score | Probabilité de défaut à 90 j (XGBoost) | 45 % |
| **CSI** | Client Solvency Index | Résilience économique face aux facteurs externes | 20 % |

---

## 2. Formule principale du MCRS

$$\boxed{MCRS = w_{CRS} \cdot (1 - CRS) + w_{RPS} \cdot RPS + w_{CSI} \cdot (1 - CSI)}$$

Avec $w_{CRS} + w_{RPS} + w_{CSI} = 1$ et $MCRS \in [0, 1]$.

**Lecture de la formule :**
- CRS et CSI sont des scores de *qualité* (1 = excellent). On les inverse pour qu'un bon comportement *réduise* le MCRS.
- RPS est déjà une probabilité de défaut (0 = pas de risque), donc on l'utilise directement.
- Les poids sont configurables par le DSI via `scoring_config.json` **sans réentraîner le modèle**.

**Valeurs par défaut :**

$$MCRS = 0.35 \cdot (1 - CRS) + 0.45 \cdot RPS + 0.20 \cdot (1 - CSI)$$

---

## 3. Composante CRS — Collection Reliability Score

### Définition
Le CRS mesure à quel point un client est régulier dans ses dépôts d'épargne terrain sur les 90 derniers jours. Un client qui collecte toutes les semaines, pour des montants stables, obtient un CRS proche de 1.

### Formule

$$CRS = \sum_{i} w_i^{CRS} \cdot f_i$$

avec les sous-indicateurs suivants :

| Sous-indicateur $f_i$ | Poids $w_i$ | Formule |
|---|---|---|
| Régularité | 0.30 | $\text{regularite\_collecte\_pct} = \frac{\text{semaines avec collecte}}{13}$ |
| Fréquence normalisée | 0.20 | $\min\!\left(\frac{\text{nb\_collectes\_30j}}{4}, 1\right)$ |
| Taux de remboursement | 0.25 | $1 - \frac{\text{montant impayé total}}{\text{montant décaissé total}}$ |
| Pénalité absences | 0.15 | $1 - \frac{\text{nb\_semaines\_sans\_collecte}}{52}$ |
| Stabilité des montants | 0.10 | $\max\!\left(0,\ 1 - \frac{CV}{2}\right)$ où $CV = \frac{\sigma(\text{montants})}{\mu(\text{montants})}$ |

**Exemple :**
- Collecte 10 semaines sur 13 → régularité = 10/13 = 0.77
- 3 collectes en 30 jours → fréquence = 3/4 = 0.75
- Taux remboursement = 0.80
- 3 semaines sans collecte → absence = 1 - 3/52 = 0.94
- CV montants = 0.40 → stabilité = 1 - 0.40/2 = 0.80

$$CRS = 0.30 \times 0.77 + 0.20 \times 0.75 + 0.25 \times 0.80 + 0.15 \times 0.94 + 0.10 \times 0.80 = \mathbf{0.80}$$

---

## 4. Composante RPS — Recovery Prediction Score

### Définition
Le RPS est la **probabilité de défaut à 90 jours** produite par un modèle **XGBoost calibré** (calibration de Platt). C'est la composante entièrement apprise par machine learning.

$$RPS = P(\text{défaut dans 90 jours} \mid \text{features client})$$

### Features utilisées (6 features RPS)

| Feature | Description |
|---|---|
| `jours_retard_actuel` | Nombre de jours de retard sur la créance la plus dégradée |
| `nb_incidents_paiement_12m` | Nombre de créances PAR30+ dans les 12 derniers mois |
| `taux_remboursement_historique` | 1 - (impayé total / décaissé total) |
| `ratio_creance_revenus` | Encours impayé / (collecte mensuelle moyenne × 12) |
| `nb_reechelonnements` | Nombre d'accords de rééchelonnement sur la vie du contrat |
| `score_rps_precedent` | Score RPS du dernier scoring (continuité temporelle) |

### Modèle XGBoost — Hyperparamètres

L'entraînement utilise l'ensemble des 30 features (CRS + RPS + CSI + CAMEROON) :

| Paramètre | Valeur | Rôle |
|---|---|---|
| `n_estimators` | 500 | Nombre d'arbres |
| `max_depth` | 6 | Profondeur max par arbre |
| `learning_rate` | 0.05 | Pas d'apprentissage (shrinkage) |
| `subsample` | 0.8 | Fraction des lignes par arbre |
| `colsample_bytree` | 0.8 | Fraction des colonnes par arbre |
| `early_stopping_rounds` | 50 | Arrêt si pas d'amélioration sur 50 tours |
| `scale_pos_weight` | auto (ratio négatifs/positifs) | Correction déséquilibre de classes |

**Fonction objectif XGBoost (log loss binaire) :**

$$\mathcal{L} = -\frac{1}{n} \sum_{i=1}^{n} \left[ y_i \log(\hat{p}_i) + (1 - y_i) \log(1 - \hat{p}_i) \right]$$

où $y_i \in \{0, 1\}$ est le label réel et $\hat{p}_i$ est la probabilité prédite.

### Calibration de Platt (post-hoc)

XGBoost produit des scores bien ordonnés mais mal calibrés (l'échelle de probabilité peut être déformée). La **calibration de Platt** (régression logistique sur les prédictions brutes) corrige cela pour que $P(\hat{y} = 0.7)$ corresponde réellement à 70 % de défauts observés.

$$p_{\text{calibrée}} = \frac{1}{1 + e^{-(A \cdot \hat{p}_{\text{brute}} + B)}}$$

où $A$ et $B$ sont appris sur un ensemble de calibration (dernier tiers des données).

**Score de Brier (mesure de calibration) :**

$$BS = \frac{1}{n} \sum_{i=1}^{n} (\hat{p}_i - y_i)^2$$

Un BS proche de 0 indique une bonne calibration. Le rapport de performance obtenu : BS = 0.0114 (moyen sur 5 folds).

### Validation walk-forward temporelle

Pour éviter les fuites d'information futures (*data leakage*), la validation utilise un schéma **walk-forward** (jamais aléatoire) :

```
Données temporelles : Jan 2024 ──────────────────────────── Déc 2025

Fold 1 : [Train Jan-Déc 2024] [Gap 3m] [Test Avr-Jun 2025]
Fold 2 : [Train Avr 2024-Mar 2025] [Gap 3m] [Test Jul-Sep 2025]
Fold 3 : [Train Jul 2024-Jun 2025] [Gap 3m] [Test Oct-Déc 2025]
...
```

Le gap de 3 mois simule le délai réel entre la décision de crédit et l'observation du défaut.

---

## 5. Composante CSI — Client Solvency Index

### Définition
Le CSI mesure la **résilience économique** du client face aux chocs externes — prix de ses produits sur les marchés locaux, conditions climatiques, inflation. Un maïs-cultivateur en période de récolte avec prix stables a un CSI élevé.

### Formule

$$CSI = \sum_{i} w_i^{CSI} \cdot g_i$$

| Sous-indicateur $g_i$ | Poids $w_i$ | Formule |
|---|---|---|
| Indice de résilience | 0.35 | $\frac{\text{nb produits vendus}}{5}$ (capped à 1) × 0.5 + régularité collecte × 0.5 |
| Capacité de remboursement | 0.30 | $\min\!\left(\frac{\text{revenu mensuel} - \text{impayé mensuel estimé}}{\text{saturation}}, 1\right)$ |
| Pression prix | 0.15 | $1 - \frac{\max(0,\ \pi - \pi_{\text{ref}})}{\pi_{\text{ref}}}$ où $\pi$ = inflation, $\pi_{\text{ref}} = 5\%$ |
| Pression climatique | 0.10 | $1 - \frac{\text{indice\_sécheresse}}{\text{sécheresse\_max}=4}$ |
| Diversification | 0.10 | score\_diversification\_produits ∈ [0, 1] |

**Exemple — client avec pression saisonnière :**
- Résilience = 0.60 (2 produits, régularité 70 %)
- Capacité remboursement = 0.80 (revenu bon, peu d'impayés)
- Inflation = 7 % → pression prix = 1 - (7-5)/5 = 0.60
- Sécheresse index = 1.0 → pression climatique = 1 - 1/4 = 0.75
- Diversification = 0.40

$$CSI = 0.35 \times 0.60 + 0.30 \times 0.80 + 0.15 \times 0.60 + 0.10 \times 0.75 + 0.10 \times 0.40 = \mathbf{0.65}$$

---

## 6. Features contextuelles camerounaises (4 features)

Ces 4 features ajoutent le contexte géographique et saisonnier propre au Cameroun.

| Feature | Description | Source |
|---|---|---|
| `risque_regional` | Coefficient de risque de la région (REG01-REG10) | scoring_config.json |
| `taux_penetration_mobile` | Taux d'usage du mobile money dans la région | scoring_config.json |
| `zone_agroclimatique` | 0=sahel, 1=équatorial, 2=highland, 3=côtier | scoring_config.json |
| `saison_recolte_active` | 1 si le mois courant correspond à la récolte principale | Calendrier agricole |

**Exemple de coefficients régionaux :**

| Région | Risque base | Mobile money | Zone |
|---|---|---|---|
| Extrême-Nord | 1.45 | 25 % | Sahel |
| Littoral (Douala) | 0.90 | 85 % | Côtier |
| Nord-Ouest | 1.25 | 55 % | Highland |
| Centre (Yaoundé) | 1.00 | 75 % | Équatorial |

Le `risque_regional` est utilisé directement comme feature numérique dans XGBoost. L'Extrême-Nord a un coefficient 1.45 car il combine insécurité alimentaire (dépendance coton), faible pénétration mobile (difficultés de collecte) et aléas climatiques (saison sèche prolongée).

---

## 7. Classification du risque et alertes

### Seuils de risque (MCRS → classe)

$$\text{Classe} = \begin{cases} \text{FAIBLE} & \text{si } MCRS < 0.30 \\ \text{MODERE} & \text{si } 0.30 \leq MCRS < 0.55 \\ \text{ELEVE} & \text{si } 0.55 \leq MCRS < 0.75 \\ \text{CRITIQUE} & \text{si } MCRS \geq 0.75 \end{cases}$$

Ces seuils sont **configurables par IMF** via l'interface DSI sans modifier le code ni réentraîner.

### Alertes prédictives (déclenchées automatiquement)

| Alerte | Condition | Action |
|---|---|---|
| `RISQUE_DEFAUT_IMMINENT` | $MCRS \geq 0.75$ | Notification push + priorité maximale dossier |
| `DETERIORATION_RAPIDE` | $MCRS \geq 0.65$ | Alerte dashboard responsable |
| `BAISSE_COLLECTE_PERSISTANTE` | $1 - CRS \geq 0.50$ | Notification agent terrain |

### Classification COBAC (indépendante du MCRS)

La classification COBAC est **réglementaire** (non modifiable) et basée uniquement sur les jours de retard :

$$\text{Classe COBAC}(j) = \begin{cases} A & j < 30 \text{ jours} \quad \rightarrow \text{ provision 0\%} \\ B & 30 \leq j < 90 \quad \rightarrow \text{ provision 20\%} \\ C & 90 \leq j < 180 \quad \rightarrow \text{ provision 50\%} \\ D & 180 \leq j < 360 \quad \rightarrow \text{ provision 80\%} \\ E & j \geq 360 \text{ jours} \quad \rightarrow \text{ provision 100\%} \end{cases}$$

---

## 8. Analyse de survie Cox PH (optionnelle)

En complément du MCRS, le modèle estime le **temps médian avant défaut** via un modèle de Cox à risques proportionnels :

$$h(t \mid \mathbf{x}) = h_0(t) \cdot \exp\!\left( \beta_1 \cdot \text{jours\_retard\_moyen} + \beta_2 \cdot \text{regularite\_collecte\_pct} + \beta_3 \cdot \text{indice\_resilience} \right)$$

où $h_0(t)$ est le risque de base et les $\beta_i$ sont estimés par maximisation de la vraisemblance partielle.

L'indice de concordance (C-statistic, analogue AUC pour le temps) est stocké dans `ml.model_runs`.

---

## 9. Explicabilité SHAP

Pour chaque client scoré, les **SHAP values** (SHapley Additive exPlanations) décomposent le score RPS feature par feature :

$$RPS(\mathbf{x}) = \phi_0 + \sum_{j=1}^{30} \phi_j(x_j)$$

où $\phi_0$ est la valeur de base (score moyen sur l'ensemble d'entraînement) et $\phi_j$ est la contribution marginale de la feature $j$ pour ce client.

**Top features SHAP sur le jeu FINTECH SARL (AUC = 0.9954) :**

| Rang | Feature | SHAP moyen |
|---|---|---|
| 1 | `jours_retard_max` | 3.47 |
| 2 | `classe_risque_cobac_encode` | 0.51 |
| 3 | `jours_retard_moyen` | 0.48 |
| 4 | `nb_incidents_paiement` | 0.19 |
| 5 | `capacite_remboursement` | 0.16 |

Ces valeurs sont stockées dans `ml.shap_explanations` et affichées dans l'interface pour expliquer à l'agent pourquoi un client est risqué.

---

## 10. Détection du drift et réentraînement (PSI)

Le modèle est réentraîné automatiquement si la distribution des features dérive trop par rapport à l'entraînement initial. La métrique utilisée est le **PSI (Population Stability Index)** :

$$PSI = \sum_{k=1}^{K} \left( P_{\text{courante},k} - P_{\text{référence},k} \right) \cdot \ln\!\left( \frac{P_{\text{courante},k}}{P_{\text{référence},k}} \right)$$

où $K$ est le nombre de bins (déciles) et les $P_k$ sont les proportions de la distribution.

**Interprétation :**

| PSI | Interprétation | Action |
|---|---|---|
| < 0.10 | Distribution stable | Aucune |
| 0.10 – 0.20 | Dérive légère | Surveillance |
| > 0.20 | Dérive significative | Réentraînement automatique |

Le PSI est calculé par segment (zone × produit) pour détecter des drifts localisés (ex : une région touchée par une sécheresse exceptionnelle).

---

## 11. Champion / Challenger

À chaque réentraînement, le nouveau modèle est comparé au **champion actuel** :

```
AUC challenger > AUC champion + 0.005
   → Oui : challenger promu champion
            ancien champion archivé dans /ml/models/mcrs/archive/
            symlink mcrs_model.pkl → champion/mcrs_model.pkl
            FastAPI rechargé (POST /model/reload)
   → Non : champion conservé
```

---

## 12. Performances mesurées (FINTECH SARL — 175 clients, 16 % de défauts)

| Métrique | Valeur (moyenne 5 folds) | Interprétation |
|---|---|---|
| **AUC-ROC** | **0.9954** | Discrimination quasi-parfaite entre défaut et non-défaut |
| **Gini** | 0.9908 | = 2 × AUC − 1 (mesure de ségrégation) |
| **KS (Kolmogorov-Smirnov)** | 0.9724 | Séparation max entre distributions positif/négatif |
| **F1-score** | 0.9667 | Équilibre précision/rappel |
| **Brier score** | 0.0114 | Calibration : les probabilités sont fiables |

**Note sur les performances :** Ces résultats sont obtenus sur un jeu de 175 clients synthétiques de FINTECH SARL. Sur des données réelles multi-IMF (2 ans, 2 000+ clients), les performances attendues sont entre AUC 0.75 et 0.85 — niveau standard en credit scoring microfinance.

---

## 13. Résumé du flux ML complet

```
PostgreSQL (app.*)
    │
    ├── dbt: int_comportement_collecte  → 7 features CRS (fenêtre 90j)
    ├── dbt: int_risque_credit          → 6 features RPS
    └── dbt: int_contexte_externe       → 13 features CSI
                    │
            dbt: ml.features_client (30 features + 4 camerounaises = 34)
                    │
    ┌───────────────▼──────────────────┐
    │        FastAPI ML (MCRSScorer)   │
    │                                  │
    │  CRS = f_CRS(7 features)         │  ← formule analytique configurable
    │  RPS = XGBoost.predict_proba()   │  ← modèle appris (calibré Platt)
    │  CSI = f_CSI(13 features)        │  ← formule analytique configurable
    │                                  │
    │  MCRS = 0.35×(1-CRS)            │
    │       + 0.45×RPS                │
    │       + 0.20×(1-CSI)            │
    └───────────────┬──────────────────┘
                    │
    ┌───────────────▼──────────────────┐
    │   ml.client_scores               │
    │   mcrs | crs | rps | csi         │
    │   risque | alertes               │
    │   cobac_classe | cobac_provision  │
    └───────────────┬──────────────────┘
                    │
    Spring Boot → SSE → Angular (dashboard directeur)
    Spring Boot → FCM → Flutter  (alerte agent terrain)
    dossiers_recouvrement.priorite_scoring mis à jour
```
