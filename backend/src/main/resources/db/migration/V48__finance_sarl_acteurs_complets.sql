-- ============================================================
-- V48 — FINANCE SARL : mise à jour des 4 acteurs
--
--  · DSI      : albanrene77@gmail.com       (email corrigé)
--  · Directeur: rene.komtsindi@saintjeaningenieur.org
--  · Analyste : renekomtsindi559@gmail.com
--  · Agent    : renekomtsindi99@gmail.com
--
-- Données complémentaires pour chaque interface :
--  · Consentements RGPD (DSI)
--  · Collectes du jour pour agent (dashboard)
--  · Dossiers crédit supplémentaires (directeur / agent)
--  · KYC clients CLF001-010 (directeur / DSI)
-- ============================================================

DO $$
DECLARE
    v_imf_id    BIGINT;
    v_dsi_id    BIGINT;
    v_dir_id    BIGINT;
    v_anl_id    BIGINT;
    v_agt_id    BIGINT;
    v_ag_yde    BIGINT;
    v_ag_dla    BIGINT;
    v_cycle_id  BIGINT;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'FINANCE SARL introuvable — V48 ignorée';
        RETURN;
    END IF;

    -- ── 1. Correction email DSI ───────────────────────────────────────────────
    UPDATE app.utilisateurs
    SET email    = 'albanrene77@gmail.com',
        username = 'alban.dsi'
    WHERE imf_id = v_imf_id
      AND role   = 'DSI'
      AND email IN ('dsi@finance-mf.cm', 'albanrene77@gmail.com');

    -- ── 2. Récupération des IDs utilisateurs ──────────────────────────────────
    SELECT id INTO v_dsi_id FROM app.utilisateurs WHERE email = 'albanrene77@gmail.com'            AND imf_id = v_imf_id LIMIT 1;
    SELECT id INTO v_dir_id FROM app.utilisateurs WHERE email = 'rene.komtsindi@saintjeaningenieur.org' AND imf_id = v_imf_id LIMIT 1;
    SELECT id INTO v_anl_id FROM app.utilisateurs WHERE email = 'renekomtsindi559@gmail.com'       AND imf_id = v_imf_id LIMIT 1;
    SELECT id INTO v_agt_id FROM app.utilisateurs WHERE email = 'renekomtsindi99@gmail.com'        AND imf_id = v_imf_id LIMIT 1;

    SELECT id INTO v_ag_yde FROM app.agences WHERE imf_id = v_imf_id AND nom ILIKE '%Yaoundé%Nlongkak%' LIMIT 1;
    SELECT id INTO v_ag_dla FROM app.agences WHERE imf_id = v_imf_id AND nom ILIKE '%Douala%' LIMIT 1;
    SELECT id INTO v_cycle_id FROM app.cycles_collecte WHERE imf_id = v_imf_id LIMIT 1;

    RAISE NOTICE 'V48 — imf=% dsi=% dir=% anl=% agt=%', v_imf_id, v_dsi_id, v_dir_id, v_anl_id, v_agt_id;

    -- ── 3. Collectes du jour pour le dashboard agent ──────────────────────────
    INSERT INTO app.collectes_epargne
        (imf_id, client_id_externe, agence_id, agent_collecteur_id,
         montant_collecte, date_collecte, cycle_id, statut, canal_collecte,
         latitude, longitude, created_at)
    SELECT v_imf_id, vals.client_id, v_ag_yde, v_agt_id,
           vals.montant, CURRENT_DATE, v_cycle_id, 'VALIDE', 'TERRAIN',
           3.870000 + (random() * 0.01), 11.518000 + (random() * 0.01), NOW()
    FROM (VALUES
        ('CLF001', 8500.00::NUMERIC),
        ('CLF002', 12000.00),
        ('CLF003', 6500.00),
        ('CLF004', 15000.00),
        ('CLF005', 9000.00),
        ('CLF006', 11000.00),
        ('CLF007', 7500.00),
        ('CLF008', 13500.00)
    ) AS vals(client_id, montant)
    WHERE v_agt_id IS NOT NULL
      AND v_cycle_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM app.collectes_epargne ce
          WHERE ce.imf_id = v_imf_id
            AND ce.client_id_externe = vals.client_id
            AND DATE(ce.date_collecte) = CURRENT_DATE
      );

    -- ── 4. Collectes historique — 30 derniers jours (courbe évolution) ────────
    INSERT INTO app.collectes_epargne
        (imf_id, client_id_externe, agence_id, agent_collecteur_id,
         montant_collecte, date_collecte, cycle_id, statut, canal_collecte, created_at)
    SELECT v_imf_id, c.client_id_externe, v_ag_yde, v_agt_id,
           (5000 + (random() * 15000))::NUMERIC(12,2),
           CURRENT_DATE - (n || ' days')::INTERVAL,
           v_cycle_id, 'VALIDE', 'TERRAIN', NOW()
    FROM generate_series(1, 30) AS n
    CROSS JOIN (
        SELECT client_id_externe FROM app.clients_informels
        WHERE imf_id = v_imf_id LIMIT 6
    ) AS c
    WHERE v_agt_id IS NOT NULL AND v_cycle_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM app.collectes_epargne ce
          WHERE ce.imf_id = v_imf_id
            AND ce.client_id_externe = c.client_id_externe
            AND DATE(ce.date_collecte) = CURRENT_DATE - (n || ' days')::INTERVAL
      );

    -- ── 5. KYC dossiers — clients CLF001-CLF015 (directeur + DSI) ───────────
    INSERT INTO app.kyc_dossiers
        (imf_id, client_id_externe, niveau_kyc, statut,
         score_risque, niveau_risque, date_creation, date_mise_a_jour,
         agent_verificateur_id, notes_verificateur)
    SELECT v_imf_id, vals.cid, vals.niveau, vals.statut,
           vals.score, vals.risque,
           NOW() - (vals.jours || ' days')::INTERVAL,
           NOW() - (vals.jours/2 || ' days')::INTERVAL,
           v_agt_id, 'Vérification terrain effectuée'
    FROM (VALUES
        ('CLF001','NIVEAU_2','APPROUVE',      25,'FAIBLE',   45),
        ('CLF002','NIVEAU_2','APPROUVE',      30,'FAIBLE',   40),
        ('CLF003','NIVEAU_1','APPROUVE',      20,'FAIBLE',   50),
        ('CLF004','NIVEAU_2','APPROUVE',      35,'FAIBLE',   38),
        ('CLF005','NIVEAU_2','APPROUVE',      28,'FAIBLE',   42),
        ('CLF006','NIVEAU_2','EN_ATTENTE',    55,'MOYEN',    10),
        ('CLF007','NIVEAU_1','APPROUVE',      22,'FAIBLE',   35),
        ('CLF008','NIVEAU_2','APPROUVE',      40,'FAIBLE',   30),
        ('CLF009','NIVEAU_2','COMPLEMENT_REQUIS', 65,'MOYEN', 7),
        ('CLF010','NIVEAU_3','APPROUVE',      18,'FAIBLE',   60),
        ('CLF011','NIVEAU_2','APPROUVE',      32,'FAIBLE',   25),
        ('CLF012','NIVEAU_1','EN_ATTENTE',    48,'MOYEN',     5),
        ('CLF013','NIVEAU_2','APPROUVE',      27,'FAIBLE',   20),
        ('CLF014','NIVEAU_2','APPROUVE',      33,'FAIBLE',   15),
        ('CLF015','NIVEAU_2','REJETE',        75,'ELEVE',    90)
    ) AS vals(cid, niveau, statut, score, risque, jours)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.kyc_dossiers k
        WHERE k.imf_id = v_imf_id AND k.client_id_externe = vals.cid
    );

    -- ── 6. Consentements RGPD — clients CLF001-CLF020 (DSI) ─────────────────
    INSERT INTO app.consentements
        (imf_id, client_id_externe, type_traitement, accorde,
         date_consentement, canal_recueil, created_at)
    SELECT v_imf_id, c.client_id_externe, types.type_traitement, TRUE,
           NOW() - (random() * 180)::INT * INTERVAL '1 day',
           'PAPIER', NOW()
    FROM (
        SELECT client_id_externe FROM app.clients_informels
        WHERE imf_id = v_imf_id ORDER BY client_id_externe LIMIT 20
    ) c
    CROSS JOIN (VALUES
        ('TRAITEMENT_CREDIT'),
        ('GEOLOCALISATION'),
        ('ANALYSE_RISQUE')
    ) AS types(type_traitement)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.consentements co
        WHERE co.imf_id = v_imf_id
          AND co.client_id_externe = c.client_id_externe
          AND co.type_traitement = types.type_traitement
    );

    -- ── 7. Dossiers crédit — pour KPI directeur (encours réel) ───────────────
    INSERT INTO app.dossiers_credit
        (imf_id, uid, client_id_externe, agence_id, agent_id,
         montant_demande, duree_mois, taux_interet_mensuel,
         objet_credit, statut, date_soumission, date_decision, created_at)
    SELECT v_imf_id, gen_random_uuid(), vals.cid,
           CASE WHEN vals.agence = 'YDE' THEN v_ag_yde ELSE v_ag_dla END,
           v_agt_id, vals.montant, vals.duree, 2.5,
           vals.objet, vals.statut,
           NOW() - (vals.jours || ' days')::INTERVAL,
           NOW() - ((vals.jours - 5) || ' days')::INTERVAL,
           NOW()
    FROM (VALUES
        ('CLF001','YDE', 350000,'COMMERCE','EN_REMBOURSEMENT', 12, 90),
        ('CLF002','YDE', 500000,'ARTISANAT','EN_REMBOURSEMENT',18, 85),
        ('CLF003','YDE', 200000,'AGRICOLE','DEBLOQUE',          6, 70),
        ('CLF004','YDE', 650000,'TRANSPORT','EN_REMBOURSEMENT', 24, 95),
        ('CLF005','YDE', 280000,'COMMERCE','EN_REMBOURSEMENT',  9, 80),
        ('CLF006','DLA', 420000,'COMMERCE','APPROUVE',         12, 15),
        ('CLF007','DLA', 180000,'AGRICOLE','EN_REMBOURSEMENT',  6, 60),
        ('CLF008','DLA', 380000,'PECHE','EN_REMBOURSEMENT',    12, 75),
        ('CLF009','YDE', 240000,'ARTISANAT','EN_REMBOURSEMENT', 9, 65),
        ('CLF010','YDE', 560000,'SERVICES','DEBLOQUE',         18, 55),
        ('CLF011','DLA', 310000,'COMMERCE','EN_REMBOURSEMENT', 12, 50),
        ('CLF012','DLA', 450000,'SERVICES','APPROUVE',         15, 12),
        ('CLF016','YDE', 720000,'TRANSPORT','EN_REMBOURSEMENT',24, 100),
        ('CLF017','DLA', 890000,'COMMERCE','EN_REMBOURSEMENT', 24, 110),
        ('CLF018','YDE', 340000,'ARTISANAT','EN_REMBOURSEMENT',12, 88),
        ('CLF019','DLA', 610000,'PECHE','EN_REMBOURSEMENT',    18, 92),
        ('CLF020','YDE', 480000,'AGRICOLE','EN_REMBOURSEMENT', 12, 76)
    ) AS vals(cid, agence, montant, objet, statut, duree, jours)
    WHERE v_ag_yde IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM app.dossiers_credit dc
          WHERE dc.imf_id = v_imf_id AND dc.client_id_externe = vals.cid
      );

    -- ── 8. Créances (retards) pour PAR directeur ─────────────────────────────
    INSERT INTO app.creances
        (imf_id, id_pret_externe, client_id_externe, agence_id,
         montant_initial, montant_impaye, jours_retard,
         statut, date_echeance, created_at)
    SELECT v_imf_id, dc.uid::text, dc.client_id_externe, dc.agence_id,
           dc.montant_demande,
           ROUND(dc.montant_demande * 0.12)::NUMERIC,
           vals.retard,
           CASE WHEN vals.retard > 90 THEN 'COMPROMISE'
                WHEN vals.retard > 30 THEN 'EN_SOUFFRANCE'
                ELSE 'NORMALE' END,
           CURRENT_DATE - (vals.retard || ' days')::INTERVAL,
           NOW()
    FROM (VALUES
        ('CLF021', 38), ('CLF022', 45), ('CLF023', 35),
        ('CLF024', 62), ('CLF025', 41), ('CLF026', 37),
        ('CLF027', 55), ('CLF028', 96), ('CLF029', 96), ('CLF030', 96)
    ) AS vals(cid, retard)
    JOIN app.dossiers_credit dc ON dc.client_id_externe = vals.cid AND dc.imf_id = v_imf_id
    WHERE NOT EXISTS (
        SELECT 1 FROM app.creances cr
        WHERE cr.imf_id = v_imf_id AND cr.client_id_externe = vals.cid
    );

    -- ── 9. Snapshots KPI (courbe évolution PAR directeur) ────────────────────
    INSERT INTO app.kpi_collecte_snapshots
        (imf_id, agence_id, date_calcul,
         taux_ponctualite_pct, taux_rejet_pct,
         montant_collecte_total, nb_clients_actifs, created_at)
    SELECT v_imf_id, v_ag_yde,
           CURRENT_DATE - (n || ' days')::INTERVAL,
           0.93 - (n * 0.001),
           0.021 + (n * 0.0005),
           (85000 + n * 500)::NUMERIC,
           28,
           NOW()
    FROM generate_series(1, 15) AS n
    WHERE v_ag_yde IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM app.kpi_collecte_snapshots k
          WHERE k.imf_id = v_imf_id
            AND DATE(k.date_calcul) = CURRENT_DATE - (n || ' days')::INTERVAL
      );

    RAISE NOTICE 'V48 OK — DSI email=albanrene77@gmail.com, collectes + KYC + dossiers crédit insérés';
END $$;
