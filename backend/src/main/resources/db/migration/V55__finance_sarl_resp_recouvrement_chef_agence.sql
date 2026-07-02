-- ============================================================
-- V55 — FINANCE SARL : Responsable Recouvrement + Chef d'Agence
--
-- Crée les deux comptes manquants et lie l'intégralité
-- des données existantes de FINANCE SARL :
--
--   renekomtsindi01@gmail.com → RESPONSABLE_RECOUVREMENT
--     · dossiers_recouvrement (CLF021-CLF030, clients PAR)
--     · actions_recouvrement historiques (appels, visites, mises en demeure)
--     · alertes_impayes déjà scopées à l'IMF (V47)
--     · creances déjà scopées à l'IMF (V48)
--
--   renekomtsindi00@gmail.com → CHEF_AGENCE
--     · dossiers_credit EN_COMITE (5 nouveaux dossiers à valider)
--     · chef_agence_id mis à jour sur les dossiers existants
--     · accord_reechelonnement pour CLF027 (approuvé par le chef)
--
-- Mot de passe : admin123
-- ============================================================

DO $$
DECLARE
    v_imf_id   BIGINT;
    v_ag_yde   BIGINT;
    v_ag_dla   BIGINT;
    v_agent_id BIGINT;   -- renekomtsindi99 (agent terrain existant)
    v_resp_id  BIGINT;   -- renekomtsindi01 (nouveau RESPONSABLE_RECOUVREMENT)
    v_chef_id  BIGINT;   -- renekomtsindi00 (nouveau CHEF_AGENCE)
    v_dos_id   BIGINT;
    v_cl       RECORD;

