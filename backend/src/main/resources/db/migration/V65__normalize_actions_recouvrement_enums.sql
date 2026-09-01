-- V65 — Harmonise les libellés d'actions de recouvrement (seeds V55 vs enums Java).
-- Sans ça, GET /recouvrement/dossiers/{uid}/actions échoue dès qu'une ligne
-- contient MISE_EN_DEMEURE ou LETTRE_ENVOYEE.

UPDATE app.actions_recouvrement
SET type_action = 'MISE_EN_DEMEURE_LETTRE'
WHERE type_action IN ('MISE_EN_DEMEURE', 'LETTRE_MISE_EN_DEMEURE');

UPDATE app.actions_recouvrement
SET resultat = 'CONTACT_ETABLI'
WHERE resultat IN ('CLIENT_CONTACTE', 'CONTACTE');

UPDATE app.actions_recouvrement
SET resultat = 'PROMESSE_PAIEMENT'
WHERE resultat IN ('PROMESSE_DE_PAIEMENT');

UPDATE app.actions_recouvrement
SET resultat = 'SANS_REPONSE'
WHERE resultat IN ('SANS_SUITE', 'CLIENT_ABSENT');

UPDATE app.actions_recouvrement
SET resultat = 'REFUSE'
WHERE resultat IN ('CLIENT_REFUSE');

UPDATE app.actions_recouvrement
SET resultat = 'PAIEMENT_EFFECTUE'
WHERE resultat IN ('PAIEMENT_TOTAL');

UPDATE app.actions_recouvrement
SET resultat = 'EN_ATTENTE'
WHERE resultat IN ('LETTRE_ENVOYEE', 'COURRIER_ENVOYE');

UPDATE app.actions_recouvrement
SET canal_paiement = 'MTN'
WHERE canal_paiement IN ('MTN_MOBILE_MONEY', 'MOMO', 'MTN_MOMO');

UPDATE app.actions_recouvrement
SET canal_paiement = 'ORANGE'
WHERE canal_paiement IN ('ORANGE_MONEY', 'OM');
