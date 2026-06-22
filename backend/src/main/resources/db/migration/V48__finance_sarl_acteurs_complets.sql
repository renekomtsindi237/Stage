-- ============================================================
-- V48 — FINANCE SARL : mise à jour des 4 acteurs
-- ============================================================

DO $$
DECLARE
    v_imf_id   BIGINT;
    v_agt_id   BIGINT;
    v_ag_yde   BIGINT;
    v_ag_dla   BIGINT;
    v_cycle_id BIGINT;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'FINANCE SARL introuvable — V48 ignorée';
        RETURN;
    END IF;

    -- ── 1. Correction email DSI ──────────────────────────────────────────────
    UPDATE app.utilisateurs
    SET email    = 'albanrene77@gmail.com',
        username = 'alban.dsi'
    WHERE imf_id = v_imf_id
      AND role   = 'DSI'
      AND email  = 'dsi@finance-mf.cm';  -- idempotent : skip si email déjà corrigé

    -- ── 2. IDs utiles ───────────────────────────────────────────────────────
    SELECT id INTO v_agt_id  FROM app.utilisateurs WHERE email = 'renekomtsindi99@gmail.com' AND imf_id = v_imf_id LIMIT 1;
    SELECT id INTO v_ag_yde  FROM app.agences WHERE imf_id = v_imf_id AND nom ILIKE '%Yaound%Nlongkak%' LIMIT 1;
    SELECT id INTO v_ag_dla  FROM app.agences WHERE imf_id = v_imf_id AND nom ILIKE '%Douala%' LIMIT 1;
    SELECT id INTO v_cycle_id FROM app.cycles_collecte WHERE imf_id = v_imf_id LIMIT 1;

    IF v_ag_yde IS NULL THEN
        SELECT id INTO v_ag_yde FROM app.agences WHERE imf_id = v_imf_id LIMIT 1;
    END IF;
    IF v_ag_dla IS NULL THEN
        v_ag_dla := v_ag_yde;
    END IF;

    RAISE NOTICE 'V48 — imf=% agt=% agYde=% cycle=%', v_imf_id, v_agt_id, v_ag_yde, v_cycle_id;

    -- ── 3. Collectes du jour (agent dashboard) ──────────────────────────────
    IF v_agt_id IS NOT NULL AND v_cycle_id IS NOT NULL AND v_ag_yde IS NOT NULL THEN
        INSERT INTO app.collectes_epargne
            (uuid_mobile, imf_id, client_id_externe, agence_id, cycle_id, agent_id,
             montant_collecte, date_collecte, canal_paiement, statut, created_at)
        SELECT gen_random_uuid(), v_imf_id, vals.cid, v_ag_yde, v_cycle_id, v_agt_id,
               vals.montant, CURRENT_DATE, 'ESPECES', 'VALIDEE', NOW()
        FROM (VALUES
            ('CLF001', 8500.00::NUMERIC),
            ('CLF002',12000.00),
            ('CLF003', 6500.00),
            ('CLF004',15000.00),
            ('CLF005', 9000.00),
            ('CLF006',11000.00),
            ('CLF007', 7500.00),
            ('CLF008',13500.00)
        ) AS vals(cid, montant)
        WHERE NOT EXISTS (
            SELECT 1 FROM app.collectes_epargne ce
            WHERE ce.imf_id = v_imf_id
              AND ce.client_id_externe = vals.cid
              AND DATE(ce.date_collecte) = CURRENT_DATE
        );
    END IF;

    -- ── 4. Collectes historique 30 jours ────────────────────────────────────
    IF v_agt_id IS NOT NULL AND v_cycle_id IS NOT NULL AND v_ag_yde IS NOT NULL THEN
        INSERT INTO app.collectes_epargne
            (uuid_mobile, imf_id, client_id_externe, agence_id, cycle_id, agent_id,
             montant_collecte, date_collecte, canal_paiement, statut, created_at)
        SELECT gen_random_uuid(), v_imf_id, c.client_id_externe, v_ag_yde, v_cycle_id, v_agt_id,
               (5000 + (random() * 15000))::NUMERIC(12,2),
               CURRENT_DATE - (n || ' days')::INTERVAL,
               'ESPECES', 'VALIDEE', NOW()
        FROM generate_series(1, 30) AS n
        CROSS JOIN (
            SELECT client_id_externe FROM app.clients_informels
            WHERE imf_id = v_imf_id LIMIT 6
        ) AS c
        WHERE NOT EXISTS (
            SELECT 1 FROM app.collectes_epargne ce
            WHERE ce.imf_id = v_imf_id
              AND ce.client_id_externe = c.client_id_externe
              AND DATE(ce.date_collecte) = CURRENT_DATE - (n || ' days')::INTERVAL
        );
    END IF;

    -- ── 5. Dossiers crédit ──────────────────────────────────────────────────
    IF v_agt_id IS NOT NULL AND v_ag_yde IS NOT NULL THEN
        INSERT INTO app.dossiers_credit
            (imf_id, uid, client_id, agence_id, agent_credit_id,
             montant_demande, duree_mois, objet_financement,
             statut, date_soumission, date_decision, created_at)
        SELECT v_imf_id, gen_random_uuid(), vals.cid,
               CASE WHEN vals.ag = 'YDE' THEN v_ag_yde ELSE v_ag_dla END,
               v_agt_id, vals.montant, vals.duree,
               vals.objet, vals.statut,
               NOW() - (vals.j || ' days')::INTERVAL,
               NOW() - ((vals.j - 5) || ' days')::INTERVAL,
               NOW()
        FROM (VALUES
            ('CLF001','YDE', 350000,'Commerce détail',  'DEBLOQUE', 12, 90),
            ('CLF002','YDE', 500000,'Artisanat',        'DEBLOQUE', 18, 85),
            ('CLF003','YDE', 200000,'Agriculture',      'DEBLOQUE',  6, 70),
            ('CLF004','YDE', 650000,'Transport',        'DEBLOQUE', 24, 95),
            ('CLF005','YDE', 280000,'Commerce détail',  'DEBLOQUE',  9, 80),
            ('CLF006','DLA', 420000,'Commerce',         'APPROUVE', 12, 15),
            ('CLF007','DLA', 180000,'Agriculture',      'DEBLOQUE',  6, 60),
            ('CLF008','DLA', 380000,'Pêche',            'DEBLOQUE', 12, 75),
            ('CLF009','YDE', 240000,'Artisanat',        'DEBLOQUE',  9, 65),
            ('CLF010','YDE', 560000,'Services',         'DEBLOQUE', 18, 55),
            ('CLF011','DLA', 310000,'Commerce',         'DEBLOQUE', 12, 50),
            ('CLF012','DLA', 450000,'Services',         'APPROUVE', 15, 12),
            ('CLF016','YDE', 720000,'Transport',        'DEBLOQUE', 24,100),
            ('CLF017','DLA', 890000,'Commerce',         'DEBLOQUE', 24,110),
            ('CLF018','YDE', 340000,'Artisanat',        'DEBLOQUE', 12, 88),
            ('CLF019','DLA', 610000,'Pêche',            'DEBLOQUE', 18, 92),
            ('CLF020','YDE', 480000,'Agriculture',      'DEBLOQUE', 12, 76),
            ('CLF021','YDE', 260000,'Commerce',         'DEBLOQUE', 12,120),
            ('CLF022','YDE', 310000,'Transport',        'DEBLOQUE',  9,115),
            ('CLF023','DLA', 180000,'Agriculture',      'DEBLOQUE',  6,110),
            ('CLF024','YDE', 420000,'Commerce',         'DEBLOQUE', 18,125),
            ('CLF025','DLA', 290000,'Artisanat',        'DEBLOQUE', 12,112),
            ('CLF026','YDE', 350000,'Services',         'DEBLOQUE',  9,118),
            ('CLF027','DLA', 430000,'Pêche',            'DEBLOQUE', 24,122),
            ('CLF028','YDE', 680000,'Transport',        'DEBLOQUE', 24,130),
            ('CLF029','DLA', 540000,'Commerce',         'DEBLOQUE', 18,135),
            ('CLF030','YDE', 390000,'Agriculture',      'DEBLOQUE', 12,140)
        ) AS vals(cid, ag, montant, objet, statut, duree, j)
        WHERE NOT EXISTS (
            SELECT 1 FROM app.dossiers_credit dc
            WHERE dc.imf_id = v_imf_id AND dc.client_id = vals.cid
        );
    END IF;

    -- ── 6. Créances PAR (CLF021-030) ────────────────────────────────────────
    INSERT INTO app.creances
        (imf_id, id_pret_externe, client_id_externe, agence_id,
         montant_initial, montant_impaye, jours_retard, categorie_par,
         statut, date_ouverture_creance, created_at)
    SELECT v_imf_id, dc.uid::text, dc.client_id, dc.agence_id,
           dc.montant_demande,
           ROUND(dc.montant_demande * 0.12)::NUMERIC,
           vals.retard,
           CASE WHEN vals.retard >= 90 THEN 'PAR90'
                WHEN vals.retard >= 60 THEN 'PAR60'
                ELSE 'PAR30' END,
           CASE WHEN vals.retard >= 90 THEN 'MISE_EN_DEMEURE'
                WHEN vals.retard >= 60 THEN 'RECOUVREMENT_AMIABLE'
                ELSE 'ACTIVE' END,
           CURRENT_DATE - (vals.retard || ' days')::INTERVAL,
           NOW()
    FROM (VALUES
        ('CLF021', 38), ('CLF022', 45), ('CLF023', 35),
        ('CLF024', 62), ('CLF025', 41), ('CLF026', 37),
        ('CLF027', 55), ('CLF028', 96), ('CLF029', 96), ('CLF030', 96)
    ) AS vals(cid, retard)
    JOIN app.dossiers_credit dc ON dc.client_id = vals.cid AND dc.imf_id = v_imf_id
    WHERE NOT EXISTS (
        SELECT 1 FROM app.creances cr
        WHERE cr.imf_id = v_imf_id AND cr.client_id_externe = vals.cid
    );

    -- ── 7. KYC dossiers (CLF001-015) ────────────────────────────────────────
    INSERT INTO app.kyc_dossiers
        (imf_id, client_id, nom_client,
         niveau_actuel, niveau_demande, statut,
         score_risque, niveau_risque,
         verificateur_id, observations)
    SELECT v_imf_id, vals.cid,
           COALESCE(
               (SELECT nom_complet FROM app.clients_informels
                WHERE imf_id = v_imf_id AND client_id_externe = vals.cid LIMIT 1),
               'Client ' || vals.cid
           ),
           vals.niveau, vals.niveau, vals.statut,
           vals.score, vals.risque,
           v_agt_id, 'Vérification terrain effectuée'
    FROM (VALUES
        ('CLF001','NIVEAU_2','APPROUVE',      25,'FAIBLE'),
        ('CLF002','NIVEAU_2','APPROUVE',      30,'FAIBLE'),
        ('CLF003','NIVEAU_1','APPROUVE',      20,'FAIBLE'),
        ('CLF004','NIVEAU_2','APPROUVE',      35,'FAIBLE'),
        ('CLF005','NIVEAU_2','APPROUVE',      28,'FAIBLE'),
        ('CLF006','NIVEAU_2','EN_ATTENTE',    55,'MOYEN'),
        ('CLF007','NIVEAU_1','APPROUVE',      22,'FAIBLE'),
        ('CLF008','NIVEAU_2','APPROUVE',      40,'FAIBLE'),
        ('CLF009','NIVEAU_2','EN_ATTENTE',    65,'MOYEN'),
        ('CLF010','NIVEAU_3','APPROUVE',      18,'FAIBLE'),
        ('CLF011','NIVEAU_2','APPROUVE',      32,'FAIBLE'),
        ('CLF012','NIVEAU_1','EN_ATTENTE',    48,'MOYEN'),
        ('CLF013','NIVEAU_2','APPROUVE',      27,'FAIBLE'),
        ('CLF014','NIVEAU_2','APPROUVE',      33,'FAIBLE'),
        ('CLF015','NIVEAU_2','REJETE',        75,'ELEVE')
    ) AS vals(cid, niveau, statut, score, risque)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.kyc_dossiers k
        WHERE k.imf_id = v_imf_id AND k.client_id = vals.cid
    );

    -- ── 8. Consentements RGPD (DSI) ─────────────────────────────────────────
    INSERT INTO app.consentements
        (imf_id, sujet_type, sujet_id, sujet_reference,
         finalite, accorde, date_consentement, canal_collecte, created_at)
    SELECT v_imf_id, 'CLIENT', ci.id, ci.client_id_externe,
           fins.finalite, TRUE,
           NOW() - ((random() * 180)::INT || ' days')::INTERVAL,
           'FORMULAIRE_PAPIER', NOW()
    FROM (
        SELECT id, client_id_externe FROM app.clients_informels
        WHERE imf_id = v_imf_id ORDER BY client_id_externe LIMIT 20
    ) ci
    CROSS JOIN (VALUES
        ('GEOLOCALISATION'),
        ('SCORING_ML'),
        ('RECOUVREMENT')
    ) AS fins(finalite)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.consentements co
        WHERE co.imf_id = v_imf_id
          AND co.sujet_id = ci.id
          AND co.finalite = fins.finalite
    );

    -- ── 9. Snapshots KPI (courbe directeur) ─────────────────────────────────
    IF v_ag_yde IS NOT NULL THEN
        INSERT INTO app.kpi_collecte_snapshots
            (imf_id, agence_id, date_calcul, periode,
             taux_ponctualite_pct, taux_rejet_pct,
             montant_total, nb_clients_uniques, created_at)
        SELECT v_imf_id, v_ag_yde,
               CURRENT_DATE - (n || ' days')::INTERVAL,
               'QUOTIDIEN',
               0.93 - (n * 0.001),
               0.021 + (n * 0.0005),
               (85000 + n * 500)::NUMERIC,
               28,
               NOW()
        FROM generate_series(1, 15) AS n
        WHERE NOT EXISTS (
            SELECT 1 FROM app.kpi_collecte_snapshots k
            WHERE k.imf_id = v_imf_id
              AND k.agence_id = v_ag_yde
              AND DATE(k.date_calcul) = CURRENT_DATE - (n || ' days')::INTERVAL
        );
    END IF;

    RAISE NOTICE 'V48 OK — données FINANCE SARL complètes pour les 4 acteurs';
END $$;