BEGIN
    -- ── 0. Résolution FINANCE SARL ────────────────────────────────────────
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'V55 : FINANCE SARL introuvable — migration ignorée';
        RETURN;
    END IF;

    SELECT id INTO v_ag_yde FROM app.agences
        WHERE imf_id = v_imf_id ORDER BY id LIMIT 1;
    SELECT id INTO v_ag_dla FROM app.agences
        WHERE imf_id = v_imf_id AND nom ILIKE '%Douala%' ORDER BY id LIMIT 1;
    IF v_ag_dla IS NULL THEN v_ag_dla := v_ag_yde; END IF;

    SELECT id INTO v_agent_id FROM app.utilisateurs
        WHERE email = 'renekomtsindi99@gmail.com' AND imf_id = v_imf_id LIMIT 1;

    -- ── 1. Créer le RESPONSABLE_RECOUVREMENT ─────────────────────────────
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         must_change_password, created_at, updated_at)
    SELECT 'resp.recouvrement',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'RESPONSABLE_RECOUVREMENT', 'renekomtsindi01@gmail.com',
           v_imf_id, TRUE, FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'resp.recouvrement' OR email = 'renekomtsindi01@gmail.com'
    );

    SELECT id INTO v_resp_id FROM app.utilisateurs
        WHERE email = 'renekomtsindi01@gmail.com' LIMIT 1;

    -- ── 2. Créer le CHEF_AGENCE ───────────────────────────────────────────
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         must_change_password, created_at, updated_at)
    SELECT 'chef.agence.finance',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'CHEF_AGENCE', 'renekomtsindi00@gmail.com',
           v_imf_id, TRUE, FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'chef.agence.finance' OR email = 'renekomtsindi00@gmail.com'
    );

    SELECT id INTO v_chef_id FROM app.utilisateurs
        WHERE email = 'renekomtsindi00@gmail.com' LIMIT 1;

    RAISE NOTICE 'V55 — imf=% resp=% chef=% agent=%',
        v_imf_id, v_resp_id, v_chef_id, v_agent_id;

    -- ──────────────────────────────────────────────────────────────────────
    -- 3. DOSSIERS_RECOUVREMENT pour CLF021-CLF030 (clients PAR existants)
    --    Données alignées sur alertes_impayes insérées en V47
    --    + creances insérées en V48
    -- ──────────────────────────────────────────────────────────────────────
    FOR v_cl IN
        SELECT vals.cid,
               vals.jours,
               vals.montant,
               vals.phase,
               vals.cobac,
               vals.taux,
               vals.nom_caution,
               vals.tel_caution,
               vals.garantie
        FROM (VALUES
            ('CLF021', 38, 126000.00::NUMERIC, 'RELANCE_AMIABLE',   'EN_SURVEILLANCE',  5.00::NUMERIC, 'NFON Clémentine',   '690445001', 'CAUTION_SOLIDAIRE'),
            ('CLF022', 45, 218000.00,           'RELANCE_AMIABLE',   'DOUTEUSE',        25.00,          'MBARGA Théodore',   '677552002', 'CAUTION_SOLIDAIRE'),
            ('CLF023', 35, 148000.00,           'RELANCE_AMIABLE',   'EN_SURVEILLANCE',  5.00,          'FOUDA Angeline',    '655443003', 'NANTISSEMENT'),
            ('CLF024', 62, 188000.00,           'MEDIATION_AMIABLE', 'DOUTEUSE',        25.00,          'TCHANGANI Paul',    '697334004', 'CAUTION_SOLIDAIRE'),
            ('CLF025', 41, 158000.00,           'RELANCE_AMIABLE',   'DOUTEUSE',        25.00,          'NGUELE Samuel',     '655445005', 'NANTISSEMENT'),
            ('CLF026', 37, 172000.00,           'RELANCE_AMIABLE',   'EN_SURVEILLANCE',  5.00,          'ABANDA Marie',      '690116006', 'CAUTION_SOLIDAIRE'),
            ('CLF027', 55, 108000.00,           'REECHELONNEMENT',   'DOUTEUSE',        25.00,          'BIKELE Hortense',   '677227007', 'NANTISSEMENT'),
            ('CLF028', 96, 556500.00,           'MISE_EN_DEMEURE',   'LITIGIEUSE',      50.00,          'NKWENTI Bridget',   '690441001', 'HYPOTHEQUE'),
            ('CLF029', 96, 760500.00,           'MISE_EN_DEMEURE',   'LITIGIEUSE',      50.00,          'MBARGA Théodore',   '677552002', 'HYPOTHEQUE'),
            ('CLF030', 96, 675000.00,           'MISE_EN_DEMEURE',   'LITIGIEUSE',      50.00,          'FOUDA Angeline',    '655443003', 'CAUTION_SOLIDAIRE')
        ) AS vals(cid, jours, montant, phase, cobac, taux, nom_caution, tel_caution, garantie)
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM app.dossiers_recouvrement
            WHERE imf_id = v_imf_id AND id_pret = v_cl.cid
        ) THEN
            INSERT INTO app.dossiers_recouvrement
                (imf_id, id_pret, nom_client,
                 montant_impaye, jours_retard,
                 phase, categorie_cobtac, taux_provision, montant_provision,
                 date_premiere_echeance_impayee,
                 nom_caution, telephone_caution, type_garantie, frais_recouvrement,
                 agent_responsable_id,
                 date_ouverture, clos, created_at, updated_at)
            VALUES (
                v_imf_id,
                v_cl.cid,
                COALESCE(
                    (SELECT nom_complet FROM app.clients_informels
                     WHERE imf_id = v_imf_id AND client_id_externe = v_cl.cid LIMIT 1),
                    'Client ' || v_cl.cid
                ),
                v_cl.montant,
                v_cl.jours,
                v_cl.phase,
                v_cl.cobac,
                v_cl.taux,
                ROUND(v_cl.montant * v_cl.taux / 100.0, 2),
                CURRENT_DATE - (v_cl.jours || ' days')::INTERVAL,
                v_cl.nom_caution,
                v_cl.tel_caution,
                v_cl.garantie,
                0.00,
                v_resp_id,
                NOW() - (v_cl.jours || ' days')::INTERVAL,
                FALSE,
                NOW(),
                NOW()
            );
        END IF;
    END LOOP;

    -- ──────────────────────────────────────────────────────────────────────
    -- 4. ACTIONS_RECOUVREMENT historiques
    --    Appel initial pour tous les dossiers
    --    Visite terrain pour les dossiers > 45j
    --    Mise en demeure pour les dossiers > 90j
    -- ──────────────────────────────────────────────────────────────────────
    FOR v_cl IN
        SELECT dr.id, dr.jours_retard, dr.id_pret
        FROM app.dossiers_recouvrement dr
        WHERE dr.imf_id = v_imf_id
          AND NOT EXISTS (
              SELECT 1 FROM app.actions_recouvrement ar
              WHERE ar.dossier_id = dr.id
          )
    LOOP
        -- Action 1 : Appel téléphonique initial
        INSERT INTO app.actions_recouvrement
            (dossier_id, type_action, date_action, agent_id, resultat,
             canal, observation, frais_engages, created_at)
        VALUES (
            v_cl.id,
            'APPEL_TELEPHONIQUE',
            NOW() - ((v_cl.jours_retard - 3) || ' days')::INTERVAL,
            v_resp_id,
            'CONTACT_ETABLI',
            'TELEPHONE',
            'Client informé du retard de paiement. Promet de régulariser sous 15 jours.',
            0.00,
            NOW()
        );

        -- Action 2 : Visite terrain si > 45 jours
        IF v_cl.jours_retard > 45 THEN
            INSERT INTO app.actions_recouvrement
                (dossier_id, type_action, date_action, agent_id, resultat,
                 canal, observation, frais_engages, created_at)
            VALUES (
                v_cl.id,
                'VISITE_TERRAIN',
                NOW() - ((v_cl.jours_retard - 20) || ' days')::INTERVAL,
                v_resp_id,
                'PROMESSE_PAIEMENT',
                'TERRAIN',
                'Visite au domicile du client. Promesse de versement partiel avant fin du mois.',
                5000.00,
                NOW()
            );
        END IF;

        -- Action 3 : Mise en demeure formelle si > 90 jours
        IF v_cl.jours_retard > 90 THEN
            INSERT INTO app.actions_recouvrement
                (dossier_id, type_action, date_action, agent_id, resultat,
                 canal, observation, frais_engages, created_at)
            VALUES (
                v_cl.id,
                'MISE_EN_DEMEURE',
                NOW() - ((v_cl.jours_retard - 35) || ' days')::INTERVAL,
                v_resp_id,
                'LETTRE_ENVOYEE',
                'COURRIER',
                'Mise en demeure transmise par voie d''huissier (OHADA art. 110 AUPSRVE). '
                    || 'Délai de 30 jours accordé pour régularisation totale.',
                25000.00,
                NOW()
            );
        END IF;
    END LOOP;

    -- ──────────────────────────────────────────────────────────────────────
    -- 5. ACCORD RÉÉCHELONNEMENT pour CLF027 (phase REECHELONNEMENT)
    -- ──────────────────────────────────────────────────────────────────────
    SELECT id INTO v_dos_id
    FROM app.dossiers_recouvrement
    WHERE imf_id = v_imf_id AND id_pret = 'CLF027' LIMIT 1;

    IF v_dos_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM app.accords_reechelonnement WHERE dossier_id = v_dos_id
    ) THEN
        INSERT INTO app.accords_reechelonnement
            (dossier_id, nouveau_montant_mensuel, nombre_nouvelles_echeances,
             date_debut_nouvel_echeancier, taux_interet_annuel,
             approuve_par_id, date_signature, observations, actif, created_at)
        VALUES (
            v_dos_id,
            10800.00,   -- 108 000 / 10 échéances
            10,
            CURRENT_DATE + INTERVAL '7 days',
            18.00,
            v_chef_id,
            CURRENT_DATE - INTERVAL '2 days',
            'Accord signé par le client et le chef d''agence. '
                || 'Plan de remboursement sur 10 mois à partir du ' ||
                (CURRENT_DATE + INTERVAL '7 days')::TEXT || '.',
            TRUE,
            NOW()
        );

        -- Mettre à jour la date de dernière action du dossier
        UPDATE app.dossiers_recouvrement
        SET date_derniere_action = NOW() - INTERVAL '2 days',
            updated_at           = NOW()
        WHERE id = v_dos_id;
    END IF;

    -- ──────────────────────────────────────────────────────────────────────
    -- 6. DOSSIERS_CREDIT EN_COMITE pour la page chef d'agence
    --    5 nouveaux dossiers soumis par l'agent existant, en attente
    --    de validation par le chef d'agence
    -- ──────────────────────────────────────────────────────────────────────
    IF v_chef_id IS NOT NULL AND v_agent_id IS NOT NULL THEN
        INSERT INTO app.dossiers_credit
            (imf_id, agence_id, agent_credit_id, chef_agence_id,
             client_id, client_nom, montant_demande, duree_mois,
             objet_financement, secteur_activite,
             revenu_estime, charges_mensuelles, capacite_remboursement,
             statut, date_soumission, created_at, updated_at)
        SELECT v_imf_id,
               CASE WHEN vals.ag = 'YDE' THEN v_ag_yde ELSE v_ag_dla END,
               v_agent_id, v_chef_id,
               vals.cid, vals.nom,
               vals.montant, vals.duree,
               vals.objet, vals.secteur,
               vals.revenu, vals.charges,
               (vals.revenu - vals.charges),
               'EN_COMITE',
               NOW() - (vals.j || ' days')::INTERVAL,
               NOW(), NOW()
        FROM (VALUES
            ('CLF031', 'NKWENTI Bridget',  'YDE', 450000::NUMERIC, 12, 'Vente de denrées alimentaires',   'COMMERCE',   145000::NUMERIC, 55000::NUMERIC, 3),
            ('CLF032', 'MBARGA Théodore',  'YDE', 280000,           9, 'Maraîchage urbain',                'AGRICOLE',    85000,          30000,          5),
            ('CLF033', 'FOUDA Angeline',   'DLA', 620000,          18, 'Salon de coiffure — Makepe',       'SERVICES',   165000,          65000,          7),
            ('CLF034', 'TCHANGANI Paul',   'DLA', 380000,          12, 'Transport urbain (taxi)',           'TRANSPORT',  120000,          40000,          4),
            ('CLF035', 'NGUELE Samuel',    'YDE', 195000,           6, 'Vente de meubles artisanaux',      'COMMERCE',    80000,          28000,          2)
        ) AS vals(cid, nom, ag, montant, duree, objet, secteur, revenu, charges, j)
        WHERE NOT EXISTS (
            SELECT 1 FROM app.dossiers_credit dc
            WHERE dc.imf_id = v_imf_id
              AND dc.client_id = vals.cid
              AND dc.statut = 'EN_COMITE'
        );
    END IF;

    -- ──────────────────────────────────────────────────────────────────────
    -- 7. Lier chef_agence_id aux dossiers existants sans chef
    --    (dossiers DEBLOQUE / APPROUVE / VALIDE déjà présents)
    -- ──────────────────────────────────────────────────────────────────────
    IF v_chef_id IS NOT NULL THEN
        UPDATE app.dossiers_credit
        SET chef_agence_id = v_chef_id,
            updated_at     = NOW()
        WHERE imf_id       = v_imf_id
          AND chef_agence_id IS NULL
          AND statut IN ('VALIDE', 'APPROUVE', 'DEBLOQUE', 'AJOURNE');
    END IF;

    -- ──────────────────────────────────────────────────────────────────────
    -- 8. Lier agent_responsable_id du responsable recouvrement
    --    aux dossiers_recouvrement orphelins (agent_responsable_id IS NULL)
    -- ──────────────────────────────────────────────────────────────────────
    IF v_resp_id IS NOT NULL THEN
        UPDATE app.dossiers_recouvrement
        SET agent_responsable_id = v_resp_id,
            updated_at           = NOW()
        WHERE imf_id              = v_imf_id
          AND agent_responsable_id IS NULL;
    END IF;

    -- ──────────────────────────────────────────────────────────────────────
    -- 9. Scores MCRS pour les clients CLF031-CLF035 (dossiers EN_COMITE)
    --    Permet à l'analyste de voir le scoring sur ces nouveaux dossiers
    -- ──────────────────────────────────────────────────────────────────────
    INSERT INTO ml.client_scores
        (imf_id, client_id_externe, score_crs, score_rps, score_csi,
         score_mcrs, niveau_risque, model_version, scored_at)
    SELECT v_imf_id, vals.cid,
           vals.crs, vals.rps, vals.csi,
           ROUND(vals.crs * 0.35 + vals.rps * 0.45 + vals.csi * 0.20, 4),
           CASE
               WHEN ROUND(vals.crs * 0.35 + vals.rps * 0.45 + vals.csi * 0.20, 4) >= 0.75 THEN 'CRITIQUE'
               WHEN ROUND(vals.crs * 0.35 + vals.rps * 0.45 + vals.csi * 0.20, 4) >= 0.55 THEN 'ELEVE'
               WHEN ROUND(vals.crs * 0.35 + vals.rps * 0.45 + vals.csi * 0.20, 4) >= 0.30 THEN 'MODERE'
               ELSE 'FAIBLE'
           END,
           'MCRS-v2.4.1',
           NOW()
    FROM (VALUES
        ('CLF031', 0.22::NUMERIC, 0.18::NUMERIC, 0.25::NUMERIC),
        ('CLF032', 0.31,          0.27,           0.35),
        ('CLF033', 0.19,          0.15,           0.22),
        ('CLF034', 0.44,          0.38,           0.41),
        ('CLF035', 0.28,          0.24,           0.30)
    ) AS vals(cid, crs, rps, csi)
    WHERE NOT EXISTS (
        SELECT 1 FROM ml.client_scores cs
        WHERE cs.imf_id = v_imf_id AND cs.client_id_externe = vals.cid
    );

    RAISE NOTICE 'V55 OK — FINANCE SARL : resp_recouvrement=% chef_agence=% liés à toutes les données',
        v_resp_id, v_chef_id;

END $$;
