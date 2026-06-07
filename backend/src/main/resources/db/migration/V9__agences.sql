-- V9 : Table des agences d'une IMF — gérées par le DSI
CREATE TABLE IF NOT EXISTS app.agences (
    id          BIGSERIAL PRIMARY KEY,
    imf_id      BIGINT NOT NULL REFERENCES app.imf(id) ON DELETE CASCADE,
    nom         VARCHAR(100) NOT NULL,
    ville       VARCHAR(100),
    responsable VARCHAR(100),
    telephone   VARCHAR(20),
    actif       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_agence_imf_nom UNIQUE (imf_id, nom)
);

CREATE INDEX IF NOT EXISTS idx_agences_imf ON app.agences (imf_id);
