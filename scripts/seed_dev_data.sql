-- ============================================================
-- Seed data pour l'environnement de développement IMF Pipeline
-- IMF : COOPEC Yaoundé  |  2 agences  |  5 users  |  6 clients
-- 15 collectes  |  4 créances  |  KPI snapshots
-- ============================================================

BEGIN;

-- ─── 1. IMF ─────────────────────────────────────────────────
INSERT INTO app.imf (
    code, nom, denomination_sociale, pays, forme_juridique,
    adresse_siege, telephone, email,
    taux_interet_annuel, duree_max_credit_mois, taux_penalite_retard,
    seuil_relance_jours, taux_epargne, solde_min_epargne, frais_tenue_compte,
    capital_social, num_agrement, actif
) VALUES (
    'COOPEC_YDE',
    'COOPEC Yaoundé',
    'Coopérative d''Épargne et de Crédit de Yaoundé',
    'Cameroun',
    'COOPERATIVE',
    'Avenue Kennedy, Yaoundé, Centre, Cameroun',
    '+237 222 230 100',
    'contact@coopec-yaounde.cm',
    18.00, 36, 2.00,
    30, 5.00, 5000.00, 500.00,
    50000000.00, 'COBAC/2018/IMF/0042',
    TRUE
) ON CONFLICT (code) DO NOTHING;

