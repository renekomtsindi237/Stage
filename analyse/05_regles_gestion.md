# 05 — Règles de Gestion

**Mémoire :** Conception d'un pipeline de données pour l'analyse et le suivi des collectes d'épargnes et recouvrement de créances dans les institutions de microfinance au Cameroun
**Auteur :** KOMTSINDI Réné Alban
**Établissement :** Institut Universitaire Saint Jean — Yaoundé
**Année :** 2025–2026

---

## 1. Règles — Collectes d'Épargne

### RG-CE01 — Identifiant unique mobile
Chaque collecte saisie sur l'application mobile doit être identifiée par un UUID v4 généré côté mobile (`uuid_mobile`). Le serveur doit rejeter toute collecte dont l'UUID est déjà enregistré (réponse 409 avec motif `DOUBLON_UUID`).

### RG-CE02 — Montant positif et non nul
Le montant d'une collecte doit être strictement supérieur à zéro (> 0 FCFA). Toute collecte avec un montant nul ou négatif est rejetée.

### RG-CE03 — Date de collecte cohérente
La date de collecte (`date_collecte`) ne peut pas être postérieure à la date du jour. Une collecte avec une date future est rejetée (motif `DATE_FUTURE`).

### RG-CE04 — Canal de collecte obligatoire
Le canal doit appartenir à l'énumération : `ESPECES`, `MOBILE_MONEY`, `VIREMENT`. Toute valeur non reconnue est rejetée.

### RG-CE05 — Agent lié à l'agence
Un agent ne peut soumettre des collectes que pour les clients de son agence. Le système vérifie que `client.agence_id == agent.agence_id`.

### RG-CE06 — Collecte soumise avant validation
Une collecte doit passer par l'état `SOUMISE` avant de pouvoir être validée ou rejetée. Une collecte déjà `VALIDEE` ne peut plus être modifiée.

### RG-CE07 — Taux de réalisation objectif
```
taux_realisation = (montant_realise / montant_objectif) × 100
```
Le `montant_realise` est la somme des collectes à statut `VALIDEE` pour le cycle et l'agent concernés.

### RG-CE08 — Variation hebdomadaire collecte
```
variation_semaine_pct = ((montant_semaine_n - montant_semaine_n1) / montant_semaine_n1) × 100
```
Si `montant_semaine_n1 = 0`, la variation est indéfinie (valeur `null`).

### RG-CE09 — Seuil d'alerte objectif non atteint
Une alerte `OBJECTIF_NON_ATTEINT` est générée si :
```
(taux_realisation < seuil_alerte_objectif) ET (jours_restants_cycle <= 3)
```
`seuil_alerte_objectif` est configurable par agence (défaut : 70%).

### RG-CE10 — Déduplication par synchronisation
La synchronisation est idempotente : soumettre le même batch deux fois ne crée pas de doublons. Le serveur répond avec les UUIDs reçus et leur statut (accepté/doublon/rejeté) à chaque appel.

---

## 2. Règles — Classification COBAC

