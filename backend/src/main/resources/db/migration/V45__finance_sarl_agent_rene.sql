-- ============================================================
-- V45 — FINANCE SARL + agent Réné KOMTSINDI + 30 clients
--        Données historiques N-1 (janvier–décembre 2025)
--        Agent : renekomtsindi99@gmail.com
--        Mot de passe de tous les utilisateurs démo : admin123
-- ============================================================

-- ── 1. IMF FINANCE SARL ───────────────────────────────────────────────────────
INSERT INTO app.imf (
    code, nom, denomination_sociale, pays,
    adresse_siege, forme_juridique, capital_social, num_agrement,
    telephone, email,
    taux_interet_annuel, duree_max_credit_mois, taux_penalite_retard,
    seuil_relance_jours, taux_epargne, solde_min_epargne, frais_tenue_compte,
    segments_clients, types_garanties, actif
) VALUES (
    'FINANCE',
    'FINANCE SARL',
    'FINANCE MICROFINANCE SARL',
    'Cameroun',
    'Rue Njoné, BP 3421, Yaoundé, Centre',
    'SARL',
    75000000.00,
    'COBAC/EMF/2020-218',
    '+237 222 31 44 55',
    'direction@finance-mf.cm',
    18.00, 36, 3.00,
    30, 5.00, 10000.00, 500.00,
    'Commerçants informels, Agriculteurs, Petits entrepreneurs, Artisans',
    'Caution solidaire, Nantissement, Hypothèque mobilière',
    TRUE
)
ON CONFLICT (code) DO NOTHING;

DO $$
DECLARE
    v_imf_id   BIGINT;
    v_ag_yde   BIGINT;
    v_ag_dla   BIGINT;
    v_agent    BIGINT;
    v_dsi      BIGINT;
    v_dir      BIGINT;
    v_cycle_id BIGINT;
    v_mois     INT;
    v_client   RECORD;
    v_nb_clt   INT := 0;

BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';

    -- ── 2. Agences ────────────────────────────────────────────────────────────
    INSERT INTO app.agences (imf_id, nom, ville, responsable, telephone, actif)
    VALUES
        (v_imf_id, 'Agence Yaoundé Nlongkak', 'Yaoundé', 'M. Réné KOMTSINDI',   '+237 699 12 34 56', TRUE),
        (v_imf_id, 'Agence Douala Bassa',     'Douala',  'Mme Yvonne MBATCHOU', '+237 677 98 00 12', TRUE)
    ON CONFLICT ON CONSTRAINT uq_agence_imf_nom DO NOTHING;

    SELECT id INTO v_ag_yde FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Yaoundé Nlongkak';
    SELECT id INTO v_ag_dla FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Douala Bassa';

    -- ── 3. Utilisateurs ───────────────────────────────────────────────────────
    -- ON CONFLICT (username) ne couvre pas l'index unique sur email :
    -- on insère chaque utilisateur séparément en vérifiant username ET email.
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'rene.komtsindi', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'renekomtsindi99@gmail.com', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'rene.komtsindi' OR email = 'renekomtsindi99@gmail.com'
    );

    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'dsi.finance', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'DSI', 'dsi@finance-mf.cm', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'dsi.finance' OR email = 'dsi@finance-mf.cm'
    );

    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif, must_change_password)
    SELECT 'dir.finance', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'DIRECTEUR', 'directeur@finance-mf.cm', v_imf_id, TRUE, FALSE
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs
        WHERE username = 'dir.finance' OR email = 'directeur@finance-mf.cm'
    );

    SELECT id INTO v_agent FROM app.utilisateurs
    WHERE (username = 'rene.komtsindi' OR email = 'renekomtsindi99@gmail.com') LIMIT 1;
    SELECT id INTO v_dsi   FROM app.utilisateurs
    WHERE (username = 'dsi.finance'    OR email = 'dsi@finance-mf.cm')         LIMIT 1;
    SELECT id INTO v_dir   FROM app.utilisateurs
    WHERE (username = 'dir.finance'    OR email = 'directeur@finance-mf.cm')   LIMIT 1;

    -- ── 4. Cycle de collecte 2025 ─────────────────────────────────────────────
    INSERT INTO app.cycles_collecte (imf_id, agence_id, nom_cycle, periodicite,
        date_debut, objectif_montant, objectif_nb_transactions, actif)
    VALUES (v_imf_id, v_ag_yde, 'Cycle Mensuel Finance 2025', 'MENSUEL',
            '2025-01-01', 600000000, 1200, TRUE)
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_cycle_id FROM app.cycles_collecte
    WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Mensuel Finance 2025';

    -- ── 5. 30 clients informels ───────────────────────────────────────────────
    INSERT INTO app.clients_informels (imf_id, client_id_externe, nom_complet, telephone_principal,
        zone_id, agence_id, date_naissance, sexe,
        secteur_principal, sous_secteur, annees_experience,
        revenu_mensuel_estime, marche_principal, frequence_marche,
        niveau_education, situation_familiale, nombre_personnes_charge,
        latitude_activite, longitude_activite, adresse_activite
    ) VALUES
        -- Bon payeurs (CLF001–CLF020)
        (v_imf_id,'CLF001','Astride FOUDA BIYONG',    '+237 655 10 20 30','YDE-NLONGKAK',v_ag_yde,'1985-03-12','F','COMMERCE','Vente de vivres frais',       9, 175000,'Marché Mvog-Ada',       'QUOTIDIEN','SECONDAIRE','MARIE',      4,3.870000,11.518000,'Marché Mvog-Ada, Yaoundé'),
        (v_imf_id,'CLF002','Gaston NKEMBI OWONA',     '+237 677 21 32 43','YDE-NLONGKAK',v_ag_yde,'1978-07-22','M','ARTISANAT','Menuiserie bois',             13,230000,'Zone artisanale Mvolyé','HEBDOMADAIRE','SECONDAIRE','MARIE',      5,3.862000,11.512000,'Atelier menuiserie Mvolyé'),
        (v_imf_id,'CLF003','Rosalie ONANA NGA',       '+237 699 33 44 55','YDE-SUD',     v_ag_yde,'1991-11-08','F','AGRICOLE', 'Maraîchage',                  6, 118000,'Marché Melen',          'HEBDOMADAIRE','PRIMAIRE', 'CELIBATAIRE', 2,3.840000,11.500000,'Champ Nkol-Bisson'),
        (v_imf_id,'CLF004','Edmond ATANGANA BEBE',    '+237 670 44 55 66','YDE-NLONGKAK',v_ag_yde,'1979-05-30','M','TRANSPORT','Moto-taxi',                   11,315000,NULL,                   'QUOTIDIEN','SECONDAIRE','MARIE',      6,3.875000,11.520000,'Carrefour Nlongkak'),
        (v_imf_id,'CLF005','Solange MBARGA ELANGA',   '+237 655 55 66 77','YDE-EST',     v_ag_yde,'1987-09-14','F','COMMERCE','Salon de coiffure',            8, 162000,'Quartier Efoulan',      'QUOTIDIEN','SECONDAIRE','MARIE',      3,3.880000,11.530000,'Avenue Kennedy, Yaoundé'),
        (v_imf_id,'CLF006','Augustin NDZANA ONANA',   '+237 690 66 77 88','DLA-BASSA',   v_ag_dla,'1973-12-01','M','COMMERCE','Commerce général',             16,285000,'Marché Bassa',          'QUOTIDIEN','SECONDAIRE','MARIE',      7,4.052000, 9.720000,'Marché Bassa, Douala'),
        (v_imf_id,'CLF007','Nadège ETOUNDI BEYALA',   '+237 677 77 88 99','DLA-BASSA',   v_ag_dla,'1994-04-25','F','AGRICOLE', 'Aviculture',                  4, 148000,'Marché Sandaga',        'HEBDOMADAIRE','PRIMAIRE', 'CELIBATAIRE', 1,4.055000, 9.720000,'Bassa Nord, Douala'),
        (v_imf_id,'CLF008','Valentin TABI ESSOMBA',   '+237 699 88 99 00','DLA-BASSA',   v_ag_dla,'1981-08-17','M','PECHE',    'Pêche artisanale',            11,198000,'Port de Bassa',         'QUOTIDIEN','PRIMAIRE', 'MARIE',      5,4.065000, 9.710000,'Port Bassa, Douala'),
        (v_imf_id,'CLF009','Clarisse MENDO OWONO',    '+237 655 99 00 11','YDE-NORD',    v_ag_yde,'1989-02-28','F','ARTISANAT','Tissage raphia',               7, 132000,'Marché central Ydé',   'BIMENSUEL','SECONDAIRE','MARIE',      4,3.892000,11.506000,'Briqueterie, Yaoundé'),
        (v_imf_id,'CLF010','Joachim BELINGA NKOA',    '+237 670 00 11 22','YDE-NLONGKAK',v_ag_yde,'1971-06-10','M','SERVICES', 'Réparation électroménager',   19,245000,'Rue Nachtigal',         'QUOTIDIEN','SECONDAIRE','MARIE',      6,3.866000,11.514000,'Messa, Yaoundé'),
        (v_imf_id,'CLF011','Laurette KOUMBA MVOGO',   '+237 698 11 22 33','DLA-BASSA',   v_ag_dla,'1986-10-20','F','COMMERCE','Prêt-à-porter',                10,215000,'Marché Central Douala', 'QUOTIDIEN','SECONDAIRE','MARIE',      3,4.048000, 9.700000,'Akwa, Douala'),
        (v_imf_id,'CLF012','Blaise ABENA FOUDA',      '+237 675 22 33 44','YDE-NLONGKAK',v_ag_yde,'1966-03-05','M','ELEVAGE',  'Porciculture',                15,325000,'Ferme Nkolbisson',      'MENSUEL','PRIMAIRE', 'MARIE',      8,3.845000,11.490000,'Nkolbisson, Yaoundé'),
        (v_imf_id,'CLF013','Delphine ETOA BIYONG',    '+237 655 33 44 55','YDE-NLONGKAK',v_ag_yde,'1995-07-14','F','COMMERCE','Restaurant de rue',            5, 168000,'Carrefour Elig-Essono', 'QUOTIDIEN','SECONDAIRE','CELIBATAIRE', 2,3.876000,11.522000,'Elig-Essono, Yaoundé'),
        (v_imf_id,'CLF014','Bruno ZANG ONANA',        '+237 699 44 55 66','DLA-BASSA',   v_ag_dla,'1984-11-30','M','TRANSPORT','Transport de marchandises',   13,385000,NULL,                   'QUOTIDIEN','SECONDAIRE','MARIE',      4,4.078000, 9.670000,'Bassa, Douala'),
        (v_imf_id,'CLF015','Victorine NGUELE MBIDA',  '+237 677 55 66 77','YDE-SUD',     v_ag_yde,'1977-04-18','F','AGRICOLE', 'Culture maïs-plantain',       16,180000,'Marché Madagascar',     'HEBDOMADAIRE','AUCUN',    'VEUF',       5,3.836000,11.498000,'Soa, Yaoundé'),
        (v_imf_id,'CLF016','Serge AWONO ESSANG',      '+237 655 66 77 88','YDE-NLONGKAK',v_ag_yde,'1988-08-22','M','ARTISANAT','Soudure',                     12,208000,'Zone industrielle',     'HEBDOMADAIRE','SECONDAIRE','MARIE',      3,3.858000,11.508000,'Ntaba, Yaoundé'),
        (v_imf_id,'CLF017','Marthe MBIDA ATANGANA',   '+237 690 77 88 99','DLA-BASSA',   v_ag_dla,'1992-01-09','F','COMMERCE','Vente cosmétiques',            6, 143000,'Marché New-Bell',       'QUOTIDIEN','SECONDAIRE','CELIBATAIRE', 1,4.042000, 9.710000,'New-Bell, Douala'),
        (v_imf_id,'CLF018','Aristide TIOKOU BELLO',   '+237 677 88 99 00','YDE-EST',     v_ag_yde,'1980-05-16','M','SERVICES', 'Photocopie / Internet café',  11,198000,'Campus Univ. Ydé I',   'QUOTIDIEN','SUPERIEUR','MARIE',      4,3.884000,11.526000,'Ngoa-Ekele, Yaoundé'),
        (v_imf_id,'CLF019','Cécile ABONDO NGONO',     '+237 699 99 00 11','DLA-BASSA',   v_ag_dla,'1969-09-03','F','ELEVAGE',  'Aviculture industrielle',     21,455000,'Ferme Logpom',          'MENSUEL','SECONDAIRE','VEUF',       3,4.060000, 9.732000,'Logpom, Douala'),
        (v_imf_id,'CLF020','Pascal BELINGA AKOUMA',   '+237 655 00 11 22','YDE-NORD',    v_ag_yde,'1985-12-25','M','TRANSPORT','Taxi inter-urbain',           10,292000,NULL,                   'QUOTIDIEN','SECONDAIRE','MARIE',      5,3.898000,11.512000,'Obili, Yaoundé'),
        -- Clients à surveiller (CLF021–CLF027)
        (v_imf_id,'CLF021','Henriette MEDJO ABENA',   '+237 670 11 22 33','YDE-NLONGKAK',v_ag_yde,'1997-06-11','F','COMMERCE','Vente de poisson fumé',        5, 126000,'Marché Mvog-Ada',       'QUOTIDIEN','PRIMAIRE', 'CELIBATAIRE', 1,3.863000,11.517000,'Mvog-Ada, Yaoundé'),
        (v_imf_id,'CLF022','Fidèle OWONO NGUEMA',     '+237 699 22 33 44','DLA-BASSA',   v_ag_dla,'1972-02-14','M','ARTISANAT','Tailleur confection',         19,218000,'Marché Sandaga',        'QUOTIDIEN','SECONDAIRE','MARIE',      6,4.047000, 9.705000,'Bonanjo, Douala'),
        (v_imf_id,'CLF023','Gisèle NKOA BEYALA',      '+237 677 33 44 55','YDE-NLONGKAK',v_ag_yde,'1990-10-07','F','SERVICES', 'Papeterie / photocopie',      7, 148000,'Centre-ville Yaoundé', 'QUOTIDIEN','SECONDAIRE','MARIE',      2,3.868000,11.516000,'Bastos, Yaoundé'),
        (v_imf_id,'CLF024','Dieudonné MVOGO ESSOLA',  '+237 655 44 55 66','YDE-SUD',     v_ag_yde,'1978-07-19','M','PECHE',    'Pisciculture',                11,188000,'Rivière Mfoundi',       'MENSUEL','PRIMAIRE', 'MARIE',      7,3.842000,11.495000,'Djoungolo, Yaoundé'),
        (v_imf_id,'CLF025','Annette EBONGUE MENDO',   '+237 690 55 66 77','DLA-BASSA',   v_ag_dla,'1993-03-28','F','COMMERCE','Épicerie',                     6, 158000,'Quartier Bepanda',      'QUOTIDIEN','SECONDAIRE','MARIE',      3,4.074000, 9.692000,'Bepanda, Douala'),
        (v_imf_id,'CLF026','Norbert ESSAMA BIYONG',   '+237 677 66 77 88','YDE-NLONGKAK',v_ag_yde,'1982-09-03','M','COMMERCE','Téléphonie mobile',           10,172000,'Carrefour Nlongkak',    'QUOTIDIEN','SECONDAIRE','MARIE',      4,3.872000,11.519000,'Nlongkak, Yaoundé'),
        (v_imf_id,'CLF027','Patience KENGNE FOUDA',   '+237 699 77 88 99','DLA-BASSA',   v_ag_dla,'1996-12-15','F','AGRICOLE', 'Maraîchage / jardinage',      4, 108000,'Marché Bassa',          'HEBDOMADAIRE','PRIMAIRE', 'CELIBATAIRE', 1,4.053000, 9.718000,'Bassa Sud, Douala'),
        -- Clients à risque (CLF028–CLF030)
        (v_imf_id,'CLF028','Théodore NKEMBI MVONDO',  '+237 655 88 99 00','YDE-EST',     v_ag_yde,'1975-04-20','M','TRANSPORT','Transport informel',           8, 142000,NULL,                   'QUOTIDIEN','SECONDAIRE','MARIE',      5,3.882000,11.524000,'Nkol-Afeme, Yaoundé'),
        (v_imf_id,'CLF029','Alice FOUDA ATANGANA',    '+237 677 99 00 11','DLA-BASSA',   v_ag_dla,'1988-08-08','F','COMMERCE','Vente en gros vivriers',      7, 195000,'Marché Bassa',          'QUOTIDIEN','SECONDAIRE','MARIE',      6,4.056000, 9.715000,'Bassa Centre, Douala'),
        (v_imf_id,'CLF030','Rodrigue ONANA BELINGA',  '+237 699 00 11 22','YDE-NLONGKAK',v_ag_yde,'1983-02-14','M','ARTISANAT','Mécanique auto',              9, 225000,'Garage Nlongkak',       'QUOTIDIEN','SECONDAIRE','MARIE',      3,3.869000,11.521000,'Nlongkak Garage, Yaoundé')
    ON CONFLICT DO NOTHING;

    -- ── 6. Collectes épargne 2025 (12 mois) ─────────────────────────────────
    -- Clients bon payeurs (CLF001–CLF020) : collectes régulières, 1 à 2 / mois
    FOR v_mois IN 1..12 LOOP
        INSERT INTO app.collectes_epargne (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
             client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
        SELECT
            gen_random_uuid(),
            v_imf_id, v_ag_yde, v_cycle_id, v_agent,
            'CLF' || LPAD(n::TEXT, 3, '0'),
            (20000 + ((n * 3711 + v_mois * 1234) % 60000))::NUMERIC,
            MAKE_DATE(2025, v_mois, LEAST(28, 5 + (n * 7 % 20))),
            CASE WHEN (n + v_mois) % 4 = 0 THEN 'MTN'
                 WHEN (n + v_mois) % 4 = 1 THEN 'ORANGE'
                 WHEN (n + v_mois) % 4 = 2 THEN 'ESPECES'
                 ELSE 'VIREMENT' END,
            'VALIDEE'
        FROM generate_series(1, 20) AS n
        ON CONFLICT DO NOTHING;
    END LOOP;

    -- 2e collecte mensuelle pour les meilleurs clients (CLF001–CLF010)
    FOR v_mois IN 1..12 LOOP
        INSERT INTO app.collectes_epargne (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
             client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
        SELECT
            gen_random_uuid(),
            v_imf_id, v_ag_yde, v_cycle_id, v_agent,
            'CLF' || LPAD(n::TEXT, 3, '0'),
            (15000 + ((n * 2233 + v_mois * 5678) % 40000))::NUMERIC,
            MAKE_DATE(2025, v_mois, LEAST(28, 18 + (n * 3 % 9))),
            CASE WHEN (n + v_mois) % 3 = 0 THEN 'MTN' WHEN (n + v_mois) % 3 = 1 THEN 'ORANGE' ELSE 'ESPECES' END,
            'VALIDEE'
        FROM generate_series(1, 10) AS n
        ON CONFLICT DO NOTHING;
    END LOOP;

    -- Clients à surveiller (CLF021–CLF027) : collectes irrégulières (gaps)
    FOR v_mois IN 1..12 LOOP
        IF v_mois NOT IN (3, 7, 10) THEN  -- manquent 3 mois sur 12
            INSERT INTO app.collectes_epargne (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
                 client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
            SELECT
                gen_random_uuid(),
                v_imf_id, v_ag_dla, v_cycle_id, v_agent,
                'CLF' || LPAD(n::TEXT, 3, '0'),
                (10000 + ((n * 4123 + v_mois * 891) % 35000))::NUMERIC,
                MAKE_DATE(2025, v_mois, LEAST(28, 10 + (n * 5 % 15))),
                CASE WHEN (n + v_mois) % 3 = 0 THEN 'MTN' ELSE 'ESPECES' END,
                CASE WHEN (n + v_mois) % 8 = 0 THEN 'REJETEE' ELSE 'VALIDEE' END
            FROM generate_series(21, 27) AS n
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;

    -- Clients à risque (CLF028–CLF030) : très peu de collectes
    FOR v_mois IN 1..12 LOOP
        IF v_mois IN (1, 4, 8, 11) THEN  -- seulement 4 mois sur 12
            INSERT INTO app.collectes_epargne (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
                 client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
            SELECT
                gen_random_uuid(),
                v_imf_id, v_ag_yde, v_cycle_id, v_agent,
                'CLF' || LPAD(n::TEXT, 3, '0'),
                (8000 + ((n * 1987 + v_mois * 456) % 22000))::NUMERIC,
                MAKE_DATE(2025, v_mois, 15),
                'ESPECES',
                'VALIDEE'
            FROM generate_series(28, 30) AS n
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;

    -- ── 7. Créances avec historique N-1 ──────────────────────────────────────
    -- 20 prêts actifs (bon payeurs) — décaissés en 2025, en cours
    INSERT INTO app.creances (imf_id, agence_id, id_pret_externe, client_id_externe, client_informel_id,
        montant_initial, montant_impaye, capital_restant_du, interets_retard, penalites,
        date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
        date_ouverture_creance, jours_retard, categorie_par, classe_risque_cobac,
        taux_provision_cobac, montant_provision, type_garantie, valeur_garantie
    )
    SELECT
        v_imf_id,
        CASE WHEN ci.zone_id LIKE 'YDE%' THEN v_ag_yde ELSE v_ag_dla END,
        'PFIN-2025-' || LPAD(ci.id::TEXT, 4, '0'),
        ci.client_id_externe,
        ci.id,
        -- Montants cohérents avec le revenu
        ROUND(ci.revenu_mensuel_estime * 8, 0),
        0,
        ROUND(ci.revenu_mensuel_estime * 8 * 0.45, 0),  -- ~55% remboursé
        0, 0,
        '2025-01-15'::DATE,
        '2025-02-15'::DATE,
        NULL,
        CURRENT_DATE,
        0,
        'COURANT', 'A', 0.00, 0.00,
        'CAUTION_SOLIDAIRE',
        ROUND(ci.revenu_mensuel_estime * 10, 0)
    FROM app.clients_informels ci
    WHERE ci.imf_id = v_imf_id
      AND ci.client_id_externe IN (
          'CLF001','CLF002','CLF003','CLF004','CLF005',
          'CLF006','CLF007','CLF008','CLF009','CLF010',
          'CLF011','CLF012','CLF013','CLF014','CLF015',
          'CLF016','CLF017','CLF018','CLF019','CLF020')
    ON CONFLICT DO NOTHING;

    -- 4 prêts clients à surveiller (CLF021–CLF024) — légèrement en retard
    INSERT INTO app.creances (imf_id, agence_id, id_pret_externe, client_id_externe, client_informel_id,
        montant_initial, montant_impaye, capital_restant_du, interets_retard, penalites,
        date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
        date_ouverture_creance, jours_retard, categorie_par, classe_risque_cobac,
        taux_provision_cobac, montant_provision, type_garantie, valeur_garantie
    )
    SELECT
        v_imf_id, v_ag_yde,
        'PFIN-2025-RET-' || LPAD(ci.id::TEXT, 4, '0'),
        ci.client_id_externe, ci.id,
        ROUND(ci.revenu_mensuel_estime * 6, 0),
        ROUND(ci.revenu_mensuel_estime * 0.8, 0),
        ROUND(ci.revenu_mensuel_estime * 6 * 0.60, 0),
        ROUND(ci.revenu_mensuel_estime * 0.03, 0), 0,
        '2025-03-01'::DATE,
        '2025-04-01'::DATE,
        CURRENT_DATE - INTERVAL '38 days',
        CURRENT_DATE - INTERVAL '38 days',
        38, 'PAR30', 'B', 10.00,
        ROUND(ci.revenu_mensuel_estime * 6 * 0.60 * 0.10, 0),
        'NANTISSEMENT', ROUND(ci.revenu_mensuel_estime * 8, 0)
    FROM app.clients_informels ci
    WHERE ci.imf_id = v_imf_id
      AND ci.client_id_externe IN ('CLF021','CLF022','CLF023','CLF024')
    ON CONFLICT DO NOTHING;

    -- 3 prêts clients à risque (CLF028–CLF030) — en contentieux
    INSERT INTO app.creances (imf_id, agence_id, id_pret_externe, client_id_externe, client_informel_id,
        montant_initial, montant_impaye, capital_restant_du, interets_retard, penalites,
        date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
        date_ouverture_creance, jours_retard, categorie_par, classe_risque_cobac,
        taux_provision_cobac, montant_provision, type_garantie, valeur_garantie
    )
    SELECT
        v_imf_id,
        CASE WHEN ci.zone_id LIKE 'YDE%' THEN v_ag_yde ELSE v_ag_dla END,
        'PFIN-2025-CONT-' || LPAD(ci.id::TEXT, 4, '0'),
        ci.client_id_externe, ci.id,
        ROUND(ci.revenu_mensuel_estime * 5, 0),
        ROUND(ci.revenu_mensuel_estime * 5 * 0.78, 0),
        ROUND(ci.revenu_mensuel_estime * 5 * 0.75, 0),
        ROUND(ci.revenu_mensuel_estime * 5 * 0.04, 0),
        ROUND(ci.revenu_mensuel_estime * 5 * 0.016, 0),
        '2025-02-01'::DATE,
        '2025-03-01'::DATE,
        CURRENT_DATE - INTERVAL '95 days',
        CURRENT_DATE - INTERVAL '95 days',
        95, 'PAR90', 'D', 50.00,
        ROUND(ci.revenu_mensuel_estime * 5 * 0.75 * 0.50, 0),
        'AUCUNE', 0
    FROM app.clients_informels ci
    WHERE ci.imf_id = v_imf_id
      AND ci.client_id_externe IN ('CLF028','CLF029','CLF030')
    ON CONFLICT DO NOTHING;

    -- ── 8. Staging tables — assure existence (miroir de V42) ─────────────────
    CREATE TABLE IF NOT EXISTS staging.stg_clients (
        id                            BIGSERIAL     PRIMARY KEY,
        imf_code                      VARCHAR(20)   NOT NULL,
        client_id_externe             TEXT          NOT NULL,
        nom_complet                   TEXT,
        telephone_principal           TEXT,
        zone_id                       TEXT,
        agence_code                   TEXT,
        secteur_principal             TEXT,
        revenu_mensuel_estime         NUMERIC(12,2),
        latitude_activite             NUMERIC(10,7),
        longitude_activite            NUMERIC(10,7),
        date_premiere_collecte        DATE,
        date_premier_pret             DATE,
        anciennete_jours              INT,
        nb_collectes_total            INT           NOT NULL DEFAULT 0,
        montant_total_collectes       NUMERIC(15,2) NOT NULL DEFAULT 0,
        nb_prets_total                INT           NOT NULL DEFAULT 0,
        taux_remboursement_historique NUMERIC(5,4),
        _dbt_loaded_at                TIMESTAMPTZ   DEFAULT NOW(),
        _dbt_updated_at               TIMESTAMPTZ   DEFAULT NOW(),
        CONSTRAINT stg_clients_imf_client_uq UNIQUE (imf_code, client_id_externe)
    );

    -- ── 9. Staging stg_clients — données KPI N-1 agrégées ────────────────────
    INSERT INTO staging.stg_clients (
        imf_code, client_id_externe, nom_complet, telephone_principal,
        zone_id, agence_code, secteur_principal, revenu_mensuel_estime,
        latitude_activite, longitude_activite,
        date_premiere_collecte, date_premier_pret, anciennete_jours,
        nb_collectes_total, montant_total_collectes, nb_prets_total,
        taux_remboursement_historique
    )
    SELECT
        'FINANCE',
        ci.client_id_externe,
        ci.nom_complet,
        ci.telephone_principal,
        ci.zone_id,
        CASE WHEN ci.agence_id = v_ag_yde THEN 'Agence Yaoundé Nlongkak' ELSE 'Agence Douala Bassa' END,
        ci.secteur_principal,
        ci.revenu_mensuel_estime,
        ci.latitude_activite,
        ci.longitude_activite,
        MIN(ce.date_collecte),
        MIN(cr.date_deblocage),
        EXTRACT(DAY FROM NOW() - MIN(ce.date_collecte))::INT,
        COUNT(DISTINCT ce.id),
        COALESCE(SUM(DISTINCT ce.montant_collecte), 0),
        COUNT(DISTINCT cr.id),
        CASE
            WHEN COUNT(DISTINCT cr.id) = 0 THEN NULL
            ELSE ROUND(1.0 - (
                SUM(CASE WHEN cr.jours_retard > 30 THEN cr.montant_impaye ELSE 0 END)
                / NULLIF(SUM(cr.montant_initial), 0)
            ), 4)
        END
    FROM app.clients_informels ci
    LEFT JOIN app.collectes_epargne ce
           ON ce.client_id_externe = ci.client_id_externe
          AND ce.imf_id = ci.imf_id
          AND ce.statut = 'VALIDEE'
          AND ce.date_collecte BETWEEN '2025-01-01' AND '2025-12-31'
    LEFT JOIN app.creances cr
           ON cr.client_id_externe = ci.client_id_externe
          AND cr.imf_id = ci.imf_id
    WHERE ci.imf_id = v_imf_id
    GROUP BY ci.id, ci.client_id_externe, ci.nom_complet, ci.telephone_principal,
             ci.zone_id, ci.agence_id, ci.secteur_principal, ci.revenu_mensuel_estime,
             ci.latitude_activite, ci.longitude_activite
    ON CONFLICT (imf_code, client_id_externe) DO UPDATE
        SET nb_collectes_total            = EXCLUDED.nb_collectes_total,
            montant_total_collectes       = EXCLUDED.montant_total_collectes,
            nb_prets_total                = EXCLUDED.nb_prets_total,
            taux_remboursement_historique = EXCLUDED.taux_remboursement_historique,
            _dbt_updated_at               = NOW();

    -- ── 10. Alertes initiales FINANCE SARL ───────────────────────────────────
    INSERT INTO app.alertes_systeme (type, titre, detail, severite, statut, source, created_at)
    VALUES
        ('CREANCE_EN_RETARD',
         'FINANCE SARL — 3 créances en retard > 30 jours',
         'Clients CLF028, CLF029, CLF030 en retard > 90 jours — phase contentieux COBAC',
         'CRITIQUE','ACTIVE','SYSTEME',NOW()),
        ('SCORE_ML_ALERTE',
         'FINANCE SARL — Score MCRS faible détecté',
         '3 clients présentent un score de risque critique — surveillance renforcée recommandée',
         'AVERTISSEMENT','ACTIVE','ML_ENGINE',NOW())
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'Seed FINANCE SARL terminé. IMF_ID=%, Agent renekomtsindi99@gmail.com créé.', v_imf_id;
END $$;
