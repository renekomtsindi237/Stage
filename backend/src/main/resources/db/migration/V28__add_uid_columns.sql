-- V28 : Ajout de la colonne uid (UUID public) sur toutes les tables exposées par l'API
-- L'uid est généré côté DB (DEFAULT gen_random_uuid()) pour les lignes existantes
-- et côté serveur JPA (@PrePersist) pour les nouvelles insertions.

-- ── Entités principales ──────────────────────────────────────────────────────

ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS utilisateurs_uid_idx ON app.utilisateurs(uid);

ALTER TABLE app.imf
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS imf_uid_idx ON app.imf(uid);

ALTER TABLE app.agences
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS agences_uid_idx ON app.agences(uid);

-- ── Alertes & Échéances ──────────────────────────────────────────────────────

ALTER TABLE app.alertes_impayes
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS alertes_impayes_uid_idx ON app.alertes_impayes(uid);

ALTER TABLE app.echeances_app
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS echeances_app_uid_idx ON app.echeances_app(uid);

-- ── Collectes ────────────────────────────────────────────────────────────────

ALTER TABLE app.collectes_epargne
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS collectes_epargne_uid_idx ON app.collectes_epargne(uid);

ALTER TABLE app.collectes_terrain
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS collectes_terrain_uid_idx ON app.collectes_terrain(uid);

ALTER TABLE app.cycles_collecte
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS cycles_collecte_uid_idx ON app.cycles_collecte(uid);

-- ── Créances & Recouvrement ──────────────────────────────────────────────────

ALTER TABLE app.creances
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS creances_uid_idx ON app.creances(uid);

ALTER TABLE app.dossiers_recouvrement
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS dossiers_recouvrement_uid_idx ON app.dossiers_recouvrement(uid);

ALTER TABLE app.actions_recouvrement
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS actions_recouvrement_uid_idx ON app.actions_recouvrement(uid);

ALTER TABLE app.accords_reechelonnement
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS accords_reechelonnement_uid_idx ON app.accords_reechelonnement(uid);

-- ── KYC ─────────────────────────────────────────────────────────────────────

ALTER TABLE app.kyc_dossiers
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS kyc_dossiers_uid_idx ON app.kyc_dossiers(uid);

ALTER TABLE app.kyc_documents
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS kyc_documents_uid_idx ON app.kyc_documents(uid);

ALTER TABLE app.kyc_verifications
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS kyc_verifications_uid_idx ON app.kyc_verifications(uid);

-- ── RGPD ─────────────────────────────────────────────────────────────────────

ALTER TABLE app.demandes_rgpd
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS demandes_rgpd_uid_idx ON app.demandes_rgpd(uid);

ALTER TABLE app.consentements
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS consentements_uid_idx ON app.consentements(uid);

ALTER TABLE app.etiquettes_dossiers
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS etiquettes_dossiers_uid_idx ON app.etiquettes_dossiers(uid);

-- ── Notifications ────────────────────────────────────────────────────────────

ALTER TABLE app.notifications
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS notifications_uid_idx ON app.notifications(uid);

-- ── Clients & Produits ───────────────────────────────────────────────────────

ALTER TABLE app.clients_informels
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS clients_informels_uid_idx ON app.clients_informels(uid);

ALTER TABLE app.produits_generiques
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS produits_generiques_uid_idx ON app.produits_generiques(uid);

-- ── Violations de données (RGPD art. 22) ─────────────────────────────────────

ALTER TABLE app.violations_donnees
    ADD COLUMN IF NOT EXISTS uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS violations_donnees_uid_idx ON app.violations_donnees(uid);
