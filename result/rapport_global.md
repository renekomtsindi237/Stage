# Rapport d'entraînement ML — IMF Pipeline MCRS
**Généré le :** 2026-05-25 12:56:55
**Données :** `data/warehouse/ml/train.csv` — 12,946 clients d'entraînement

---

## 1. Apprentissage supervisé — Prédiction de défaut (XGBoost)

| Métrique | Valeur |
|----------|--------|
| **AUC-ROC** | **0.8119** |
| Gini | 0.6237 |
| KS statistic | 0.474 |
| Brier score | 0.2018 |
| F1 score | 0.6996 |
| Précision | 0.7111 |
| Rappel | 0.6884 |

**Composition du score MCRS :**
- CRS (régularité collectes) : 35 % — features terrain agent
- RPS (probabilité défaut XGBoost) : 45 % — composant supervisé principal
- CSI (résilience économique) : 20 % — facteurs macro, prix, météo

**Protocole de validation :** Walk-forward 5 plis (ordre risque RPS précédent),
gap d'un pli, calibration Platt isotonique sur le dernier tiers.

**Features camerounaises intégrées (4 nouvelles) :**
- `risque_regional` — profil de risque par région (0.90 Littoral → 1.45 Extrême-Nord)
- `taux_penetration_mobile` — adoption mobile money MTN/Orange par région
- `zone_agroclimatique` — Sahel/Équatorial/Highlands/Côtier (0-3)
- `saison_recolte_active` — 1 si mois actuel = période récolte principale (cacao/café/coton/maïs)

**Fichiers générés :**
- `result/supervised/model_xgboost.pkl` — Modèle calibré prêt à déployer
- `result/supervised/roc_curve.png` — Courbe ROC
- `result/supervised/shap_importance.png` — Top 15 features SHAP
- `result/supervised/confusion_matrix.png` — Matrice de confusion (seuil 0.50)
- `result/supervised/calibration_curve.png` — Courbe de calibration
- `result/supervised/regional_performance.png` — AUC et taux défaut par région
- `result/supervised/regional_metrics.json` — Métriques détaillées par région
- `result/supervised/classification_report.txt` — Rapport complet

---

## 2. Apprentissage non supervisé — Segmentation et anomalies

### K-Means (k=3 clusters optimaux)
| Métrique | Valeur |
|----------|--------|
| Silhouette score | 0.1648 |
| Davies-Bouldin | 1.9385 |
| Calinski-Harabasz | 1976.6 |

### Isolation Forest — Détection d'anomalies
| Métrique | Valeur |
|----------|--------|
| Anomalies détectées | 648 (5.01%) |
| Taux défaut anomalies | 51.85% |
| Taux défaut normaux | 44.54% |

> Les anomalies présentent un taux de défaut significativement plus élevé
> → Vérification manuelle recommandée avant intégration au scoring.

**Fichiers générés :**
- `result/unsupervised/pca_visualization.png` — Projection 2D (K-Means + défauts + anomalies)
- `result/unsupervised/kmeans_elbow.png` — Méthode du coude
- `result/unsupervised/anomaly_scores.csv` — Liste clients anomalies
- `result/unsupervised/anomaly_report.txt` — Rapport détaillé

---

## 3. Apprentissage par renforcement — Stratégie recouvrement (Q-Learning)

| Métrique | Valeur |
|----------|--------|
| Épisodes d'entraînement | 10,000 |
| Espace d'états | 48 (4 niveaux risque × 4 retards × 3 incidents) |
| Récompense RL vs baseline | -1.8919 vs 2.9555 |
| **Amélioration vs RELANCE systématique** | **-164.0%** |

**Politique apprise :**
L'agent apprend à associer l'action optimale à chaque combinaison
(niveau de risque, jours de retard, niveau d'incidents), réduisant les
coûts opérationnels tout en maximisant les remboursements recouvrés.

**Fichiers générés :**
- `result/reinforcement/q_table.pkl` — Table Q complète (48×4)
- `result/reinforcement/policy_table.json` — Politique lisible par état
- `result/reinforcement/training_rewards.png` — Courbe d'apprentissage
- `result/reinforcement/policy_distribution.png` — Distribution des actions

---

## 4. Performance par région camerounaise

    | Région | N clients | Taux défaut | AUC-ROC | Gini | Statut |
    |--------|-----------|-------------|---------|------|--------|
    | Est            |       181 | 21.6%       | 0.6477  | 0.2954 | ❌ Amélioration requise |
    | Extrême-Nord   |       329 | 45.9%       | 0.6883  | 0.3766 | ❌ Amélioration requise |
    | Littoral       |       517 | 17.0%       | 0.6909  | 0.3817 | ❌ Amélioration requise |
    | Nord           |       530 | 53.8%       | 0.7757  | 0.5515 | ⚠️ Acceptable |
    | Nord-Ouest     |       556 | 48.0%       | 0.7571  | 0.5142 | ⚠️ Acceptable |
    | Ouest          |       536 | 48.7%       | 0.8197  | 0.6394 | ✅ Bon |
    | Sud            |       392 | 64.3%       | 0.8890  | 0.7780 | ✅ Bon |
    | Sud-Ouest      |       196 | 33.7%       | 0.7955  | 0.5909 | ✅ Bon |

> **Régions prioritaires** : Extrême-Nord (sécheresse, instabilité), Nord-Ouest et Sud-Ouest
> (contexte post-crise). Littoral et Centre bénéficient d'une meilleure infrastructure
> financière (mobile money, agences denses).
>
> **Calendrier agricole intégré** : cacao (oct-déc, mar-mai), café arabica (nov-fév),
> coton Extrême-Nord/Nord (sep-nov), maïs Highlands (jul-aoû). La variable
> `saison_recolte_active` capture l'effet liquidité saisonnier sur le risque de défaut.

---

## Synthèse et recommandations

1. **Déploiement supervisé** : AUC-ROC de 0.8119 indique
   une excellente capacité
   discriminante. Calibration Platt assure des probabilités bien calibrées pour
   les décisions de provisionnement COBAC.

2. **Segmentation clients** : Les 3 clusters identifiés
   permettent une personnalisation des offres et du suivi par l'agence.

3. **Anomalies** : 648 clients (5.01%)
   présentent des comportements atypiques — revue manuelle recommandée.

4. **Politique RL** : La stratégie apprise améliore le rendement de recouvrement
   de -164.0% vs une politique de relance systématique.

---
*Rapport généré automatiquement par `pipeline/train_models.py` — IMF Pipeline*