-- ============================================================
-- V21 — Données externes : prix produits, facteurs macro,
--        météo, événements terrain
-- Modèle générique prix_produit_x — aucun produit hardcodé
-- ============================================================

-- ── Séries temporelles prix produits (générique) ────────────────────────────
CREATE TABLE IF NOT EXISTS app.prix_produits (
    id                  BIGSERIAL PRIMARY KEY,
    produit_id          BIGINT       NOT NULL REFERENCES app.produits_generiques(id),
    zone_id             VARCHAR(20)  NOT NULL,              -- zone géographique
    date_prix           DATE         NOT NULL,
    prix_unitaire       NUMERIC(12,4) NOT NULL CHECK (prix_unitaire > 0),
    unite_mesure        VARCHAR(20)  NOT NULL,              -- peut différer de l'unité ref
    facteur_conversion  NUMERIC(10,4) NOT NULL DEFAULT 1,  -- vers unité_ref
    prix_min            NUMERIC(12,4),
    prix_max            NUMERIC(12,4),
    source              VARCHAR(30)  NOT NULL DEFAULT 'AGENT_TERRAIN'
                        CHECK (source IN (
                            'AGENT_TERRAIN','MINCOMMERCE','ANSP','BEAC',
                            'FAO','WORLD_BANK','SCRAP_WEB','PARTENAIRE'
                        )),
    fiabilite           SMALLINT DEFAULT 3 CHECK (fiabilite BETWEEN 1 AND 5),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (produit_id, zone_id, date_prix, source)
);

CREATE INDEX IF NOT EXISTS idx_pp_produit_date  ON app.prix_produits(produit_id, date_prix);
CREATE INDEX IF NOT EXISTS idx_pp_zone_date     ON app.prix_produits(zone_id, date_prix);
CREATE INDEX IF NOT EXISTS idx_pp_source        ON app.prix_produits(source);
COMMENT ON TABLE app.prix_produits IS 'Prix marché des produits — séries temporelles, toutes sources, modèle générique';

-- ── Indicateurs macroéconomiques ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.facteurs_macro (
    id                  BIGSERIAL PRIMARY KEY,
    indicateur          VARCHAR(50)  NOT NULL
                        CHECK (indicateur IN (
                            'TAUX_INFLATION_MENSUEL',
                            'TAUX_INFLATION_ANNUEL',
                            'TAUX_DIRECTEUR_BEAC',
                            'TAUX_CHOMAGE',
                            'PIB_MENSUEL_FCFA',
                            'COURS_EUR_XAF',
                            'COURS_USD_XAF',
                            'INDICE_PRIX_CONSOMMATION',
                            'INDICE_PRODUCTION_AGRICOLE',
                            'REMISES_DIASPORA_MILLIONS',
                            'BALANCE_PAIEMENTS',
                            'DETTE_PUBLIQUE_PIB_PCT'
                        )),
    valeur              NUMERIC(18,6) NOT NULL,
    date_observation    DATE         NOT NULL,
    periode             VARCHAR(20)  NOT NULL DEFAULT 'MENSUEL'
                        CHECK (periode IN ('QUOTIDIEN','MENSUEL','TRIMESTRIEL','ANNUEL')),
    source              VARCHAR(30)  NOT NULL DEFAULT 'BEAC'
                        CHECK (source IN ('BEAC','INS_CAMEROUN','FMI','BANQUE_MONDIALE','CEDEAO','AGENT_TERRAIN')),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (indicateur, date_observation)
);

CREATE INDEX IF NOT EXISTS idx_fm_indicateur_date ON app.facteurs_macro(indicateur, date_observation);
COMMENT ON TABLE app.facteurs_macro IS 'Indicateurs macroéconomiques (BEAC, INS) — séries temporelles pour features ML';

