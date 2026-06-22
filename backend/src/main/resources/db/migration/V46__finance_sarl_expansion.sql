-- ============================================================
-- V46 — FINANCE SARL : expansion complète
--   · DIRECTEUR : rene.komtsindi@saintjeaningenieur.org
--   · ANALYSTE  : renekomtsindi559@gmail.com
--   · 3 nouvelles agences Cameroun (YDE Centre, Bafoussam, Garoua)
--   · 3 nouveaux agents terrain géolocalisés
--   · Historique GPS (app.positions_agents)
--   · KYC dossiers pour les 30 clients FINANCE SARL
--   · Run ML MCRS v1.2.0 + features + scores + SHAP
--   · Alertes prédictives ML (5 alertes)
--   · Alertes opérationnelles (5 alertes)
--   · Alertes système (4 nouvelles)
--   · KPI snapshots mensuels 2025 (2 agences) + 2026 (3 agences)
--   · Benchmarks trimestriels inter-agences 2025
--   · Collectes 2026 Jan–Jun pour les 3 nouveaux agents
-- ============================================================

DO $$
DECLARE
    v_imf_id     BIGINT;
    v_ag_yde     BIGINT;
    v_ag_dla     BIGINT;
    v_ag_yde2    BIGINT;
    v_ag_baf     BIGINT;
    v_ag_gar     BIGINT;
    v_agent_rene BIGINT;
    v_dsi        BIGINT;
    v_dir_new    BIGINT;
    v_analyste   BIGINT;
    v_agent2     BIGINT;
    v_agent3     BIGINT;
    v_agent4     BIGINT;
    v_cycle_yde  BIGINT;
    v_cycle_yde2 BIGINT;
    v_cycle_baf  BIGINT;
    v_cycle_gar  BIGINT;
    v_run_id     BIGINT;
    v_feat_id    BIGINT;
    v_score_id   BIGINT;
    v_mois       INT;
    v_n          INT;
    v_c          RECORD;

