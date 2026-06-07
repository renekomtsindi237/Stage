# Recommandations techniques — Modèle MCRS

## Observations clés
- Disparités AUC, FPR et FNR observées entre régions et groupes socio-économiques (voir `result/eval_equity.json`).
- Certaines régions présentent AUC < 0.7 → vérifier dataset local et features spécifiques (data quality, label noise).
- Missingness 30% réduit AUC ≈ 0.78 — prévoir robustesse et imputation robuste.

## Actions recommandées (priorité)
1. Re-vérifier représentativité du jeu d'entraînement pour régions sous-performantes et enrichir en données locales si nécessaire.
2. Ajuster seuils par région (seuils opérationnels) pour contrôler FPR/FNR au niveau local plutôt que global.
3. Mettre en place monitoring post-déploiement : AUC, FPR, FNR, dérive covariables par région et par quartile de capacité.
4. Ajouter calibration locale si nécessaire (re-calibration par région).
5. Avant déploiement, exécuter un pilote en production avec revue humaine pour les cas à risque élevé.

## Actions techniques (moyen terme)
- Entraîner modèles locaux ou modèles multi-tâches avec adaptation par domaine (domain adaptation).
- Explorer modèles plus robustes au bruit et missingness (ensembles, regularization).
- Documenter features sensibles et limiter usage si elles introduisent biais discriminatoires.

## Mesures opérationnelles
- Définir KPIs métier par région (ex : gains de recouvrement, coûts faux positifs).
- Processus d'appel manuel pour prédictions critiques et procédure de contestation.

## Pilote contrôlé au Cameroun
- Régions prises en compte : Centre, Littoral, Ouest, Nord-Ouest, Sud-Ouest, Bamenda, Est, Sud.
- Seuils opérationnels locaux générés dans `pipeline/region_thresholds.json` à partir du jeu d'évaluation disponible.
- Règle de pilotage : toute prédiction au-dessus du seuil local passe en revue humaine avant action métier.
- Région(s) à surveiller en priorité : Centre et Ouest, où l'AUC est la plus faible sur le jeu de test.
- Le déploiement national doit rester progressif : pilote par région, mesure hebdomadaire de FPR/FNR et recalibration si dérive.

## Adaptation locale — Cartographie régionale (Cameroun)

Mapping codes→régions utilisé :


- REG03 → Centre
- REG04 → Littoral
- REG05 → Ouest
- REG06 → Nord-Ouest
- REG07 → Sud-Ouest
- REG08 → Bamenda
- REG09 → Est
- REG10 → Sud

