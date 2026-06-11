-- V40 : Système de délégation hiérarchique IMF
-- Couvre deux cas :
--   REASSIGNATION_DOSSIER — transfert d'un dossier crédit entre agents
--   DELEGATION_AUTORITE   — délégation temporaire de pouvoir de validation/signature

CREATE TABLE IF NOT EXISTS app.delegations (
    id              BIGSERIAL PRIMARY KEY,
    uid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    imf_id          BIGINT NOT NULL REFERENCES app.imfs(id),
    delegant_id     BIGINT NOT NULL REFERENCES app.utilisateurs(id),
    delegataire_id  BIGINT NOT NULL REFERENCES app.utilisateurs(id),
    type_delegation VARCHAR(30)  NOT NULL,           -- REASSIGNATION_DOSSIER | DELEGATION_AUTORITE
    objet_id        BIGINT,                          -- ID du dossier concerné (REASSIGNATION uniquement)
    objet_type      VARCHAR(50),                     -- 'DOSSIER_CREDIT'
    motif           TEXT,
    role_delegue    VARCHAR(30),                     -- rôle délégué (DELEGATION_AUTORITE)
    montant_seuil   NUMERIC(15,2),                   -- plafond d'autorité délégué (FCFA)
    date_debut      DATE NOT NULL DEFAULT CURRENT_DATE,
    date_fin        DATE,                            -- NULL = sans limite de durée
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delegations_delegant    ON app.delegations(delegant_id);
CREATE INDEX idx_delegations_delegataire ON app.delegations(delegataire_id);
CREATE INDEX idx_delegations_imf_actif   ON app.delegations(imf_id, actif);
CREATE INDEX idx_delegations_objet       ON app.delegations(objet_id) WHERE objet_id IS NOT NULL;