BEGIN
    -- ── Fix schéma : niveau VARCHAR(10) trop court pour 'AVERTISSEMENT' (13 c) ─
    ALTER TABLE app.alertes_operationnelles ALTER COLUMN niveau TYPE VARCHAR(20);

    -- ── Résolution identifiants existants ─────────────────────────────────────
    SELECT id INTO v_imf_id     FROM app.imf           WHERE code = 'FINANCE';
    SELECT id INTO v_ag_yde     FROM app.agences        WHERE imf_id = v_imf_id AND nom = 'Agence Yaoundé Nlongkak';
    SELECT id INTO v_ag_dla     FROM app.agences        WHERE imf_id = v_imf_id AND nom = 'Agence Douala Bassa';
    SELECT id INTO v_agent_rene FROM app.utilisateurs   WHERE email  = 'renekomtsindi99@gmail.com';
    SELECT id INTO v_dsi        FROM app.utilisateurs   WHERE email  = 'dsi@finance-mf.cm';
    SELECT id INTO v_cycle_yde  FROM app.cycles_collecte WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Mensuel Finance 2025';

    -- ── 1. 3 nouvelles agences ────────────────────────────────────────────────
    INSERT INTO app.agences (imf_id, nom, ville, responsable, telephone, actif)
    VALUES
        (v_imf_id, 'Agence Yaoundé Centre-Plateau', 'Yaoundé',   'Mme Isabelle NKENG',     '+237 699 20 30 40', TRUE),
        (v_imf_id, 'Agence Bafoussam Marché A',     'Bafoussam', 'M. Jean-Pierre KENGNE',  '+237 677 30 40 50', TRUE),
        (v_imf_id, 'Agence Garoua Centre',          'Garoua',    'M. Ibrahim ALIOUM',      '+237 655 40 50 60', TRUE)
    ON CONFLICT ON CONSTRAINT uq_agence_imf_nom DO NOTHING;

    SELECT id INTO v_ag_yde2 FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Yaoundé Centre-Plateau';
    SELECT id INTO v_ag_baf  FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Bafoussam Marché A';
    SELECT id INTO v_ag_gar  FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Garoua Centre';

    -- ── 2. Nouveaux utilisateurs ──────────────────────────────────────────────
    INSERT INTO app.utilisateurs (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'rene.directeur', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'DIRECTEUR', 'rene.komtsindi@saintjeaningenieur.org', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'rene.directeur' OR email = 'rene.komtsindi@saintjeaningenieur.org'
    );

    INSERT INTO app.utilisateurs (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'rene.analyste', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'ANALYSTE', 'renekomtsindi559@gmail.com', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'rene.analyste' OR email = 'renekomtsindi559@gmail.com'
    );

    INSERT INTO app.utilisateurs (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'agent.ydec', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.ydec@finance-mf.cm', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'agent.ydec' OR email = 'agent.ydec@finance-mf.cm'
    );

    INSERT INTO app.utilisateurs (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'agent.baf', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.baf@finance-mf.cm', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'agent.baf' OR email = 'agent.baf@finance-mf.cm'
    );

    INSERT INTO app.utilisateurs (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'agent.gar', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.gar@finance-mf.cm', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'agent.gar' OR email = 'agent.gar@finance-mf.cm'
    );

    SELECT id INTO v_dir_new  FROM app.utilisateurs WHERE email = 'rene.komtsindi@saintjeaningenieur.org';
    SELECT id INTO v_analyste FROM app.utilisateurs WHERE email = 'renekomtsindi559@gmail.com';
    SELECT id INTO v_agent2   FROM app.utilisateurs WHERE email = 'agent.ydec@finance-mf.cm';
    SELECT id INTO v_agent3   FROM app.utilisateurs WHERE email = 'agent.baf@finance-mf.cm';
    SELECT id INTO v_agent4   FROM app.utilisateurs WHERE email = 'agent.gar@finance-mf.cm';

    -- ── 3. Dernière position GPS sur app.utilisateurs ─────────────────────────
    UPDATE app.utilisateurs SET
        latitude = 3.87000, longitude = 11.51800, precision_gps_m = 8.5,
        derniere_position_at = NOW() - INTERVAL '2 hours', position_active = TRUE
    WHERE id = v_agent_rene AND latitude IS NULL;

    UPDATE app.utilisateurs SET
        latitude = 3.86500, longitude = 11.51750, precision_gps_m = 6.2,
        derniere_position_at = NOW() - INTERVAL '45 minutes', position_active = TRUE
    WHERE id = v_agent2;

    UPDATE app.utilisateurs SET
        latitude = 5.47900, longitude = 10.41620, precision_gps_m = 12.1,
        derniere_position_at = NOW() - INTERVAL '80 minutes', position_active = TRUE
    WHERE id = v_agent3;

    UPDATE app.utilisateurs SET
        latitude = 9.30120, longitude = 13.39680, precision_gps_m = 18.4,
        derniere_position_at = NOW() - INTERVAL '3 hours', position_active = TRUE
    WHERE id = v_agent4;

    -- ── 4. Historique GPS — 20 positions par agent (30 derniers jours) ────────
    -- Agent Réné — tournée Nlongkak / Mvog-Ada (coordonnées YDE)
    FOR v_n IN 1..20 LOOP
        IF NOT EXISTS (
            SELECT 1 FROM app.positions_agents
            WHERE agent_id = v_agent_rene
              AND captured_at::DATE = (NOW() - ((30 - v_n) || ' days')::INTERVAL)::DATE
        ) THEN
            INSERT INTO app.positions_agents
                (imf_id, agent_id, latitude, longitude, precision_gps_m,
                 vitesse_kmh, source, captured_at)
            VALUES (
                v_imf_id, v_agent_rene,
                3.87000 + (sin(v_n * 0.7) * 0.0030),
                11.51800 + (cos(v_n * 0.5) * 0.0040),
                6.0 + (v_n % 5),
                CASE WHEN v_n % 4 = 0 THEN 12.5 ELSE 3.2 END,
                CASE WHEN v_n % 5 = 0 THEN 'COLLECTE' ELSE 'MOBILE' END,
                NOW() - ((30 - v_n) || ' days')::INTERVAL
            );
        END IF;
    END LOOP;

    -- Agent YDE Centre — Plateau / Bastos
    FOR v_n IN 1..20 LOOP
        IF NOT EXISTS (
            SELECT 1 FROM app.positions_agents
            WHERE agent_id = v_agent2
              AND captured_at::DATE = (NOW() - ((30 - v_n) || ' days')::INTERVAL)::DATE
        ) THEN
            INSERT INTO app.positions_agents
                (imf_id, agent_id, latitude, longitude, precision_gps_m,
                 vitesse_kmh, source, captured_at)
            VALUES (
                v_imf_id, v_agent2,
                3.86500 + (sin(v_n * 0.8) * 0.0025),
                11.51750 + (cos(v_n * 0.6) * 0.0035),
                5.0 + (v_n % 4),
                CASE WHEN v_n % 3 = 0 THEN 14.0 ELSE 2.8 END,
                CASE WHEN v_n % 4 = 0 THEN 'COLLECTE' ELSE 'MOBILE' END,
                NOW() - ((30 - v_n) || ' days')::INTERVAL
            );
        END IF;
    END LOOP;

    -- Agent Bafoussam — Marché A
    FOR v_n IN 1..20 LOOP
        IF NOT EXISTS (
            SELECT 1 FROM app.positions_agents
            WHERE agent_id = v_agent3
              AND captured_at::DATE = (NOW() - ((30 - v_n) || ' days')::INTERVAL)::DATE
        ) THEN
            INSERT INTO app.positions_agents
                (imf_id, agent_id, latitude, longitude, precision_gps_m,
                 vitesse_kmh, source, captured_at)
            VALUES (
                v_imf_id, v_agent3,
                5.47900 + (sin(v_n * 0.9) * 0.0050),
                10.41620 + (cos(v_n * 0.7) * 0.0060),
                10.0 + (v_n % 8),
                CASE WHEN v_n % 5 = 0 THEN 18.0 ELSE 4.1 END,
                CASE WHEN v_n % 6 = 0 THEN 'COLLECTE' ELSE 'MOBILE' END,
                NOW() - ((30 - v_n) || ' days')::INTERVAL
            );
        END IF;
    END LOOP;

    -- Agent Garoua — Centre
    FOR v_n IN 1..20 LOOP
        IF NOT EXISTS (
            SELECT 1 FROM app.positions_agents
            WHERE agent_id = v_agent4
              AND captured_at::DATE = (NOW() - ((30 - v_n) || ' days')::INTERVAL)::DATE
        ) THEN
            INSERT INTO app.positions_agents
                (imf_id, agent_id, latitude, longitude, precision_gps_m,
                 vitesse_kmh, source, captured_at)
            VALUES (
                v_imf_id, v_agent4,
                9.30120 + (sin(v_n * 0.6) * 0.0040),
                13.39680 + (cos(v_n * 0.8) * 0.0055),
                15.0 + (v_n % 6),
                CASE WHEN v_n % 6 = 0 THEN 22.0 ELSE 5.3 END,
                CASE WHEN v_n % 7 = 0 THEN 'COLLECTE' ELSE 'MOBILE' END,
                NOW() - ((30 - v_n) || ' days')::INTERVAL
            );
        END IF;
    END LOOP;

    -- ── 5. Cycles collecte pour les 3 nouvelles agences ──────────────────────
    INSERT INTO app.cycles_collecte
        (imf_id, agence_id, nom_cycle, periodicite, date_debut, objectif_montant, objectif_nb_transactions, actif)
    VALUES
        (v_imf_id, v_ag_yde2, 'Cycle Finance YDE Centre 2026', 'MENSUEL', '2026-01-01', 200000000, 400, TRUE),
        (v_imf_id, v_ag_baf,  'Cycle Finance Bafoussam 2026',  'MENSUEL', '2026-01-01', 150000000, 300, TRUE),
        (v_imf_id, v_ag_gar,  'Cycle Finance Garoua 2026',     'MENSUEL', '2026-01-01', 100000000, 200, TRUE)
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_cycle_yde2 FROM app.cycles_collecte WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Finance YDE Centre 2026';
    SELECT id INTO v_cycle_baf  FROM app.cycles_collecte WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Finance Bafoussam 2026';
    SELECT id INTO v_cycle_gar  FROM app.cycles_collecte WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Finance Garoua 2026';

    -- ── 6. Collectes 2026 (Jan–Jun) pour les 3 nouveaux agents ───────────────
    -- Agent YDE Centre → CLF001–CLF010 (bon payeurs YDE)
    FOR v_mois IN 1..6 LOOP
        INSERT INTO app.collectes_epargne
            (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
             client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
        SELECT
            gen_random_uuid(), v_imf_id, v_ag_yde2, v_cycle_yde2, v_agent2,
            'CLF' || LPAD(n::TEXT, 3, '0'),
            (22000 + ((n * 4321 + v_mois * 1111) % 55000))::NUMERIC,
            MAKE_DATE(2026, v_mois, LEAST(28, 7 + (n * 6 % 18))),
            CASE WHEN (n + v_mois) % 3 = 0 THEN 'MTN'
                 WHEN (n + v_mois) % 3 = 1 THEN 'ORANGE' ELSE 'ESPECES' END,
            'VALIDEE'
        FROM generate_series(1, 10) AS n
        ON CONFLICT DO NOTHING;
    END LOOP;

    -- Agent Bafoussam → CLF011–CLF020 (bon payeurs DLA/YDE)
    FOR v_mois IN 1..6 LOOP
        INSERT INTO app.collectes_epargne
            (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
             client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
        SELECT
            gen_random_uuid(), v_imf_id, v_ag_baf, v_cycle_baf, v_agent3,
            'CLF' || LPAD(n::TEXT, 3, '0'),
            (18000 + ((n * 3210 + v_mois * 987) % 48000))::NUMERIC,
            MAKE_DATE(2026, v_mois, LEAST(28, 10 + (n * 4 % 15))),
            CASE WHEN (n + v_mois) % 4 = 0 THEN 'MTN'
                 WHEN (n + v_mois) % 4 = 1 THEN 'ORANGE' ELSE 'ESPECES' END,
            'VALIDEE'
        FROM generate_series(11, 20) AS n
        ON CONFLICT DO NOTHING;
    END LOOP;

    -- Agent Garoua → CLF021–CLF027 (à surveiller — quelques manques)
    FOR v_mois IN 1..6 LOOP
        IF v_mois NOT IN (3, 6) THEN
            INSERT INTO app.collectes_epargne
                (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
                 client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
            SELECT
                gen_random_uuid(), v_imf_id, v_ag_gar, v_cycle_gar, v_agent4,
                'CLF' || LPAD(n::TEXT, 3, '0'),
                (12000 + ((n * 2100 + v_mois * 543) % 32000))::NUMERIC,
                MAKE_DATE(2026, v_mois, LEAST(28, 12 + (n * 5 % 14))),
                CASE WHEN (n + v_mois) % 3 = 0 THEN 'MTN' ELSE 'ESPECES' END,
                CASE WHEN (n + v_mois) % 9 = 0 THEN 'REJETEE' ELSE 'VALIDEE' END
            FROM generate_series(21, 27) AS n
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;

    -- ── 7. KYC — dossiers pour les 30 clients FINANCE SARL ───────────────────
    -- Bon payeurs CLF001–CLF020 : VALIDE, NIVEAU_2, risque FAIBLE
    FOR v_c IN
        SELECT ci.id, ci.client_id_externe, ci.nom_complet, ci.telephone_principal,
               ci.date_naissance, ci.secteur_principal, ci.revenu_mensuel_estime
        FROM app.clients_informels ci
        WHERE ci.imf_id = v_imf_id
          AND ci.client_id_externe = ANY(ARRAY[
              'CLF001','CLF002','CLF003','CLF004','CLF005',
              'CLF006','CLF007','CLF008','CLF009','CLF010',
              'CLF011','CLF012','CLF013','CLF014','CLF015',
              'CLF016','CLF017','CLF018','CLF019','CLF020'])
    LOOP
        INSERT INTO app.kyc_dossiers (
            imf_id, client_id, nom_client, date_naissance, telephone,
            profession, revenu_mensuel_estim,
            type_piece_identite, numero_piece, date_emission_piece, date_expiration_piece,
            niveau_actuel, niveau_demande, statut,
            score_risque, niveau_risque, est_pep,
            verificateur_id, date_verification, date_expiration_kyc,
            verif_sanctions, verif_listes_noires
        ) VALUES (
            v_imf_id,
            v_c.client_id_externe,
            v_c.nom_complet,
            v_c.date_naissance,
            v_c.telephone_principal,
            v_c.secteur_principal,
            v_c.revenu_mensuel_estime,
            'CNI',
            'CMR' || LPAD((EXTRACT(YEAR FROM v_c.date_naissance)::INT * 11
                           + LENGTH(v_c.client_id_externe) * 37)::TEXT, 9, '0'),
            '2020-03-15', '2030-03-14',
            'NIVEAU_2', 'NIVEAU_2', 'VALIDE',
            15, 'FAIBLE', FALSE,
            v_dsi, NOW() - INTERVAL '60 days',
            CURRENT_DATE + INTERVAL '2 years',
            TRUE, TRUE
        ) ON CONFLICT ON CONSTRAINT uq_kyc_client_imf DO NOTHING;
    END LOOP;

    -- À surveiller CLF021–CLF027 : VALIDE, NIVEAU_1, risque MOYEN
    FOR v_c IN
        SELECT ci.id, ci.client_id_externe, ci.nom_complet, ci.telephone_principal,
               ci.date_naissance, ci.secteur_principal, ci.revenu_mensuel_estime
        FROM app.clients_informels ci
        WHERE ci.imf_id = v_imf_id
          AND ci.client_id_externe = ANY(ARRAY[
              'CLF021','CLF022','CLF023','CLF024','CLF025','CLF026','CLF027'])
    LOOP
        INSERT INTO app.kyc_dossiers (
            imf_id, client_id, nom_client, date_naissance, telephone,
            profession, revenu_mensuel_estim,
            type_piece_identite, numero_piece, date_emission_piece, date_expiration_piece,
            niveau_actuel, niveau_demande, statut,
            score_risque, niveau_risque, est_pep,
            verificateur_id, date_verification, date_expiration_kyc,
            verif_sanctions, verif_listes_noires
        ) VALUES (
            v_imf_id,
            v_c.client_id_externe,
            v_c.nom_complet,
            v_c.date_naissance,
            v_c.telephone_principal,
            v_c.secteur_principal,
            v_c.revenu_mensuel_estime,
            'CNI',
            'CMR' || LPAD((EXTRACT(YEAR FROM v_c.date_naissance)::INT * 13
                           + LENGTH(v_c.client_id_externe) * 43)::TEXT, 9, '0'),
            '2019-07-01', '2029-06-30',
            'NIVEAU_1', 'NIVEAU_2', 'VALIDE',
            45, 'MOYEN', FALSE,
            v_dsi, NOW() - INTERVAL '30 days',
            CURRENT_DATE + INTERVAL '1 year',
            TRUE, FALSE
        ) ON CONFLICT ON CONSTRAINT uq_kyc_client_imf DO NOTHING;
    END LOOP;

    -- À risque CLF028–CLF030 : EN_ATTENTE, NIVEAU_1, risque ELEVE
    FOR v_c IN
        SELECT ci.id, ci.client_id_externe, ci.nom_complet, ci.telephone_principal,
               ci.date_naissance, ci.secteur_principal, ci.revenu_mensuel_estime
        FROM app.clients_informels ci
        WHERE ci.imf_id = v_imf_id
          AND ci.client_id_externe = ANY(ARRAY['CLF028','CLF029','CLF030'])
    LOOP
        INSERT INTO app.kyc_dossiers (
            imf_id, client_id, nom_client, date_naissance, telephone,
            profession, revenu_mensuel_estim,
            type_piece_identite, numero_piece, date_emission_piece, date_expiration_piece,
            niveau_actuel, niveau_demande, statut,
            score_risque, niveau_risque, est_pep,
            verificateur_id, date_verification,
            verif_sanctions, verif_listes_noires,
            motif_risque_eleve
        ) VALUES (
            v_imf_id,
            v_c.client_id_externe,
            v_c.nom_complet,
            v_c.date_naissance,
            v_c.telephone_principal,
            v_c.secteur_principal,
            v_c.revenu_mensuel_estime,
            'CNI',
            'CMR' || LPAD((EXTRACT(YEAR FROM v_c.date_naissance)::INT * 17
                           + LENGTH(v_c.client_id_externe) * 57)::TEXT, 9, '0'),
            '2018-04-01', '2028-03-31',
            'NIVEAU_1', 'NIVEAU_1', 'EN_ATTENTE',
            72, 'ELEVE', FALSE,
            NULL, NULL,
            FALSE, FALSE,
            'Créance PAR90 en contentieux OHADA — audit KYC renforcé requis'
        ) ON CONFLICT ON CONSTRAINT uq_kyc_client_imf DO NOTHING;
    END LOOP;

    -- ── 8. ML — run modèle XGBoost MCRS v1.2.0 ───────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM ml.model_runs WHERE version = 'v1.2.0') THEN
        INSERT INTO ml.model_runs (
            model_name, version, dag_run_id, params_json,
            auc_roc, precision_score, recall_score, f1_score,
            gini_coefficient, ks_statistic, brier_score,
            nb_folds_temporels,
            periode_train_debut, periode_train_fin,
            periode_test_debut,  periode_test_fin,
            statut, est_modele_actif, artifact_path
        ) VALUES (
            'MCRS_XGBoost', 'v1.2.0', 'dag_ml_train_2026_06_01',
            '{"n_estimators":300,"max_depth":6,"learning_rate":0.05,'
            '"subsample":0.8,"colsample_bytree":0.8,"reg_alpha":0.1,"reg_lambda":1.0}',
            0.8734, 0.8210, 0.7945, 0.8076,
            0.7468, 0.6891, 0.1423,
            5,
            '2024-01-01', '2025-09-30',
            '2025-10-01', '2025-12-31',
            'SUCCES', TRUE, '/ml/models/mcrs/champion'
        );
    END IF;
    SELECT id INTO v_run_id FROM ml.model_runs
    WHERE version = 'v1.2.0' AND est_modele_actif = TRUE LIMIT 1;

    -- ── 9. ML features + scores — bon payeurs (CLF001–CLF020) ───────────────
    FOR v_c IN
        SELECT ci.client_id_externe,
               ci.revenu_mensuel_estime,
               ci.secteur_principal,
               ci.annees_experience,
               -- indice de variation basé sur l'ID pour différencier les clients
               (ascii(substring(ci.client_id_externe, 4, 1)) - 48) * 10
               + (ascii(substring(ci.client_id_externe, 5, 1)) - 48) AS seq
        FROM app.clients_informels ci
        WHERE ci.imf_id = v_imf_id
          AND ci.client_id_externe = ANY(ARRAY[
              'CLF001','CLF002','CLF003','CLF004','CLF005',
              'CLF006','CLF007','CLF008','CLF009','CLF010',
              'CLF011','CLF012','CLF013','CLF014','CLF015',
              'CLF016','CLF017','CLF018','CLF019','CLF020'])
    LOOP
        INSERT INTO ml.features_client (
            imf_id, client_id_externe, periode_debut, periode_fin,
            nb_collectes_12m, montant_total_collectes_12m,
            regularite_collecte_pct, montant_moy_collecte, ecart_type_collecte,
            tendance_collecte_3m, nb_cycles_manques_12m,
            nb_remboursements_12m, taux_remboursement_pct,
            jours_retard_moyen, jours_retard_max,
            nb_incidents_paiement, montant_impaye_courant,
            anciennete_client_jours, secteur_principal,
            revenu_mensuel_estime,
            ratio_collecte_credit, capacite_remboursement, indice_resilience,
            version_features, dag_run_id
        ) VALUES (
            v_imf_id, v_c.client_id_externe, '2025-01-01', '2025-12-31',
            22 + (v_c.seq % 3),
            ROUND(v_c.revenu_mensuel_estime * 0.18 * 12, 0),
            ROUND(0.9200 + (v_c.seq % 7) * 0.0100, 4),
            ROUND(v_c.revenu_mensuel_estime * 0.18, 0),
            ROUND(v_c.revenu_mensuel_estime * 0.02, 0),
            0.0320, 0,
            12,
            ROUND(0.9500 + (v_c.seq % 5) * 0.0100, 4),
            ROUND(1.2 + (v_c.seq % 4) * 0.3, 1),
            5 + (v_c.seq % 5),
            0, 0,
            EXTRACT(DAY FROM NOW() - '2024-01-01'::DATE)::INT,
            v_c.secteur_principal,
            v_c.revenu_mensuel_estime,
            1.45, ROUND(v_c.revenu_mensuel_estime * 0.72, 0), 0.8200,
            'v1', 'dag_ml_feat_2026_06_01'
        ) ON CONFLICT (imf_id, client_id_externe, periode_debut, version_features) DO NOTHING
        RETURNING id INTO v_feat_id;

        IF v_feat_id IS NULL THEN
            SELECT id INTO v_feat_id FROM ml.features_client
            WHERE imf_id = v_imf_id AND client_id_externe = v_c.client_id_externe
              AND periode_debut = '2025-01-01' AND version_features = 'v1' LIMIT 1;
        END IF;

        INSERT INTO ml.client_scores (
            imf_id, client_id_externe, feature_id, model_run_id,
            score_crs, score_rps, score_csi, score_mcrs,
            niveau_risque, cobac_classe, cobac_provision_taux,
            probabilite_defaut_30j, probabilite_defaut_90j,
            score_mcrs_ic_bas, score_mcrs_ic_haut,
            temps_survie_median_jours,
            action_recommandee, priorite_recouvrement, valide_jusqu_au
        ) VALUES (
            v_imf_id, v_c.client_id_externe, v_feat_id, v_run_id,
            ROUND(0.9200 + (v_c.seq % 7) * 0.0100, 4),
            ROUND(0.9500 + (v_c.seq % 5) * 0.0100, 4),
            ROUND(0.8200 + (v_c.seq % 8) * 0.0125, 4),
            ROUND(
                0.35 * (0.9200 + (v_c.seq % 7) * 0.0100)
              + 0.45 * (0.9500 + (v_c.seq % 5) * 0.0100)
              + 0.20 * (0.8200 + (v_c.seq % 8) * 0.0125), 4),
            'FAIBLE', 'A', 0.0000,
            0.0180, 0.0520,
            0.8800, 0.9600, 730,
            'AUCUNE', 5,
            CURRENT_DATE + INTERVAL '90 days'
        ) ON CONFLICT ON CONSTRAINT client_scores_client_imf_unique DO NOTHING;
    END LOOP;

    -- ── 9b. ML features + scores — clients à surveiller (CLF021–CLF027) ──────
    FOR v_c IN
        SELECT ci.client_id_externe,
               ci.revenu_mensuel_estime,
               ci.secteur_principal,
               (ascii(substring(ci.client_id_externe, 4, 1)) - 48) * 10
               + (ascii(substring(ci.client_id_externe, 5, 1)) - 48) AS seq
        FROM app.clients_informels ci
        WHERE ci.imf_id = v_imf_id
          AND ci.client_id_externe = ANY(ARRAY[
              'CLF021','CLF022','CLF023','CLF024','CLF025','CLF026','CLF027'])
    LOOP
        INSERT INTO ml.features_client (
            imf_id, client_id_externe, periode_debut, periode_fin,
            nb_collectes_12m, montant_total_collectes_12m,
            regularite_collecte_pct, montant_moy_collecte, ecart_type_collecte,
            tendance_collecte_3m, nb_cycles_manques_12m,
            nb_remboursements_12m, taux_remboursement_pct,
            jours_retard_moyen, jours_retard_max,
            nb_incidents_paiement, montant_impaye_courant,
            anciennete_client_jours, secteur_principal,
            revenu_mensuel_estime,
            ratio_collecte_credit, capacite_remboursement, indice_resilience,
            version_features, dag_run_id
        ) VALUES (
            v_imf_id, v_c.client_id_externe, '2025-01-01', '2025-12-31',
            8 + (v_c.seq % 2),
            ROUND(v_c.revenu_mensuel_estime * 0.11 * 9, 0),
            ROUND(0.6800 + (v_c.seq % 6) * 0.0080, 4),
            ROUND(v_c.revenu_mensuel_estime * 0.11, 0),
            ROUND(v_c.revenu_mensuel_estime * 0.04, 0),
            -0.0120, 3,
            9,
            ROUND(0.7200 + (v_c.seq % 6) * 0.0080, 4),
            ROUND(25.5 + (v_c.seq % 5) * 3.0, 1),
            35 + (v_c.seq % 10),
            1, 0,
            EXTRACT(DAY FROM NOW() - '2024-06-01'::DATE)::INT,
            v_c.secteur_principal,
            v_c.revenu_mensuel_estime,
            0.82, ROUND(v_c.revenu_mensuel_estime * 0.55, 0), 0.5600,
            'v1', 'dag_ml_feat_2026_06_01'
        ) ON CONFLICT (imf_id, client_id_externe, periode_debut, version_features) DO NOTHING
        RETURNING id INTO v_feat_id;

        IF v_feat_id IS NULL THEN
            SELECT id INTO v_feat_id FROM ml.features_client
            WHERE imf_id = v_imf_id AND client_id_externe = v_c.client_id_externe
              AND periode_debut = '2025-01-01' AND version_features = 'v1' LIMIT 1;
        END IF;

        INSERT INTO ml.client_scores (
            imf_id, client_id_externe, feature_id, model_run_id,
            score_crs, score_rps, score_csi, score_mcrs,
            niveau_risque, cobac_classe, cobac_provision_taux,
            probabilite_defaut_30j, probabilite_defaut_90j,
            score_mcrs_ic_bas, score_mcrs_ic_haut,
            temps_survie_median_jours,
            action_recommandee, priorite_recouvrement, valide_jusqu_au
        ) VALUES (
            v_imf_id, v_c.client_id_externe, v_feat_id, v_run_id,
            ROUND(0.5200 + (v_c.seq % 6) * 0.0100, 4),
            ROUND(0.7200 + (v_c.seq % 5) * 0.0080, 4),
            ROUND(0.5800 + (v_c.seq % 7) * 0.0100, 4),
            ROUND(
                0.35 * (0.5200 + (v_c.seq % 6) * 0.0100)
              + 0.45 * (0.7200 + (v_c.seq % 5) * 0.0080)
              + 0.20 * (0.5800 + (v_c.seq % 7) * 0.0100), 4),
            'MODERE', 'B', 0.2000,
            0.1640, 0.3280,
            0.5400, 0.7000, 310,
            'RELANCE_PREVENTIVE', 3,
            CURRENT_DATE + INTERVAL '60 days'
        ) ON CONFLICT ON CONSTRAINT client_scores_client_imf_unique DO NOTHING;
    END LOOP;

    -- ── 9c. ML features + scores + SHAP — clients à risque (CLF028–CLF030) ──
    FOR v_c IN
        SELECT ci.client_id_externe,
               ci.revenu_mensuel_estime,
               ci.secteur_principal,
               (ascii(substring(ci.client_id_externe, 4, 1)) - 48) * 10
               + (ascii(substring(ci.client_id_externe, 5, 1)) - 48) AS seq
        FROM app.clients_informels ci
        WHERE ci.imf_id = v_imf_id
          AND ci.client_id_externe = ANY(ARRAY['CLF028','CLF029','CLF030'])
    LOOP
        INSERT INTO ml.features_client (
            imf_id, client_id_externe, periode_debut, periode_fin,
            nb_collectes_12m, montant_total_collectes_12m,
            regularite_collecte_pct, montant_moy_collecte, ecart_type_collecte,
            tendance_collecte_3m, nb_cycles_manques_12m,
            nb_remboursements_12m, taux_remboursement_pct,
            jours_retard_moyen, jours_retard_max,
            nb_incidents_paiement, montant_impaye_courant,
            anciennete_client_jours, secteur_principal,
            revenu_mensuel_estime,
            ratio_collecte_credit, capacite_remboursement, indice_resilience,
            version_features, dag_run_id
        ) VALUES (
            v_imf_id, v_c.client_id_externe, '2025-01-01', '2025-12-31',
            4,
            ROUND(v_c.revenu_mensuel_estime * 0.06 * 4, 0),
            ROUND(0.1500 + (v_c.seq % 3) * 0.0100, 4),
            ROUND(v_c.revenu_mensuel_estime * 0.06, 0),
            ROUND(v_c.revenu_mensuel_estime * 0.08, 0),
            -0.0480, 8,
            3,
            ROUND(0.2800 + (v_c.seq % 3) * 0.0100, 4),
            82.0, 96,
            3,
            ROUND(v_c.revenu_mensuel_estime * 5 * 0.78, 0),
            EXTRACT(DAY FROM NOW() - '2025-01-01'::DATE)::INT,
            v_c.secteur_principal,
            v_c.revenu_mensuel_estime,
            0.28, ROUND(v_c.revenu_mensuel_estime * 0.20, 0), 0.2200,
            'v1', 'dag_ml_feat_2026_06_01'
        ) ON CONFLICT (imf_id, client_id_externe, periode_debut, version_features) DO NOTHING
        RETURNING id INTO v_feat_id;

        IF v_feat_id IS NULL THEN
            SELECT id INTO v_feat_id FROM ml.features_client
            WHERE imf_id = v_imf_id AND client_id_externe = v_c.client_id_externe
              AND periode_debut = '2025-01-01' AND version_features = 'v1' LIMIT 1;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM ml.client_scores
            WHERE imf_id = v_imf_id AND client_id_externe = v_c.client_id_externe
        ) THEN
            INSERT INTO ml.client_scores (
                imf_id, client_id_externe, feature_id, model_run_id,
                score_crs, score_rps, score_csi, score_mcrs,
                niveau_risque, cobac_classe, cobac_provision_taux,
                probabilite_defaut_30j, probabilite_defaut_90j,
                score_mcrs_ic_bas, score_mcrs_ic_haut,
                temps_survie_median_jours,
                action_recommandee, priorite_recouvrement, valide_jusqu_au
            ) VALUES (
                v_imf_id, v_c.client_id_externe, v_feat_id, v_run_id,
                ROUND(0.1500 + (v_c.seq % 3) * 0.0100, 4),
                ROUND(0.2800 + (v_c.seq % 3) * 0.0100, 4),
                ROUND(0.3800 + (v_c.seq % 3) * 0.0100, 4),
                ROUND(
                    0.35 * (0.1500 + (v_c.seq % 3) * 0.0100)
                  + 0.45 * (0.2800 + (v_c.seq % 3) * 0.0100)
                  + 0.20 * (0.3800 + (v_c.seq % 3) * 0.0100), 4),
                'CRITIQUE', 'D', 0.8000,
                0.7230, 0.9120,
                0.1800, 0.3200, 45,
                'ESCALADE_JURIDIQUE', 1,
                CURRENT_DATE + INTERVAL '30 days'
            )
            ON CONFLICT ON CONSTRAINT client_scores_client_imf_unique DO NOTHING
            RETURNING id INTO v_score_id;

            -- SHAP explicabilité — top 5 features pour les clients critiques
            IF v_score_id IS NOT NULL THEN
                INSERT INTO ml.shap_explanations
                    (score_id, feature_name, shap_value, feature_value, rang_importance, signe)
                VALUES
                    (v_score_id, 'jours_retard_max',          0.342800, '96',      1, '+'),
                    (v_score_id, 'taux_remboursement_pct',   -0.298400, '0.28',    2, '-'),
                    (v_score_id, 'regularite_collecte_pct',  -0.187600, '0.16',    3, '-'),
                    (v_score_id, 'nb_collectes_12m',         -0.156200, '4',       4, '-'),
                    (v_score_id, 'montant_impaye_courant',    0.134500, '556500',  5, '+');
            END IF;
        END IF;
    END LOOP;

    -- SHAP résumé pour 3 clients à surveiller (CLF021, CLF022, CLF023)
    FOR v_c IN
        SELECT cs.id AS score_id
        FROM ml.client_scores cs
        WHERE cs.imf_id = v_imf_id
          AND cs.client_id_externe IN ('CLF021','CLF022','CLF023')
          AND NOT EXISTS (
              SELECT 1 FROM ml.shap_explanations se WHERE se.score_id = cs.id
          )
    LOOP
        INSERT INTO ml.shap_explanations
            (score_id, feature_name, shap_value, feature_value, rang_importance, signe)
        VALUES
            (v_c.score_id, 'taux_remboursement_pct',  -0.145600, '0.73', 1, '-'),
            (v_c.score_id, 'jours_retard_max',          0.112300, '42',   2, '+'),
            (v_c.score_id, 'regularite_collecte_pct', -0.089400, '0.69', 3, '-');
    END LOOP;

    -- ── 10. Alertes prédictives ML ────────────────────────────────────────────
    INSERT INTO ml.alertes_predictives (
        imf_id, client_id_externe, type_alerte, urgence,
        titre, description, recommandation,
        valeur_declenchante, seuil_alerte, statut
    )
    SELECT v_imf_id, vals.cid, vals.type_alerte, vals.urgence,
           vals.titre, vals.description, vals.recommandation,
           vals.valeur_declenchante, vals.seuil_alerte, 'ACTIVE'
    FROM (VALUES
        ('CLF028','RISQUE_DEFAUT_IMMINENT','CRITIQUE',
         'CLF028 — Défaut imminent (MCRS 0.27)',
         'Théodore NKEMBI : 96j retard, 3 incidents, taux remboursement 28%. PAR90.',
         'Escalade juridique OHADA immédiate recommandée.',
         0.2700, 0.3500),
        ('CLF029','RISQUE_DEFAUT_IMMINENT','CRITIQUE',
         'CLF029 — Défaut imminent (MCRS 0.26)',
         'Alice FOUDA : 96j retard, encours restant 760 500 FCFA. Contentieux.',
         'Saisie des garanties et mise en demeure à engager.',
         0.2600, 0.3500),
        ('CLF030','RISQUE_DEFAUT_IMMINENT','CRITIQUE',
         'CLF030 — Défaut imminent (MCRS 0.28)',
         'Rodrigue ONANA : taux remboursement 30%, 96j retard. PAR90.',
         'Plan apurement négocié ou escalade juridique.',
         0.2800, 0.3500),
        ('CLF021','BAISSE_COLLECTE_DETECTEE','HAUTE',
         'CLF021 — Baisse collecte −38% sur 3 mois',
         'Henriette MEDJO : régularité passée de 78% à 61% sur Q3-Q4 2025.',
         'Relance SMS + visite terrain recommandée dans les 7 jours.',
         0.6100, 0.7500),
        ('CLF024','RUPTURE_CYCLE_COLLECTE','HAUTE',
         'CLF024 — 3 cycles consécutifs manqués',
         'Dieudonné MVOGO : aucune collecte depuis juillet 2025.',
         'Contact téléphonique urgent — vérifier situation activité pisciculture.',
         3.0000, 2.0000)
    ) AS vals(cid, type_alerte, urgence, titre, description, recommandation,
              valeur_declenchante, seuil_alerte)
    WHERE NOT EXISTS (
        SELECT 1 FROM ml.alertes_predictives ap
        WHERE ap.imf_id = v_imf_id AND ap.client_id_externe = vals.cid
          AND ap.type_alerte = vals.type_alerte
    );

    -- ── 11. KPI snapshots mensuels ────────────────────────────────────────────
    -- Agence Yaoundé Nlongkak — 12 mois 2025 (agent Réné)
    FOR v_mois IN 1..12 LOOP
        INSERT INTO app.kpi_collecte_snapshots (
            imf_id, agence_id, cycle_id, agent_id, date_calcul, periode,
            nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
            objectif_montant, taux_realisation_pct,
            taux_ponctualite_pct, taux_rejet_pct, nb_doublons_detectes,
            montant_especes, montant_mtn, montant_orange,
            montant_wave, montant_autres,
            pct_collectes_geolocalisees
        ) VALUES (
            v_imf_id, v_ag_yde, v_cycle_yde, v_agent_rene,
            MAKE_DATE(2025, v_mois, 28), 'MENSUEL',
            (180 + (v_mois * 7 % 40))::INT,
            (7500000 + (v_mois * 310000 % 1900000))::NUMERIC(15,2),
            (41667  + (v_mois * 1200  % 8000))::NUMERIC(12,2),
            18,
            8000000.00,
            ROUND((7500000.0 + (v_mois * 310000 % 1900000)) / 8000000.0, 4),
            ROUND(0.8900 + (v_mois % 5) * 0.0100, 4),
            ROUND(0.0200 + (v_mois % 3) * 0.0050, 4),
            0,
            (2625000 + (v_mois * 100000 % 500000))::NUMERIC(15,2),
            (2625000 + (v_mois * 120000 % 600000))::NUMERIC(15,2),
            (1500000 + (v_mois * 80000  % 400000))::NUMERIC(15,2),
            0, 750000.00,
            78.50
        ) ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;
    END LOOP;

    -- Agence Douala Bassa — 12 mois 2025 (agent Réné)
    FOR v_mois IN 1..12 LOOP
        INSERT INTO app.kpi_collecte_snapshots (
            imf_id, agence_id, cycle_id, agent_id, date_calcul, periode,
            nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
            objectif_montant, taux_realisation_pct,
            taux_ponctualite_pct, taux_rejet_pct, nb_doublons_detectes,
            montant_especes, montant_mtn, montant_orange,
            montant_wave, montant_autres,
            pct_collectes_geolocalisees
        ) VALUES (
            v_imf_id, v_ag_dla, v_cycle_yde, v_agent_rene,
            MAKE_DATE(2025, v_mois, 28), 'MENSUEL',
            (95 + (v_mois * 5 % 25))::INT,
            (3800000 + (v_mois * 180000 % 1200000))::NUMERIC(15,2),
            (40000  + (v_mois * 900   % 6000))::NUMERIC(12,2),
            12,
            4500000.00,
            ROUND((3800000.0 + (v_mois * 180000 % 1200000)) / 4500000.0, 4),
            ROUND(0.8300 + (v_mois % 6) * 0.0100, 4),
            ROUND(0.0300 + (v_mois % 4) * 0.0050, 4),
            0,
            (1330000 + (v_mois * 60000  % 300000))::NUMERIC(15,2),
            (1330000 + (v_mois * 70000  % 350000))::NUMERIC(15,2),
            (950000  + (v_mois * 40000  % 200000))::NUMERIC(15,2),
            0, 190000.00,
            82.30
        ) ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;
    END LOOP;

    -- Nouvelles agences — 6 mois 2026
    FOR v_mois IN 1..6 LOOP
        -- YDE Centre
        INSERT INTO app.kpi_collecte_snapshots (
            imf_id, agence_id, cycle_id, agent_id, date_calcul, periode,
            nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
            objectif_montant, taux_realisation_pct,
            taux_ponctualite_pct, taux_rejet_pct, nb_doublons_detectes,
            montant_especes, montant_mtn, montant_orange,
            montant_wave, montant_autres,
            pct_collectes_geolocalisees
        ) VALUES (
            v_imf_id, v_ag_yde2, v_cycle_yde2, v_agent2,
            MAKE_DATE(2026, v_mois, 28), 'MENSUEL',
            (58 + (v_mois * 4 % 18))::INT,
            (2350000 + (v_mois * 150000 % 800000))::NUMERIC(15,2),
            (40517 + (v_mois * 800 % 5000))::NUMERIC(12,2),
            10, 5000000.00,
            ROUND((2350000.0 + (v_mois * 150000 % 800000)) / 5000000.0, 4),
            0.9100, 0.0100, 0,
            700000, 900000, 700000, 0, 50000, 91.20
        ) ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;

        -- Bafoussam
        INSERT INTO app.kpi_collecte_snapshots (
            imf_id, agence_id, cycle_id, agent_id, date_calcul, periode,
            nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
            objectif_montant, taux_realisation_pct,
            taux_ponctualite_pct, taux_rejet_pct, nb_doublons_detectes,
            montant_especes, montant_mtn, montant_orange,
            montant_wave, montant_autres,
            pct_collectes_geolocalisees
        ) VALUES (
            v_imf_id, v_ag_baf, v_cycle_baf, v_agent3,
            MAKE_DATE(2026, v_mois, 28), 'MENSUEL',
            (42 + (v_mois * 3 % 15))::INT,
            (1680000 + (v_mois * 120000 % 600000))::NUMERIC(15,2),
            (40000 + (v_mois * 600 % 4000))::NUMERIC(12,2),
            10, 3500000.00,
            ROUND((1680000.0 + (v_mois * 120000 % 600000)) / 3500000.0, 4),
            0.8800, 0.0150, 0,
            588000, 504000, 504000, 0, 84000, 85.60
        ) ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;

        -- Garoua
        INSERT INTO app.kpi_collecte_snapshots (
            imf_id, agence_id, cycle_id, agent_id, date_calcul, periode,
            nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
            objectif_montant, taux_realisation_pct,
            taux_ponctualite_pct, taux_rejet_pct, nb_doublons_detectes,
            montant_especes, montant_mtn, montant_orange,
            montant_wave, montant_autres,
            pct_collectes_geolocalisees
        ) VALUES (
            v_imf_id, v_ag_gar, v_cycle_gar, v_agent4,
            MAKE_DATE(2026, v_mois, 28), 'MENSUEL',
            (28 + (v_mois * 2 % 12))::INT,
            (980000 + (v_mois * 80000 % 400000))::NUMERIC(15,2),
            (35000 + (v_mois * 400 % 3000))::NUMERIC(12,2),
            7, 2500000.00,
            ROUND((980000.0 + (v_mois * 80000 % 400000)) / 2500000.0, 4),
            0.7900, 0.0250, 0,
            440000, 294000, 196000, 0, 50000, 72.40
        ) ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;
    END LOOP;

    -- ── 12. Benchmarks inter-agences — trimestriel 2025 ──────────────────────
    FOR v_n IN 1..4 LOOP
        INSERT INTO app.benchmarks_agences (
            imf_id, agence_id, date_calcul, periode,
            rang_collecte, score_collecte_zscore,
            rang_recouvrement, score_recouvrement_zscore,
            rang_global, score_global, nb_agences_comparees
        ) VALUES
            (v_imf_id, v_ag_yde, MAKE_DATE(2025, v_n * 3, 28), 'TRIMESTRIEL',
             1, ROUND(0.8200 + v_n * 0.0200, 4),
             1, ROUND(0.7800 + v_n * 0.0150, 4),
             1, ROUND(0.8100 + v_n * 0.0100, 4), 2),
            (v_imf_id, v_ag_dla, MAKE_DATE(2025, v_n * 3, 28), 'TRIMESTRIEL',
             2, ROUND(0.4100 + v_n * 0.0100, 4),
             2, ROUND(0.3900 + v_n * 0.0120, 4),
             2, ROUND(0.4000 + v_n * 0.0080, 4), 2)
        ON CONFLICT (imf_id, agence_id, date_calcul, periode) DO NOTHING;
    END LOOP;

    -- ── 13. Alertes opérationnelles ───────────────────────────────────────────
    INSERT INTO app.alertes_operationnelles (
        imf_id, type_alerte, niveau, titre, message,
        entite_type, entite_id, valeur_observee, seuil_configure,
        statut, destinataire_role
    )
    SELECT v_imf_id, vals.type_alerte, vals.niveau, vals.titre, vals.message,
           vals.entite_type, vals.entite_id, vals.valeur_observee, vals.seuil_configure,
           'ACTIVE', vals.destinataire_role
    FROM (VALUES
        ('PAR_SEUIL_DEPASSE','CRITIQUE',
         'FINANCE SARL — PAR90 = 11.1% (seuil COBAC 5% dépassé)',
         'CLF028-CLF030 en contentieux : PAR90 = 3/27 créances = 11.1%.',
         'IMF', v_imf_id, 11.10, 5.00, 'DIRECTEUR'),
        ('OBJECTIF_NON_ATTEINT','AVERTISSEMENT',
         'Agence Douala Bassa — Taux réalisation 84.4% (déc. 2025)',
         'Objectif 4 500 000 FCFA — réalisé 3 800 000 FCFA en décembre 2025.',
         'AGENCE', v_ag_dla, 84.40, 90.00, 'DIRECTEUR'),
        ('AGENT_INACTIF','AVERTISSEMENT',
         'Zone Nkol-Afeme — Aucune collecte depuis 45 jours',
         'CLF028 zone : dernière collecte Avril 2025. Agent à relancer.',
         'AGENT', v_agent_rene, 45.0, 30.0, 'DIRECTEUR'),
        ('PROVISION_INSUFFISANTE','CRITIQUE',
         'Provisions COBAC insuffisantes — 3 créances classe D',
         'CLF028-CLF030 classe D (PAR90) : taux provision 50% requis par COBAC.',
         'IMF', v_imf_id, 35.00, 50.00, 'DIRECTEUR'),
        ('DOSSIER_SANS_ACTION','AVERTISSEMENT',
         '4 dossiers recouvrement sans action depuis 30 jours',
         'CLF021, CLF022, CLF023, CLF024 PAR30 — aucune action tracée.',
         'IMF', v_imf_id, 4.0, 1.0, 'RESPONSABLE_RECOUVREMENT')
    ) AS vals(type_alerte, niveau, titre, message,
              entite_type, entite_id, valeur_observee, seuil_configure,
              destinataire_role)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.alertes_operationnelles ao
        WHERE ao.imf_id = v_imf_id AND ao.titre = vals.titre
    );

    -- ── 14. Alertes système ───────────────────────────────────────────────────
    INSERT INTO app.alertes_systeme (type, titre, detail, severite, statut, source, created_at)
    SELECT vals.type, vals.titre, vals.detail, vals.severite, 'ACTIVE', vals.source, NOW() - vals.age
    FROM (VALUES
        ('PAR_CRITIQUE',
         'FINANCE SARL — PAR90 = 11.1% (COBAC 5% dépassé)',
         'Rapport provisionnement COBAC à soumettre. Clients : CLF028, CLF029, CLF030.',
         'CRITIQUE', 'RISK_ENGINE', INTERVAL '2 days'),
        ('KYC_EXPIRATION',
         'FINANCE SARL — 7 dossiers KYC niveau 1 non promus',
         'CLF021–CLF027 : dossiers KYC NIVEAU_1 en attente de promotion NIVEAU_2.',
         'AVERTISSEMENT', 'KYC_MONITOR', INTERVAL '5 days'),
        ('ML_DRIFT_DETECTE',
         'Drift données — features MCRS CLF028-CLF030 (PSI > 0.25)',
         'PSI élevé sur jours_retard_max et taux_remboursement_pct. Réentraînement recommandé.',
         'AVERTISSEMENT', 'ML_ENGINE', INTERVAL '1 day'),
        ('AGENT_GPS_ANOMALIE',
         'Anomalie GPS — Agent Réné hors zone habituelle',
         'Position détectée à 8 km de Nlongkak à 07h23. Cohérence trajet à vérifier.',
         'INFO', 'GPS_MONITOR', INTERVAL '3 hours')
    ) AS vals(type, titre, detail, severite, source, age)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.alertes_systeme als WHERE als.titre = vals.titre
    );

    RAISE NOTICE 'V46 OK — FINANCE SARL : Directeur %, Analyste %, 5 agences, 4 agents GPS, 30 KYC, 30 ML scores, alertes et KPI insérés.',
        'rene.komtsindi@saintjeaningenieur.org',
        'renekomtsindi559@gmail.com';
END $$;
