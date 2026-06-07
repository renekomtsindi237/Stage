-- ============================================================
-- V20 — Profil clients du secteur informel
-- Clients multi-activités, multi-produits agricoles/commerciaux
-- ============================================================

-- ── Profil étendu client informel ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.clients_informels (
    id                      BIGSERIAL PRIMARY KEY,
    imf_id                  BIGINT       NOT NULL REFERENCES app.imf(id),
    client_id_externe       VARCHAR(50)  NOT NULL,          -- référence CBS externe
    nom_complet             VARCHAR(200) NOT NULL,
    telephone_principal     VARCHAR(20),
    telephone_secondaire    VARCHAR(20),
    zone_id                 VARCHAR(20),
    agence_id               BIGINT REFERENCES app.agences(id),
    date_naissance          DATE,
    sexe                    CHAR(1) CHECK (sexe IN ('M','F')),
    -- Activité économique
    secteur_principal       VARCHAR(30)  NOT NULL DEFAULT 'COMMERCE'
                            CHECK (secteur_principal IN (
                                'AGRICOLE','COMMERCE','ARTISANAT','ELEVAGE',
                                'PECHE','TRANSPORT','SERVICES','MIXTE'
                            )),
    sous_secteur            VARCHAR(50),                    -- ex: maraîchage, aviculture, tissage
    annees_experience       SMALLINT,
    revenu_mensuel_estime   NUMERIC(15,2),
    -- Marché principal de vente
    marche_principal        VARCHAR(100),
    frequence_marche        VARCHAR(20)
                            CHECK (frequence_marche IN ('QUOTIDIEN','HEBDOMADAIRE','BIMENSUEL','MENSUEL','OCCASIONNEL')),
    -- Situation sociale
    niveau_education        VARCHAR(20)
                            CHECK (niveau_education IN ('AUCUN','PRIMAIRE','SECONDAIRE','SUPERIEUR')),
    situation_familiale     VARCHAR(20)
                            CHECK (situation_familiale IN ('CELIBATAIRE','MARIE','DIVORCE','VEUF')),
    nombre_personnes_charge SMALLINT,
    -- Géolocalisation domicile/activité
    latitude_activite       NUMERIC(10,7),
    longitude_activite      NUMERIC(10,7),
    adresse_activite        TEXT,
    -- Statut
    actif                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_id, client_id_externe)
);

CREATE INDEX IF NOT EXISTS idx_ci_imf_secteur   ON app.clients_informels(imf_id, secteur_principal);
CREATE INDEX IF NOT EXISTS idx_ci_imf_zone      ON app.clients_informels(imf_id, zone_id);
CREATE INDEX IF NOT EXISTS idx_ci_telephone     ON app.clients_informels(telephone_principal);
COMMENT ON TABLE app.clients_informels IS 'Profil étendu des clients du secteur informel — activités, produits, marché';

-- ── Catalogue de produits génériques ────────────────────────────────────────
-- Produit générique = ce que vend le client (pas un produit financier)
CREATE TABLE IF NOT EXISTS app.produits_generiques (
    id                      BIGSERIAL PRIMARY KEY,
    code_produit            VARCHAR(30)  NOT NULL UNIQUE,
    nom_produit             VARCHAR(100) NOT NULL,
    categorie               VARCHAR(30)  NOT NULL
                            CHECK (categorie IN (
                                'CEREALE','TUBERCULE','LEGUME','FRUIT',
                                'OLEAGINEUX','BETAIL','VOLAILLE','POISSON',
                                'ARTISANAT','TEXTILE','PETROLIER','AUTRE'
                            )),
    sous_categorie          VARCHAR(50),
    unite_mesure_ref        VARCHAR(20) NOT NULL DEFAULT 'KG'
                            CHECK (unite_mesure_ref IN ('KG','LITRE','UNITE','SAC_50KG','SAC_100KG','TONNE','BOTTE','CARTON')),
    saisonnalite            BOOLEAN NOT NULL DEFAULT TRUE,
    mois_saison_haute       INTEGER[],                      -- ex: {11,12,1,2} = nov-fév
    zones_production        VARCHAR[],                      -- ex: {ADAMAOUA, NORD}
    description             TEXT,
    actif                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE app.produits_generiques IS 'Catalogue générique des produits vendus par les clients informels';

-- ── Activités/produits par client ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.client_activites_produits (
    id                      BIGSERIAL PRIMARY KEY,
    client_id               BIGINT NOT NULL REFERENCES app.clients_informels(id) ON DELETE CASCADE,
    produit_id              BIGINT NOT NULL REFERENCES app.produits_generiques(id),
    est_produit_principal   BOOLEAN NOT NULL DEFAULT FALSE,
    volume_habituel         NUMERIC(10,2),
    unite_volume            VARCHAR(20),
    revenu_mensuel_produit  NUMERIC(15,2),
    mois_activite           INTEGER[],                      -- mois actifs dans l'année
    observation             TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, produit_id)
);