-- Récupérer l'id IMF
DO $$
DECLARE
    v_imf_id     BIGINT;
    v_agence1_id BIGINT;
    v_agence2_id BIGINT;
    v_dir_id     BIGINT;
    v_rr_id      BIGINT;
    v_analyste_id BIGINT;
    v_agent1_id  BIGINT;
    v_agent2_id  BIGINT;
    v_cycle_id   BIGINT;
    v_cli1_id    BIGINT;
    v_cli2_id    BIGINT;
    v_cli3_id    BIGINT;
    v_cli4_id    BIGINT;
    v_cli5_id    BIGINT;
    v_cli6_id    BIGINT;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'COOPEC_YDE';

    -- ─── 2. Agences ────────────────────────────────────────
    INSERT INTO app.agences (imf_id, nom, ville, responsable, telephone, actif)
    VALUES
        (v_imf_id, 'Agence Centre-Ville', 'Yaoundé',  'Jean-Paul Mbida',   '+237 699 100 200', TRUE),
        (v_imf_id, 'Agence Mvan',         'Yaoundé',  'Marie-Claire Ateba', '+237 699 100 300', TRUE)
    ON CONFLICT (imf_id, nom) DO NOTHING;

    SELECT id INTO v_agence1_id FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Centre-Ville';
    SELECT id INTO v_agence2_id FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Mvan';

    -- ─── 3. Utilisateurs ───────────────────────────────────
    -- Mot de passe : Agent2024!      → hash $2b$10$zKPQAXmTv7S9Hm9mzu1Lx.nNFa3uAp.mX.5KLSn1TB76JL45KdTnq
    -- Mot de passe : Directeur2024!  → hash $2b$10$T8mnT7FQkr7xcpPpnjdG0eJ9b6F9GJQsDQPyWCFfvf7jmHD1A6GkO
    -- Mot de passe : Analyst2024!    → hash $2b$10$YF27aX9eEHs671iQH1jaRuszt9xCoRpTx/47lPAVmL/8ebl7L1jY.

    INSERT INTO app.utilisateurs (username, password_hash, role, email, imf_id, actif)
    VALUES
        ('directeur',  '$2b$10$T8mnT7FQkr7xcpPpnjdG0eJ9b6F9GJQsDQPyWCFfvf7jmHD1A6GkO',
         'DIRECTEUR',               'directeur@coopec-yaounde.cm',  v_imf_id, TRUE),
        ('resp_recouv', '$2b$10$T8mnT7FQkr7xcpPpnjdG0eJ9b6F9GJQsDQPyWCFfvf7jmHD1A6GkO',
         'RESPONSABLE_RECOUVREMENT', 'recouv@coopec-yaounde.cm',    v_imf_id, TRUE),
        ('analyste1',  '$2b$10$YF27aX9eEHs671iQH1jaRuszt9xCoRpTx/47lPAVmL/8ebl7L1jY.',
         'ANALYSTE',                'analyste@coopec-yaounde.cm',   v_imf_id, TRUE),
        ('agent_mvogo', '$2b$10$zKPQAXmTv7S9Hm9mzu1Lx.nNFa3uAp.mX.5KLSn1TB76JL45KdTnq',
         'AGENT',                   'mvogo@coopec-yaounde.cm',      v_imf_id, TRUE),
        ('agent_nkolo', '$2b$10$zKPQAXmTv7S9Hm9mzu1Lx.nNFa3uAp.mX.5KLSn1TB76JL45KdTnq',
         'AGENT',                   'nkolo@coopec-yaounde.cm',      v_imf_id, TRUE)
    ON CONFLICT (username) DO NOTHING;

    -- Lier admin à l'IMF (pas de changement de rôle)
    UPDATE app.utilisateurs SET imf_id = v_imf_id WHERE username = 'admin' AND imf_id IS NULL;

    SELECT id INTO v_dir_id      FROM app.utilisateurs WHERE username = 'directeur';
    SELECT id INTO v_rr_id       FROM app.utilisateurs WHERE username = 'resp_recouv';
    SELECT id INTO v_analyste_id FROM app.utilisateurs WHERE username = 'analyste1';
    SELECT id INTO v_agent1_id   FROM app.utilisateurs WHERE username = 'agent_mvogo';
    SELECT id INTO v_agent2_id   FROM app.utilisateurs WHERE username = 'agent_nkolo';

    -- ─── 4. Cycles de collecte ─────────────────────────────
    INSERT INTO app.cycles_collecte (imf_id, agence_id, nom_cycle, periodicite, date_debut, objectif_montant, objectif_nb_transactions, actif)
    VALUES
        (v_imf_id, v_agence1_id, 'Cycle Semaine 21-2026', 'HEBDOMADAIRE',
         '2026-05-18', 500000.00, 50, TRUE),
        (v_imf_id, v_agence2_id, 'Cycle Semaine 21-2026 Mvan', 'HEBDOMADAIRE',
         '2026-05-18', 350000.00, 35, TRUE)
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_cycle_id FROM app.cycles_collecte
    WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Semaine 21-2026';

    -- ─── 5. Clients informels ──────────────────────────────
    INSERT INTO app.clients_informels (
        imf_id, client_id_externe, nom_complet, telephone_principal, zone_id,
        agence_id, date_naissance, sexe, secteur_principal, sous_secteur,
        annees_experience, revenu_mensuel_estime, marche_principal, frequence_marche,
        niveau_education, situation_familiale, nombre_personnes_charge,
        latitude_activite, longitude_activite, adresse_activite, actif
    ) VALUES
        (v_imf_id, 'CLI-YDE-0001', 'Pauline Owona Nkomo',   '+237 677 100 001', 'ZONE_CTR',
         v_agence1_id, '1985-03-15', 'F', 'COMMERCE',   'Vente alimentaire',
         8, 85000.00, 'Marché Mokolo', 'QUOTIDIEN', 'PRIMAIRE', 'MARIE', 3,
         3.8701200, 11.5128400, 'Marché Mokolo, Stand A12, Yaoundé', TRUE),

        (v_imf_id, 'CLI-YDE-0002', 'Emmanuel Talla Feudjio','+237 699 200 002', 'ZONE_CTR',
         v_agence1_id, '1979-07-22', 'M', 'ARTISANAT',  'Menuiserie',
         15, 120000.00, 'Marché Central', 'HEBDOMADAIRE', 'SECONDAIRE', 'MARIE', 5,
         3.8620000, 11.5180000, 'Atelier Artisanat, Rue Essono, Yaoundé', TRUE),

        (v_imf_id, 'CLI-YDE-0003', 'Cécile Mengue Abomo',   '+237 655 300 003', 'ZONE_MVN',
         v_agence2_id, '1992-11-08', 'F', 'COMMERCE',   'Cosmétiques',
         5, 60000.00, 'Marché Mvan', 'QUOTIDIEN', 'SECONDAIRE', 'CELIBATAIRE', 1,
         3.8320000, 11.5050000, 'Marché Mvan, Allée 3, Yaoundé', TRUE),

        (v_imf_id, 'CLI-YDE-0004', 'Roger Biyong Esso',     '+237 677 400 004', 'ZONE_MVN',
         v_agence2_id, '1975-02-14', 'M', 'TRANSPORT',  'Taxi moto',
         12, 95000.00, NULL, 'OCCASIONNEL', 'PRIMAIRE', 'MARIE', 4,
         3.8400000, 11.5100000, 'Quartier Mvan, Yaoundé', TRUE),

        (v_imf_id, 'CLI-YDE-0005', 'Anastasie Ngo Biyong',  '+237 699 500 005', 'ZONE_CTR',
         v_agence1_id, '1988-09-30', 'F', 'AGRICOLE',  'Maraîchage',
         10, 70000.00, 'Marché Mfoundi', 'HEBDOMADAIRE', 'SECONDAIRE', 'VEUF', 2,
         3.8750000, 11.5200000, 'Jardin derrière Université, Yaoundé', TRUE),

        (v_imf_id, 'CLI-YDE-0006', 'Théodore Akoa Zang',    '+237 655 600 006', 'ZONE_MVN',
         v_agence2_id, '1982-06-05', 'M', 'ELEVAGE',    'Aviculture',
         20, 150000.00, NULL, 'MENSUEL', 'SUPERIEUR', 'MARIE', 6,
         3.8250000, 11.4950000, 'Ferme Akoa, Route Mvan, Yaoundé', TRUE)
    ON CONFLICT (imf_id, client_id_externe) DO NOTHING;

    SELECT id INTO v_cli1_id FROM app.clients_informels WHERE imf_id = v_imf_id AND client_id_externe = 'CLI-YDE-0001';
    SELECT id INTO v_cli2_id FROM app.clients_informels WHERE imf_id = v_imf_id AND client_id_externe = 'CLI-YDE-0002';
    SELECT id INTO v_cli3_id FROM app.clients_informels WHERE imf_id = v_imf_id AND client_id_externe = 'CLI-YDE-0003';
    SELECT id INTO v_cli4_id FROM app.clients_informels WHERE imf_id = v_imf_id AND client_id_externe = 'CLI-YDE-0004';
    SELECT id INTO v_cli5_id FROM app.clients_informels WHERE imf_id = v_imf_id AND client_id_externe = 'CLI-YDE-0005';
    SELECT id INTO v_cli6_id FROM app.clients_informels WHERE imf_id = v_imf_id AND client_id_externe = 'CLI-YDE-0006';

    -- ─── 6. Collectes épargne ──────────────────────────────
    INSERT INTO app.collectes_epargne (
        uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
        client_id_externe, montant_collecte, date_collecte, heure_collecte,
        canal_paiement, reference_transaction, latitude, longitude, precision_gps_metres,
        statut, validated_by_id, validated_at
    ) VALUES
        -- Agent 1 (Mvogo) — Agence Centre-Ville
        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0001', 10000.00, '2026-05-19', '08:30:00', 'ESPECES',   NULL,
         3.8701, 11.5128, 5.0, 'VALIDEE', v_dir_id, NOW() - INTERVAL '6 days'),

        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0002', 15000.00, '2026-05-19', '09:15:00', 'MTN',       'MTN-TXN-001',
         3.8620, 11.5180, 8.0, 'VALIDEE', v_dir_id, NOW() - INTERVAL '6 days'),

        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0005', 8000.00,  '2026-05-20', '10:00:00', 'ORANGE',    'ORG-TXN-001',
         3.8750, 11.5200, 10.0, 'VALIDEE', v_dir_id, NOW() - INTERVAL '5 days'),

        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0001', 10000.00, '2026-05-21', '08:45:00', 'ESPECES',   NULL,
         3.8701, 11.5128, 5.0, 'VALIDEE', v_dir_id, NOW() - INTERVAL '4 days'),

        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0002', 15000.00, '2026-05-22', '09:30:00', 'MTN',       'MTN-TXN-002',
         3.8620, 11.5180, 7.0, 'VALIDEE', v_dir_id, NOW() - INTERVAL '3 days'),

        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0005', 8000.00,  '2026-05-23', '10:30:00', 'ESPECES',   NULL,
         3.8750, 11.5200, 6.0, 'SOUMISE', NULL, NULL),

        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0001', 10000.00, '2026-05-26', '08:00:00', 'ESPECES',   NULL,
         3.8701, 11.5128, 4.0, 'SOUMISE', NULL, NULL),

        -- Agent 2 (Nkolo) — Agence Mvan
        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0003', 5000.00,  '2026-05-19', '08:00:00', 'ESPECES',   NULL,
         3.8320, 11.5050, 12.0, 'VALIDEE', v_rr_id, NOW() - INTERVAL '6 days'),

        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0004', 12000.00, '2026-05-20', '09:00:00', 'MTN',       'MTN-TXN-003',
         3.8400, 11.5100, 6.0, 'VALIDEE', v_rr_id, NOW() - INTERVAL '5 days'),

        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0006', 20000.00, '2026-05-21', '10:00:00', 'VIREMENT',  'VIR-2026-001',
         3.8250, 11.4950, 3.0, 'VALIDEE', v_rr_id, NOW() - INTERVAL '4 days'),

        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0003', 5000.00,  '2026-05-22', '08:30:00', 'ESPECES',   NULL,
         3.8320, 11.5050, 9.0, 'VALIDEE', v_rr_id, NOW() - INTERVAL '3 days'),

        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0004', 12000.00, '2026-05-25', '09:30:00', 'ORANGE',    'ORG-TXN-002',
         3.8400, 11.5100, 5.0, 'SOUMISE', NULL, NULL),

        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0006', 20000.00, '2026-05-26', '07:50:00', 'ESPECES',   NULL,
         3.8250, 11.4950, 4.0, 'SOUMISE', NULL, NULL),

        -- Collecte rejetée (doublon)
        (gen_random_uuid(), v_imf_id, v_agence1_id, v_cycle_id, v_agent1_id,
         'CLI-YDE-0001', 10000.00, '2026-05-21', '08:50:00', 'ESPECES',   NULL,
         3.8701, 11.5128, 5.0, 'DOUBLON', NULL, NULL),

        -- Collecte en attente de sync
        (gen_random_uuid(), v_imf_id, v_agence2_id, NULL, v_agent2_id,
         'CLI-YDE-0003', 5000.00,  '2026-05-24', '08:15:00', 'ESPECES',   NULL,
         3.8320, 11.5050, 15.0, 'EN_ATTENTE', NULL, NULL)
    ON CONFLICT (uuid_mobile) DO NOTHING;

    -- ─── 7. Créances ───────────────────────────────────────
    INSERT INTO app.creances (
        imf_id, agence_id, id_pret_externe, client_id_externe, client_informel_id,
        montant_initial, montant_impaye, capital_restant_du, interets_retard, penalites,
        date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
        jours_retard, categorie_par, classe_risque_cobac, taux_provision_cobac, montant_provision,
        type_garantie, valeur_garantie, statut, agent_responsable_id
    ) VALUES
        -- Créance saine (classe A, 0 jours retard)
        (v_imf_id, v_agence1_id, 'PRET-2025-0142', 'CLI-YDE-0001', v_cli1_id,
         250000.00, 150000.00, 148000.00, 0.00, 0.00,
         '2025-11-01', '2025-12-01', NULL,
         0, 'COURANT', 'A', 0.00, 0.00,
         'CAUTION_SOLIDAIRE', 100000.00, 'ACTIVE', v_agent1_id),

        -- Créance légèrement en retard (classe B, 35 jours)
        (v_imf_id, v_agence1_id, 'PRET-2025-0098', 'CLI-YDE-0002', v_cli2_id,
         500000.00, 320000.00, 315000.00, 6400.00, 0.00,
         '2025-08-15', '2025-09-15', '2026-04-22',
         35, 'PAR30', 'B', 20.00, 64000.00,
         'HYPOTHEQUE', 800000.00, 'RECOUVREMENT_AMIABLE', v_agent1_id),

        -- Créance en souffrance (classe C, 75 jours)
        (v_imf_id, v_agence2_id, 'PRET-2025-0067', 'CLI-YDE-0004', v_cli4_id,
         180000.00, 130000.00, 127000.00, 9750.00, 2600.00,
         '2025-09-01', '2025-10-01', '2026-03-13',
         75, 'PAR60', 'C', 50.00, 65000.00,
         NULL, NULL, 'MISE_EN_DEMEURE', v_agent2_id),

        -- Créance normale (classe A, 0 jours retard)
        (v_imf_id, v_agence2_id, 'PRET-2026-0011', 'CLI-YDE-0006', v_cli6_id,
         800000.00, 740000.00, 735000.00, 0.00, 0.00,
         '2026-03-01', '2026-04-01', NULL,
         0, 'COURANT', 'A', 0.00, 0.00,
         'NANTISSEMENT', 2000000.00, 'ACTIVE', v_agent2_id)
    ON CONFLICT (imf_id, id_pret_externe) DO NOTHING;

    -- ─── 8. KPI collecte snapshot (cette semaine) ──────────
    INSERT INTO app.kpi_collecte_snapshots (
        imf_id, agence_id, agent_id, date_calcul, periode,
        nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
        objectif_montant, taux_realisation_pct, taux_ponctualite_pct, taux_rejet_pct,
        nb_doublons_detectes, montant_especes, montant_mtn, montant_orange, montant_wave, montant_autres,
        pct_collectes_geolocalisees, dag_run_id
    ) VALUES
        -- Agence Centre-Ville — semaine
        (v_imf_id, v_agence1_id, NULL, '2026-05-25', 'HEBDOMADAIRE',
         9, 76000.00, 8444.44, 3,
         500000.00, 15.2000, 85.5000, 11.1000,
         1, 43000.00, 25000.00, 8000.00, 0.00, 0.00,
         100.00, 'manual_seed'),

        -- Agence Mvan — semaine
        (v_imf_id, v_agence2_id, NULL, '2026-05-25', 'HEBDOMADAIRE',
         6, 74000.00, 12333.33, 3,
         350000.00, 21.1429, 83.3000, 0.0000,
         0, 37000.00, 12000.00, 12000.00, 0.00, 20000.00,
         100.00, 'manual_seed'),

        -- Global IMF — semaine
        (v_imf_id, NULL, NULL, '2026-05-25', 'HEBDOMADAIRE',
         15, 150000.00, 10000.00, 6,
         850000.00, 17.6471, 84.6000, 6.7000,
         1, 80000.00, 37000.00, 20000.00, 0.00, 20000.00,
         100.00, 'manual_seed')
    ON CONFLICT DO NOTHING;

    -- ─── 9. KPI recouvrement snapshot ──────────────────────
    INSERT INTO app.kpi_recouvrement_snapshots (
        imf_id, agence_id, date_calcul, periode,
        nb_creances_actives, encours_total, nb_creances_probleme, total_provisions,
        par30_montant, par60_montant, par90_montant,
        par30_taux_pct, par60_taux_pct, par90_taux_pct,
        montant_recouvre, montant_perte_nette, taux_recouvrement_pct,
        dag_run_id
    ) VALUES
        (v_imf_id, NULL, '2026-05-25', 'MENSUEL',
         4, 1340000.00, 2, 129000.00,
         320000.00, 130000.00, 0.00,
         23.8806, 9.7015, 0.0000,
         45000.00, 0.00, 3.3582,
         'manual_seed')
    ON CONFLICT (imf_id, agence_id, date_calcul, periode) DO NOTHING;
    -- Note : agence_id IS NULL → le ON CONFLICT ci-dessus ne matche pas les NULLs en PostgreSQL,
    -- mais c'est acceptable pour un seed idempotent (réexécuter insère en doublon possible).

    RAISE NOTICE '✓ IMF id=%  |  agences: %, %  |  users: dir=%, rr=%, ana=%, ag1=%, ag2=%',
        v_imf_id, v_agence1_id, v_agence2_id,
        v_dir_id, v_rr_id, v_analyste_id, v_agent1_id, v_agent2_id;
    RAISE NOTICE '✓ Clients: %, %, %, %, %, %', v_cli1_id, v_cli2_id, v_cli3_id, v_cli4_id, v_cli5_id, v_cli6_id;
    RAISE NOTICE '✓ Seed complet.';
END $$;

COMMIT;
