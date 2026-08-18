-- ────────────────────────────────────────────────────────────────────────────
-- V63__kyc_document_analyse_ia.sql
-- Ajoute le résultat de l'analyse IA (OCR + extraction structurée) des pièces
-- d'identité scannées lors de l'upload KYC. Vérification humaine du DSI
-- toujours requise pour la décision finale — l'IA signale, elle ne bloque pas.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE app.kyc_documents
    ADD COLUMN donnees_extraites   JSONB,        -- champs lus sur la pièce par l'IA
    ADD COLUMN ecarts_detectes     JSONB,        -- divergences vs. champs saisis dans le dossier
    ADD COLUMN analyse_ia_at       TIMESTAMPTZ,  -- horodatage de l'analyse
    ADD COLUMN analyse_ia_erreur   VARCHAR(500); -- message si l'analyse a échoué (non bloquant)