CREATE INDEX IF NOT EXISTS idx_cap_client   ON app.client_activites_produits(client_id);
CREATE INDEX IF NOT EXISTS idx_cap_produit  ON app.client_activites_produits(produit_id);
COMMENT ON TABLE app.client_activites_produits IS 'Produits vendus par chaque client informel avec volumes et revenus estimés';

-- Seed produits courants au Cameroun
INSERT INTO app.produits_generiques (code_produit, nom_produit, categorie, sous_categorie, unite_mesure_ref, mois_saison_haute, zones_production) VALUES
    ('MAIS',      'Maïs',           'CEREALE',    'Céréale sèche',   'KG',       '{8,9,10,3,4}',    '{OUEST,SUD_OUEST,ADAMAOUA}'),
    ('MANIOC',    'Manioc',         'TUBERCULE',  'Tubercule frais', 'KG',       NULL,              '{CENTRE,SUD,LITTORAL}'),
    ('PLANTAIN',  'Plantain',       'FRUIT',      'Banane plantain', 'BOTTE',    '{1,2,3,10,11}',   '{SUD_OUEST,LITTORAL,SUD}'),
    ('ARACHIDE',  'Arachide',       'OLEAGINEUX', 'Légumineuse',     'KG',       '{8,9,10}',        '{NORD,ADAMAOUA,OUEST}'),
    ('IGNAME',    'Igname',         'TUBERCULE',  'Tubercule sèche', 'KG',       '{8,9}',           '{ADAMAOUA,NORD_OUEST}'),
    ('TOMATE',    'Tomate',         'LEGUME',     'Légume fruit',    'KG',       '{11,12,1,2}',     '{OUEST,ADAMAOUA}'),
    ('PIMENT',    'Piment',         'LEGUME',     'Condiment',       'KG',       NULL,              '{TOUS}'),
    ('POISSON_S', 'Poisson fumé',   'POISSON',    'Poisson transformé','KG',     NULL,              '{LITTORAL,SUD,EST}'),
    ('POULET',    'Poulet vivant',  'VOLAILLE',   'Aviculture',      'UNITE',    '{12,1,4,8}',      '{TOUS}'),
    ('BOEUF',     'Bœuf sur pied',  'BETAIL',     'Bovin',           'UNITE',    '{11,12,1}',       '{NORD,ADAMAOUA,EXTREME_NORD}'),
    ('HUILE_P',   'Huile de palme', 'OLEAGINEUX', 'Huile végétale',  'LITRE',    '{3,4,5}',         '{SUD_OUEST,LITTORAL,SUD}'),
    ('CAFE_R',    'Café Robusta',   'OLEAGINEUX', 'Culture de rente','KG',       '{11,12,1,2}',     '{SUD_OUEST,LITTORAL}'),
    ('CACAO',     'Cacao',          'OLEAGINEUX', 'Culture de rente','KG',       '{10,11,12,3,4}',  '{SUD_OUEST,CENTRE,SUD,EST}'),
    ('COTON',     'Coton',          'AUTRE',      'Culture de rente','KG',       '{10,11}',         '{NORD,EXTREME_NORD}'),
    ('SORGHO',    'Sorgho',         'CEREALE',    'Céréale sèche',   'KG',       '{10,11}',         '{NORD,EXTREME_NORD,ADAMAOUA}')
ON CONFLICT (code_produit) DO NOTHING;