-- ── Données météorologiques par zone ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.donnees_meteo (
    id                  BIGSERIAL PRIMARY KEY,
    zone_id             VARCHAR(20) NOT NULL,
    date_observation    DATE        NOT NULL,
    temperature_min     NUMERIC(5,2),
    temperature_max     NUMERIC(5,2),
    temperature_moy     NUMERIC(5,2),
    precipitation_mm    NUMERIC(8,2) DEFAULT 0,
    humidite_pct        NUMERIC(5,2),
    indice_secheresse   VARCHAR(20) DEFAULT 'NORMAL'
                        CHECK (indice_secheresse IN (
                            'NORMAL','SECHERESSE_LEGERE','SECHERESSE_MODEREE',
                            'SECHERESSE_SEVERE','INONDATION','INONDATION_SEVERE'
                        )),
    source              VARCHAR(30) NOT NULL DEFAULT 'METEOCAM'
                        CHECK (source IN ('METEOCAM','NASA_POWER','OPEN_METEO','AGENT_TERRAIN')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (zone_id, date_observation)
);

CREATE INDEX IF NOT EXISTS idx_meteo_zone_date ON app.donnees_meteo(zone_id, date_observation);
CREATE INDEX IF NOT EXISTS idx_meteo_indice     ON app.donnees_meteo(indice_secheresse);
COMMENT ON TABLE app.donnees_meteo IS 'Données météo par zone géographique — impact sur collecte agricole';

-- ── Événements extérieurs perturbateurs ou facilitants ──────────────────────
CREATE TABLE IF NOT EXISTS app.evenements_exterieurs (
    id                  BIGSERIAL PRIMARY KEY,
    date_debut          DATE        NOT NULL,
    date_fin            DATE,
    type_evenement      VARCHAR(40) NOT NULL
                        CHECK (type_evenement IN (
                            'FETE_NATIONALE','FOIRE_LOCALE','MARCHE_SPECIAL',
                            'ELECTION','GREVE_GENERALE','GREVE_TRANSPORT',
                            'CATASTROPHE_NATURELLE','EPIDEMIE','INSECURITE',
                            'RUPTURE_CARBURANT','HAUSSE_PRIX_CARBURANT',
                            'HAUSSE_PRIX_ALIMENTAIRE','DEVALUATION_MONNAIE',
                            'FERMETURE_FRONTIERE','RETRAIT_MOBILE_MONEY',
                            'FESTIVAL_CULTUREL','AUTRES'
                        )),
    nom_evenement       VARCHAR(200) NOT NULL,
    zone_id             VARCHAR(20),                        -- null = national
    description         TEXT,
    impact_collecte     VARCHAR(10) DEFAULT 'NEUTRE'
                        CHECK (impact_collecte IN ('POSITIF','NEGATIF','NEUTRE')),
    impact_estime_pct   NUMERIC(5,2),                      -- % d'impact estimé sur collecte
    source              VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evt_date     ON app.evenements_exterieurs(date_debut, date_fin);
CREATE INDEX IF NOT EXISTS idx_evt_zone     ON app.evenements_exterieurs(zone_id);
CREATE INDEX IF NOT EXISTS idx_evt_type     ON app.evenements_exterieurs(type_evenement);
COMMENT ON TABLE app.evenements_exterieurs IS 'Événements externes affectant les collectes (météo, politique, économique)';

-- ── Indicateurs de marché local par zone ────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.marches_locaux (
    id                  BIGSERIAL PRIMARY KEY,
    zone_id             VARCHAR(20)  NOT NULL,
    nom_marche          VARCHAR(100) NOT NULL,
    type_marche         VARCHAR(20)  NOT NULL
                        CHECK (type_marche IN ('HEBDOMADAIRE','QUOTIDIEN','PERIODIQUE')),
    jours_marche        INTEGER[],                          -- {1,4} = lundi, jeudi
    latitude            NUMERIC(10,7),
    longitude           NUMERIC(10,7),
    rayon_influence_km  NUMERIC(6,1),
    nb_commercants_estim INTEGER,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_marche_zone ON app.marches_locaux(zone_id);
COMMENT ON TABLE app.marches_locaux IS 'Marchés locaux — fréquence et influence géographique sur les flux de collecte';