### RG-CO01 — Calcul des jours de retard
```
jours_retard = CURRENT_DATE - date_derniere_echeance_impayee
```
Si `jours_retard < 0` (pas d'échéance dépassée), la créance est classée A.

### RG-CO02 — Classification en classe COBAC
```
jours_retard < 30       → Classe A (créance courante)
30 ≤ jours_retard < 90  → Classe B (créance en souffrance ordinaire)
90 ≤ jours_retard < 180 → Classe C (créance douteuse)
180 ≤ jours_retard < 360→ Classe D (créance litigieuse)
jours_retard ≥ 360      → Classe E (créance irrécouvrable)
```

### RG-CO03 — Taux de provisionnement réglementaire
```
Classe A : 0%
Classe B : 20% de l'encours
Classe C : 50% de l'encours
Classe D : 80% de l'encours
Classe E : 100% de l'encours
```
```sql
montant_provision = ROUND(montant_encours * taux_provision / 100, 2)
```

### RG-CO04 — Calcul du PAR
```
PAR30 = (Σ encours créances avec jours_retard ≥ 30) / encours_total_portefeuille
PAR60 = (Σ encours créances avec jours_retard ≥ 60) / encours_total_portefeuille
PAR90 = (Σ encours créances avec jours_retard ≥ 90) / encours_total_portefeuille
PAR180= (Σ encours créances avec jours_retard ≥ 180) / encours_total_portefeuille
```
Le PAR est exprimé en pourcentage (×100) et en montant absolu.

### RG-CO05 — Seuil d'alerte PAR COBAC
Alerte `PAR_SEUIL_DEPASSE` générée si PAR90 > seuil configuré (défaut : 5% conformément aux recommandations COBAC pour les EMF de catégorie 1).

### RG-CO06 — Snapshot quotidien obligatoire
Un snapshot des KPI recouvrement est archivé chaque jour dans `app.kpi_recouvrement_snapshots`. Les snapshots ne sont jamais mis à jour (INSERT uniquement) pour garantir la traçabilité réglementaire.

---

## 3. Règles — Scoring MCRS

### RG-ML01 — Formule composite MCRS
```
MCRS = 0.35 × CRS + 0.45 × RPS + 0.20 × CSI
```
Le score MCRS est compris dans [0, 1]. Un score élevé indique un risque de défaut élevé.

### RG-ML02 — Classification du risque MCRS
```
MCRS < 0.30  → FAIBLE
MCRS < 0.55  → MODERE
MCRS < 0.75  → ELEVE
MCRS ≥ 0.75  → CRITIQUE
```

### RG-ML03 — Calcul du CRS (Collection Reliability Score)
```
CRS = 0.6 × regularite_collecte_pct + 0.4 × sigmoid(tendance_30j)
```
Où :
- `regularite_collecte_pct` = (nb_semaines_avec_collecte / nb_semaines_suivi) × 100.
- `tendance_30j` = pente normalisée de la série temporelle des collectes sur 30 jours.
- `sigmoid(x) = 1 / (1 + exp(-x))`.

### RG-ML04 — Calcul du RPS (Recovery Prediction Score)
Le RPS est la probabilité calibrée (Platt scaling) produite par le modèle XGBoost : P(défaut à 90 jours). Un défaut est défini comme le passage en classe COBAC C ou pire dans les 90 jours suivants.

### RG-ML05 — Calcul du CSI (Client Solvency Index)
```
CSI = 0.50 × indice_resilience + 0.30 × impact_prix + 0.20 × impact_macro_meteo
```
Où :
- `indice_resilience = min(nb_produits_activite / 5, 1)` (diversification).
- `impact_prix` : fonction de la volatilité et de la tendance du prix du produit principal.
- `impact_macro_meteo` : score composite des indicateurs macro et météo de la zone.

### RG-ML06 — Périmètre du scoring
Seuls les clients ayant au moins une créance active (non classée E) et 4 semaines de données de collecte minimum sont scorés par le modèle MCRS.

### RG-ML07 — Détection de dérive (PSI)
```
PSI = Σ (Ai - Ei) × ln(Ai / Ei)
```
Où Ai = proportion actuelle, Ei = proportion de référence (distribution d'entraînement), pour chaque décile du score.
- PSI < 0.10 : pas de dérive significative.
- 0.10 ≤ PSI < 0.20 : dérive modérée → surveillance.
- PSI ≥ 0.20 : dérive significative → retraining automatique déclenché.

---

## 4. Règles — Facteurs Externes

### RG-FE01 — Généricité des produits
Aucun produit agricole ou commercial ne doit être hardcodé dans la logique du pipeline ML. Tous les produits référencés dans les features sont récupérés dynamiquement depuis `app.produits_generiques` (filtre `actif = TRUE`).

### RG-FE02 — Score de fiabilité des prix
Les prix avec un score de fiabilité ≤ 2 (sur 5) ne sont pas utilisés dans le calcul des features CSI. Les features basées sur des prix de faible fiabilité sont marquées comme `NULL` (le modèle gère les valeurs manquantes par imputation médiane).

### RG-FE03 — Fenêtre temporelle des features
Les features de prix et météo sont calculées sur les 90 derniers jours glissants. Si moins de 30 jours de données sont disponibles pour un produit/zone, la feature correspondante est `NULL`.

### RG-FE04 — Variation de prix : sens économique
Pour les producteurs (secteur agriculture), une hausse du prix de leur produit principal améliore la solvabilité (CSI plus bas). Pour les consommateurs nets (artisans, revendeurs achetant la matière première), c'est l'inverse. Le profil du client (`app.clients_informels.secteur_activite`) détermine le sens de l'impact.

---

## 5. Règles — Multi-tenant et Sécurité

### RG-MT01 — Isolation des données par IMF
Toute lecture ou écriture dans les tables `app.*` doit inclure un filtre `imf_id = TenantContext.getCurrentTenant()`. L'absence de ce filtre est une erreur de sécurité critique.

### RG-MT02 — Benchmarks anonymisés
Les benchmarks inter-agences comparent des agences au sein d'une même IMF uniquement. Les données agrégées cross-IMF produites par le pipeline pour usage académique ne permettent pas d'identifier une IMF spécifique.

### RG-MT03 — Rotation du token de rafraîchissement
À chaque utilisation du refresh token, un nouveau `jti` (JWT ID) est généré et l'ancien est invalidé. Cela prévient la réutilisation d'un refresh token compromis.

### RG-MT04 — Verrouillage de compte
Après 5 tentatives de connexion consécutives échouées, le compte est verrouillé pendant 15 minutes. La tentative de connexion est loggée avec l'IP source.
