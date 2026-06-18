-- ============================================================
-- V42 — Données de démonstration : FINTECH SARL
-- IMF fictive basée au Cameroun — devise FCFA
-- Mot de passe de tous les utilisateurs démo : admin123
-- Hash BCrypt(admin123, cost=10)
-- ============================================================

-- ── 1. IMF ────────────────────────────────────────────────────────────────────
INSERT INTO app.imf (
    code, nom, denomination_sociale, pays,
    adresse_siege, forme_juridique, capital_social, num_agrement,
    telephone, email,
    taux_interet_annuel, duree_max_credit_mois, taux_penalite_retard,
    seuil_relance_jours, taux_epargne, solde_min_epargne, frais_tenue_compte,
    segments_clients, types_garanties, actif
) VALUES (
    'FINTECH',
    'FINTECH SARL',
    'FINTECH MICROFINANCE SARL',
    'Cameroun',
    'Avenue Kennedy, BP 1247, Yaoundé, Centre',
    'SARL',
    50000000.00,
    'COBAC/EMF/2019-142',
    '+237 222 23 45 67',
    'direction@fintech-mf.cm',
    18.00, 36, 3.00,
    30, 5.00, 10000.00, 500.00,
    'Commerçants informels, Agriculteurs, Artisans, Petits entrepreneurs',
    'Caution solidaire, Nantissement, Hypothèque mobilière',
    TRUE
)
ON CONFLICT (code) DO NOTHING;

-- Variable locale pour l'ID IMF
DO $$
DECLARE
    v_imf_id         BIGINT;
    v_ag_yde BIGINT;
    v_ag_dla  BIGINT;
    v_dsi          BIGINT;
    v_dir          BIGINT;
    v_chef      BIGINT;
    v_ac1          BIGINT;
    v_ac2          BIGINT;
    v_ac3          BIGINT;
    v_ag1          BIGINT;
    v_ag2          BIGINT;
    v_caissier     BIGINT;
    v_resp     BIGINT;
    v_analyste     BIGINT;
    v_saisie    BIGINT;
    v_cycle_id       BIGINT;
    -- clients
    cl RECORD;
    dc_id BIGINT;
    cnt_id BIGINT;
    cr_id  BIGINT;

BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINTECH';

    -- ── 2. Agences ───────────────────────────────────────────────────────────
    INSERT INTO app.agences (imf_id, nom, ville, responsable, telephone, actif)
    VALUES
        (v_imf_id, 'Agence Yaoundé Centre', 'Yaoundé', 'Mme Chantal EBONGUE', '+237 222 23 45 70', TRUE),
        (v_imf_id, 'Agence Douala Akwa',    'Douala',  'M. François NKEMBI',  '+237 233 42 11 88', TRUE)
    ON CONFLICT ON CONSTRAINT uq_agence_imf_nom DO NOTHING;

    SELECT id INTO v_ag_yde FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Yaoundé Centre';
    SELECT id INTO v_ag_dla  FROM app.agences WHERE imf_id = v_imf_id AND nom = 'Agence Douala Akwa';

    -- ── 3. Utilisateurs ──────────────────────────────────────────────────────
    -- Mot de passe : admin123
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif, must_change_password)
    VALUES
        ('dsi.fintech',      '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'DSI',                    'dsi@fintech-mf.cm',         v_imf_id, TRUE, FALSE),
        ('dir.fintech',      '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'DIRECTEUR',              'directeur@fintech-mf.cm',   v_imf_id, TRUE, FALSE),
        ('chef.yaounde',     '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'chef.yaounde@fintech-mf.cm',v_imf_id, TRUE, FALSE),
        ('ac.nguema',        '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'nguema.ac@fintech-mf.cm',   v_imf_id, TRUE, FALSE),
        ('ac.mbarga',        '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'mbarga.ac@fintech-mf.cm',   v_imf_id, TRUE, FALSE),
        ('ac.fouda',         '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'fouda.ac@fintech-mf.cm',    v_imf_id, TRUE, FALSE),
        ('ag.belinga',       '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'belinga@fintech-mf.cm',     v_imf_id, TRUE, FALSE),
        ('ag.ondoa',         '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'ondoa@fintech-mf.cm',       v_imf_id, TRUE, FALSE),
        ('caissier.fintech', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'caisse@fintech-mf.cm',      v_imf_id, TRUE, FALSE),
        ('resp.rec',         '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'RESPONSABLE_RECOUVREMENT','rec@fintech-mf.cm',        v_imf_id, TRUE, FALSE),
        ('analyste.fintech', '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'ANALYSTE',               'analyste@fintech-mf.cm',    v_imf_id, TRUE, FALSE),
        ('saisie.fintech',   '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW', 'AGENT',                  'saisie@fintech-mf.cm',      v_imf_id, TRUE, FALSE)
    ON CONFLICT (username) DO NOTHING;

    SELECT id INTO v_dsi       FROM app.utilisateurs WHERE username = 'dsi.fintech'      AND imf_id = v_imf_id;
    SELECT id INTO v_dir       FROM app.utilisateurs WHERE username = 'dir.fintech'      AND imf_id = v_imf_id;
    SELECT id INTO v_chef   FROM app.utilisateurs WHERE username = 'chef.yaounde'     AND imf_id = v_imf_id;
    SELECT id INTO v_ac1       FROM app.utilisateurs WHERE username = 'ac.nguema'        AND imf_id = v_imf_id;
    SELECT id INTO v_ac2       FROM app.utilisateurs WHERE username = 'ac.mbarga'        AND imf_id = v_imf_id;
    SELECT id INTO v_ac3       FROM app.utilisateurs WHERE username = 'ac.fouda'         AND imf_id = v_imf_id;
    SELECT id INTO v_ag1       FROM app.utilisateurs WHERE username = 'ag.belinga'       AND imf_id = v_imf_id;
    SELECT id INTO v_ag2       FROM app.utilisateurs WHERE username = 'ag.ondoa'         AND imf_id = v_imf_id;
    SELECT id INTO v_caissier  FROM app.utilisateurs WHERE username = 'caissier.fintech' AND imf_id = v_imf_id;
    SELECT id INTO v_resp  FROM app.utilisateurs WHERE username = 'resp.rec'         AND imf_id = v_imf_id;
    SELECT id INTO v_analyste  FROM app.utilisateurs WHERE username = 'analyste.fintech' AND imf_id = v_imf_id;
    SELECT id INTO v_saisie FROM app.utilisateurs WHERE username = 'saisie.fintech'   AND imf_id = v_imf_id;

    -- ── 4. Clients informels ─────────────────────────────────────────────────
    INSERT INTO app.clients_informels (imf_id, client_id_externe, nom_complet, telephone_principal,
        zone_id, agence_id, date_naissance, sexe,
        secteur_principal, sous_secteur, annees_experience,
        revenu_mensuel_estime, marche_principal, frequence_marche,
        niveau_education, situation_familiale, nombre_personnes_charge,
        latitude_activite, longitude_activite, adresse_activite
    ) VALUES
        (v_imf_id,'CLT001','Marie-Thérèse ABENA BIYONG','+237 655 12 34 56','YDE-CENTRE',v_ag_yde,'1982-03-15','F','COMMERCE','Vente de vivres frais',8, 185000,'Marché Mvog-Mbi','QUOTIDIEN','PRIMAIRE','MARIE',4, 3.866667,11.516667,'Marché Mvog-Mbi, Yaoundé'),
        (v_imf_id,'CLT002','Jean-Baptiste ONDOA ESSOMBA','+237 677 98 23 11','YDE-CENTRE',v_ag_yde,'1975-07-22','M','ARTISANAT','Menuiserie bois',12,220000,'Rue du marché central','HEBDOMADAIRE','SECONDAIRE','MARIE',5, 3.862000,11.512000,'Atelier menuiserie, Mvolyé'),
        (v_imf_id,'CLT003','Patience FOUDA NGA','+237 699 44 55 66','YDE-SUD',v_ag_yde,'1990-11-08','F','AGRICOLE','Maraîchage',5, 120000,'Marché Melen','HEBDOMADAIRE','PRIMAIRE','CELIBATAIRE',2, 3.840000,11.500000,'Champ Nkol-Bisson'),
        (v_imf_id,'CLT004','Emmanuel NKEMBI BEBE','+237 670 33 44 55','YDE-CENTRE',v_ag_yde,'1978-05-30','M','TRANSPORT','Moto-taxi',10,310000,NULL,'QUOTIDIEN','SECONDAIRE','MARIE',6, 3.870000,11.520000,'Carrefour Mballa II'),
        (v_imf_id,'CLT005','Suzanne ATANGANA ELANGA','+237 655 78 90 12','YDE-EST',v_ag_yde,'1986-09-14','F','COMMERCE','Salon de coiffure',7, 155000,'Quartier Omnisport','QUOTIDIEN','SECONDAIRE','MARIE',3, 3.880000,11.530000,'Avenue Foé, Yaoundé'),
        (v_imf_id,'CLT006','Pierre MBARGA ONANA','+237 690 21 43 65','DLA-AKWA',v_ag_dla,'1972-12-01','M','COMMERCE','Commerce général',15,280000,'Marché Nkoulou','QUOTIDIEN','SECONDAIRE','MARIE',7, 4.050000, 9.700000,'Marché Nkoulou, Douala'),
        (v_imf_id,'CLT007','Cécile BELLO NGONO','+237 677 65 43 21','DLA-AKWA',v_ag_dla,'1993-04-25','F','AGRICOLE','Aviculture',3, 145000,'Marché Sandaga','HEBDOMADAIRE','PRIMAIRE','CELIBATAIRE',1, 4.055000, 9.720000,'Bassa, Douala'),
        (v_imf_id,'CLT008','Richard TABI MONGO','+237 699 87 65 43','DLA-BONABERI',v_ag_dla,'1980-08-17','M','PECHE','Pêche artisanale',10,195000,'Wouri','QUOTIDIEN','PRIMAIRE','MARIE',5, 4.070000, 9.680000,'Bonabéri, Douala'),
        (v_imf_id,'CLT009','Anastasie ETOUNDI OWONO','+237 655 34 56 78','YDE-NORD',v_ag_yde,'1988-02-28','F','ARTISANAT','Tissage raphia',6, 130000,'Marché central Yaoundé','BIMENSUEL','SECONDAIRE','MARIE',4, 3.890000,11.505000,'Briqueterie, Yaoundé'),
        (v_imf_id,'CLT010','Théophile ESSANG ETOUNDI','+237 670 56 78 90','YDE-CENTRE',v_ag_yde,'1970-06-10','M','SERVICES','Réparation électroménager',18,240000,'Rue Nachtigal','QUOTIDIEN','SECONDAIRE','MARIE',6, 3.865000,11.514000,'Messa, Yaoundé'),
        (v_imf_id,'CLT011','Flavienne KOUMBA BEKONO','+237 698 23 45 67','DLA-AKWA',v_ag_dla,'1985-10-20','F','COMMERCE','Prêt-à-porter',9, 210000,'Marché Central Douala','QUOTIDIEN','SECONDAIRE','MARIE',3, 4.048000, 9.700000,'Akwa, Douala'),
        (v_imf_id,'CLT012','Alphonse NDZANA MABINA','+237 675 43 21 09','YDE-CENTRE',v_ag_yde,'1965-03-05','M','ELEVAGE','Porciculture',14,320000,'Ferme Nkolbisson','MENSUEL','PRIMAIRE','MARIE',8, 3.845000,11.490000,'Nkolbisson, Yaoundé'),
        (v_imf_id,'CLT013','Bernadette ESSOLA ETOA','+237 655 67 89 01','YDE-CENTRE',v_ag_yde,'1994-07-14','F','COMMERCE','Restaurant de rue',4, 165000,'Carrefour Nlongkak','QUOTIDIEN','SECONDAIRE','CELIBATAIRE',2, 3.875000,11.522000,'Nlongkak, Yaoundé'),
        (v_imf_id,'CLT014','Fabrice ZANG NKOA','+237 699 01 23 45','DLA-BONABERI',v_ag_dla,'1983-11-30','M','TRANSPORT','Transport de marchandises',12,380000,NULL,'QUOTIDIEN','SECONDAIRE','MARIE',4, 4.080000, 9.670000,'Bonabéri, Douala'),
        (v_imf_id,'CLT015','Odette NGUELE ABATE','+237 677 89 01 23','YDE-SUD',v_ag_yde,'1976-04-18','F','AGRICOLE','Culture maïs-plantain',15,175000,'Marché Madagascar','HEBDOMADAIRE','AUCUN','VEUF',5, 3.835000,11.498000,'Soa, Yaoundé'),
        (v_imf_id,'CLT016','Sylvain AWONO BIYONG','+237 655 12 98 76','YDE-CENTRE',v_ag_yde,'1987-08-22','M','ARTISANAT','Soudure',11,205000,'Zone industrielle','HEBDOMADAIRE','SECONDAIRE','MARIE',3, 3.858000,11.508000,'Ntaba, Yaoundé'),
        (v_imf_id,'CLT017','Clémentine MBIDA ATANGANA','+237 690 67 45 23','DLA-AKWA',v_ag_dla,'1991-01-09','F','COMMERCE','Vente cosmétiques',5, 140000,'Marché New-Bell','QUOTIDIEN','SECONDAIRE','CELIBATAIRE',1, 4.042000, 9.710000,'New-Bell, Douala'),
        (v_imf_id,'CLT018','Norbert TIOKOU KENGNE','+237 677 34 56 12','YDE-EST',v_ag_yde,'1979-05-16','M','SERVICES','Photocopie/Internet café',10,195000,'Campus Univ. Yaoundé I','QUOTIDIEN','SUPERIEUR','MARIE',4, 3.882000,11.525000,'Ngoa-Ekele, Yaoundé'),
        (v_imf_id,'CLT019','Victorine ABONDO ESSAMA','+237 699 78 56 34','DLA-AKWA',v_ag_dla,'1968-09-03','F','ELEVAGE','Aviculture industrielle',20,450000,'Ferme Logpom','MENSUEL','SECONDAIRE','VEUF',3, 4.060000, 9.730000,'Logpom, Douala'),
        (v_imf_id,'CLT020','Marcel BELINGA AKOUMA','+237 655 45 23 01','YDE-NORD',v_ag_yde,'1984-12-25','M','TRANSPORT','Taxi inter-urbain',9, 290000,NULL,'QUOTIDIEN','SECONDAIRE','MARIE',5, 3.895000,11.510000,'Obili, Yaoundé'),
        (v_imf_id,'CLT021','Angélique MEDJO ABENA','+237 670 23 45 67','YDE-CENTRE',v_ag_yde,'1996-06-11','F','COMMERCE','Vente de poisson fumé',4, 128000,'Marché Mvog-Ada','QUOTIDIEN','PRIMAIRE','CELIBATAIRE',1, 3.862000,11.517000,'Mvog-Ada, Yaoundé'),
        (v_imf_id,'CLT022','Hilaire OWONO NGUEMA','+237 699 56 34 12','DLA-AKWA',v_ag_dla,'1971-02-14','M','ARTISANAT','Tailleur confection',18,215000,'Marché Sandaga','QUOTIDIEN','SECONDAIRE','MARIE',6, 4.047000, 9.705000,'Bonanjo, Douala'),
        (v_imf_id,'CLT023','Geneviève NKOA BEYALA','+237 677 12 34 90','YDE-CENTRE',v_ag_yde,'1989-10-07','F','SERVICES','Agence de voyage',6, 350000,'Centre-ville Yaoundé','QUOTIDIEN','SUPERIEUR','MARIE',2, 3.868000,11.516000,'Bastos, Yaoundé'),
        (v_imf_id,'CLT024','Didier MVOGO ONDOA','+237 655 90 12 34','YDE-SUD',v_ag_yde,'1977-07-19','M','PECHE','Pisciculture',10,185000,'Rivière Mfoundi','MENSUEL','PRIMAIRE','MARIE',7, 3.842000,11.495000,'Djoungolo, Yaoundé'),
        (v_imf_id,'CLT025','Monique EBONGUE MENDO','+237 690 34 56 78','DLA-BONABERI',v_ag_dla,'1992-03-28','F','COMMERCE','Épicerie',5, 155000,'Quartier Bepanda','QUOTIDIEN','SECONDAIRE','MARIE',3, 4.073000, 9.690000,'Bepanda, Douala')
    ON CONFLICT DO NOTHING;

    -- ── 5. Cycle de collecte ─────────────────────────────────────────────────
    INSERT INTO app.cycles_collecte (imf_id, agence_id, nom_cycle, periodicite,
        date_debut, objectif_montant, objectif_nb_transactions, actif
    ) VALUES (
        v_imf_id, v_ag_yde, 'Cycle Hebdomadaire Yaoundé 2025', 'HEBDOMADAIRE',
        '2024-07-01', 500000000, 1000, TRUE
    ) ON CONFLICT DO NOTHING;

    SELECT id INTO v_cycle_id FROM app.cycles_collecte WHERE imf_id = v_imf_id AND nom_cycle = 'Cycle Hebdomadaire Yaoundé 2025';

    -- ── 6. Collectes épargne (données ML — 12 derniers mois) ─────────────────
    -- Clients bons payeurs (CLT001–CLT010) : collectes régulières
    INSERT INTO app.collectes_epargne (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
         client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
    SELECT
        gen_random_uuid(),
        v_imf_id, v_ag_yde, v_cycle_id,
        CASE WHEN (n % 3) = 0 THEN v_ac1 WHEN (n % 3) = 1 THEN v_ac2 ELSE v_ac3 END,
        'CLT' || LPAD(((n % 10) + 1)::TEXT, 3, '0'),
        -- Montants variant de 15 000 à 75 000 FCFA selon le client
        CASE
            WHEN (n % 10) IN (0,1,2) THEN (15000 + (n * 1234 % 45000))::NUMERIC
            WHEN (n % 10) IN (3,4,5) THEN (25000 + (n * 2341 % 35000))::NUMERIC
            ELSE                          (10000 + (n * 3412 % 55000))::NUMERIC
        END,
        (CURRENT_DATE - (((n / 10) * 7) || ' days')::INTERVAL)::DATE,
        CASE WHEN (n % 4) = 0 THEN 'MTN'
             WHEN (n % 4) = 1 THEN 'ORANGE'
             WHEN (n % 4) = 2 THEN 'ESPECES'
             ELSE                   'VIREMENT' END,
        'VALIDEE'
    FROM generate_series(0, 159) AS n
    WHERE (CURRENT_DATE - (((n / 10) * 7) || ' days')::INTERVAL)::DATE >= (CURRENT_DATE - INTERVAL '12 months')
    ON CONFLICT DO NOTHING;

    -- Clients à risque (CLT011–CLT020) : collectes irrégulières (gaps)
    INSERT INTO app.collectes_epargne (uuid_mobile, imf_id, agence_id, cycle_id, agent_id,
         client_id_externe, montant_collecte, date_collecte, canal_paiement, statut)
    SELECT
        gen_random_uuid(),
        v_imf_id, v_ag_dla, v_cycle_id,
        CASE WHEN (n % 2) = 0 THEN v_ac1 ELSE v_ac2 END,
        'CLT' || LPAD(((n % 10) + 11)::TEXT, 3, '0'),
        (8000 + (n * 4123 % 42000))::NUMERIC,
        (CURRENT_DATE - (((n / 6) * 14 + (n % 3) * 3) || ' days')::INTERVAL)::DATE,
        CASE WHEN (n % 3) = 0 THEN 'MTN'
             WHEN (n % 3) = 1 THEN 'ESPECES'
             ELSE                   'ORANGE' END,
        CASE WHEN (n % 7) = 0 THEN 'REJETEE' ELSE 'VALIDEE' END
    FROM generate_series(0, 89) AS n
    WHERE (CURRENT_DATE - (((n / 6) * 14 + (n % 3) * 3) || ' days')::INTERVAL)::DATE >= (CURRENT_DATE - INTERVAL '12 months')
    ON CONFLICT DO NOTHING;

    -- ── 7. Collectes terrain (app mobile) ────────────────────────────────────
    INSERT INTO app.collectes_terrain
        (id_collecte_mobile, agent_id, imf_id, client_id, pret_id,
         date_collecte, montant_collecte, canal_paiement,
         reference_transaction, observation, statut, latitude, longitude)
    VALUES
        ('MOB-YDE-2025-001',v_ag1,v_imf_id,'CLT001','PRE001','2025-09-15',  50000,'MTN',   'MTN20250915001','RAS','CONFIRMEE',3.866667,11.516667),
        ('MOB-YDE-2025-002',v_ag1,v_imf_id,'CLT003','PRE003','2025-09-15',  25000,'ESPECES',NULL,           'Client présent','CONFIRMEE',3.840000,11.500000),
        ('MOB-YDE-2025-003',v_ag2,v_imf_id,'CLT002','PRE002','2025-09-16',  75000,'ORANGE','OM20250916001', 'Paiement partiel','CONFIRMEE',3.862000,11.512000),
        ('MOB-YDE-2025-004',v_ag1,v_imf_id,'CLT005','PRE005','2025-09-18',  40000,'MTN',   'MTN20250918001','RAS','CONFIRMEE',3.880000,11.530000),
        ('MOB-DLA-2025-001',v_ag2,v_imf_id,'CLT006','PRE006','2025-09-20', 100000,'ESPECES',NULL,           'Règlement mensuel','CONFIRMEE',4.050000,9.700000),
        ('MOB-DLA-2025-002',v_ag1,v_imf_id,'CLT008','PRE008','2025-09-22',  60000,'MTN',   'MTN20250922001','RAS','CONFIRMEE',4.070000,9.680000),
        ('MOB-YDE-2025-005',v_ag2,v_imf_id,'CLT004','PRE004','2025-09-25',  80000,'ORANGE','OM20250925001', 'RAS','CONFIRMEE',3.870000,11.520000),
        ('MOB-YDE-2025-006',v_ag1,v_imf_id,'CLT009','EPG-YDE-006','2025-09-26',30000,'ESPECES',NULL,'Épargne libre','CONFIRMEE',3.890000,11.505000),
        ('MOB-DLA-2025-003',v_ag2,v_imf_id,'CLT007','EPG-DLA-003','2025-09-27',20000,'MTN',   'MTN20250927001','RAS','CONFIRMEE',4.055000,9.720000),
        ('MOB-YDE-2025-007',v_ag1,v_imf_id,'CLT013','PRE013','2025-10-01',  35000,'ESPECES',NULL,           'RAS','CONFIRMEE',3.875000,11.522000),
        ('MOB-YDE-2025-008',v_ag2,v_imf_id,'CLT015','EPG-YDE-008','2025-10-03',15000,'MTN', 'MTN20251003001','Retard signalé','SOUMISE',3.835000,11.498000),
        ('MOB-DLA-2025-004',v_ag1,v_imf_id,'CLT011','PRE011','2025-10-05',  55000,'ORANGE','OM20251005001', 'RAS','CONFIRMEE',4.048000,9.700000),
        ('MOB-YDE-2025-009',v_ag2,v_imf_id,'CLT020','PRE020','2025-10-08',  90000,'ESPECES',NULL,           'Paiement complet','CONFIRMEE',3.895000,11.510000),
        ('MOB-DLA-2025-005',v_ag1,v_imf_id,'CLT022','EPG-DLA-005','2025-10-10',45000,'MTN', 'MTN20251010001','RAS','CONFIRMEE',4.047000,9.705000),
        ('MOB-YDE-2025-010',v_ag2,v_imf_id,'CLT010','PRE010','2025-10-12', 120000,'ORANGE','OM20251012001', 'RAS','CONFIRMEE',3.865000,11.514000)
    ON CONFLICT (id_collecte_mobile) DO NOTHING;

    -- ── 8. Dossiers crédit ───────────────────────────────────────────────────
    -- Statuts : INSTRUCTION, EN_ANALYSE, COMITE, ACCORDE, REFUSE, DECAISSE, SOLDE

    INSERT INTO app.dossiers_credit (imf_id, agence_id, agent_credit_id, client_id, client_nom,
        montant_demande, duree_mois, objet_financement, secteur_activite,
        revenu_estime, charges_mensuelles, capacite_remboursement,
        statut, note_analyse, date_soumission, date_decision, chef_agence_id
    ) VALUES
        -- Dossiers accordés et décaissés (historique)
        (v_imf_id,v_ag_yde,v_ac1,'CLT001','Marie-Thérèse ABENA BIYONG',
         1500000,18,'Agrandissement fonds de commerce vivres','COMMERCE',
         185000,65000,120000,'DECAISSE',
         'Cliente fiable, 8 ans d''expérience, collectes régulières. Risque faible.',
         NOW()-INTERVAL '11 months', NOW()-INTERVAL '10 months 15 days', v_chef),

        (v_imf_id,v_ag_yde,v_ac2,'CLT002','Jean-Baptiste ONDOA ESSOMBA',
         2500000,24,'Achat équipement menuiserie (scie circulaire, rabot)','ARTISANAT',
         220000,80000,140000,'DECAISSE',
         'Artisan qualifié, carnet de commandes fourni. Garantie nantissement matériel.',
         NOW()-INTERVAL '10 months', NOW()-INTERVAL '9 months 20 days', v_chef),

        (v_imf_id,v_ag_dla,v_ac3,'CLT006','Pierre MBARGA ONANA',
         3000000,24,'Stock marchandises pour campagne fin d''année','COMMERCE',
         280000,95000,185000,'DECAISSE',
         'Commerce bien établi. Marché Nkoulou — flux réguliers constatés.',
         NOW()-INTERVAL '9 months', NOW()-INTERVAL '8 months 25 days', v_chef),

        (v_imf_id,v_ag_yde,v_ac1,'CLT004','Emmanuel NKEMBI BEBE',
         800000,12,'Achat moto Honda CB150 pour activité taxi','TRANSPORT',
         310000,120000,190000,'DECAISSE',
         'Moto-taxi avec 10 ans d''expérience. Revenu stable et élevé.',
         NOW()-INTERVAL '8 months', NOW()-INTERVAL '7 months 20 days', v_chef),

        (v_imf_id,v_ag_yde,v_ac2,'CLT012','Alphonse NDZANA MABINA',
         4000000,36,'Extension porcherie — 50 porcs supplémentaires','ELEVAGE',
         320000,100000,220000,'DECAISSE',
         'Éleveur expérimenté 14 ans. Contrat d''approvisionnement Supermarché Score fourni.',
         NOW()-INTERVAL '7 months', NOW()-INTERVAL '6 months 18 days', v_chef),

        (v_imf_id,v_ag_dla,v_ac3,'CLT011','Flavienne KOUMBA BEKONO',
         1800000,18,'Stock vêtements prêt-à-porter importés Dubaï','COMMERCE',
         210000,72000,138000,'DECAISSE',
         'Commerçante sérieuse. Factures pro-forma fournisseur Dubaï jointes.',
         NOW()-INTERVAL '6 months', NOW()-INTERVAL '5 months 22 days', v_chef),

        (v_imf_id,v_ag_yde,v_ac1,'CLT010','Théophile ESSANG ETOUNDI',
         1200000,12,'Achat outillage réparation électroménager','SERVICES',
         240000,85000,155000,'ACCORDE',
         'Technicien confirmé. Prise de garantie nantissement outillage.',
         NOW()-INTERVAL '2 months', NOW()-INTERVAL '1 month 20 days', v_chef),

        (v_imf_id,v_ag_dla,v_ac2,'CLT019','Victorine ABONDO ESSAMA',
         6000000,48,'Modernisation ferme avicole — 2000 poulets de chair','ELEVAGE',
         450000,150000,300000,'ACCORDE',
         'Grande exploitante. Plan d''affaires solide. Contrat vente Spar Douala.',
         NOW()-INTERVAL '1 month 15 days', NOW()-INTERVAL '20 days', v_chef),

        -- Dossiers en cours d'instruction
        (v_imf_id,v_ag_yde,v_ac3,'CLT005','Suzanne ATANGANA ELANGA',
         900000,12,'Équipement salon de coiffure moderne','ARTISANAT',
         155000,55000,100000,'EN_ANALYSE',
         'Analyse terrain en cours. Bon comportement épargne.',
         NOW()-INTERVAL '25 days', NULL, v_chef),

        (v_imf_id,v_ag_yde,v_ac1,'CLT013','Bernadette ESSOLA ETOA',
         600000,12,'Équipement restaurant (réfrigérateur, gazinière)','SERVICES',
         165000,60000,105000,'INSTRUCTION',
         NULL,NOW()-INTERVAL '10 days', NULL, v_chef),

        (v_imf_id,v_ag_dla,v_ac2,'CLT014','Fabrice ZANG NKOA',
         5000000,36,'Achat camion frigorifique occasion','TRANSPORT',
         380000,140000,240000,'COMITE',
         'Transporteur professionnel. Camion inspecté. Passage en comité requis.',
         NOW()-INTERVAL '35 days', NULL, v_chef),

        (v_imf_id,v_ag_yde,v_ac3,'CLT023','Geneviève NKOA BEYALA',
         2000000,24,'Développement agence de voyage (visa, billetterie)','SERVICES',
         350000,110000,240000,'COMITE',
         'Secteur touristique. Documents légaux en ordre. Comité convoqué.',
         NOW()-INTERVAL '20 days', NULL, v_chef),

        -- Dossiers refusés (avec motif)
        (v_imf_id,v_ag_dla,v_ac1,'CLT017','Clémentine MBIDA ATANGANA',
         2500000,24,'Stock cosmétiques importation Chine','COMMERCE',
         140000,55000,85000,'REFUSE',
         'Capacité remboursement insuffisante (85k/mois < mensualité estimée 125k). Refusé.',
         NOW()-INTERVAL '45 days', NOW()-INTERVAL '38 days', v_chef),

        (v_imf_id,v_ag_yde,v_ac2,'CLT021','Angélique MEDJO ABENA',
         1500000,18,'Agrandissement point de vente poisson fumé','COMMERCE',
         128000,50000,78000,'REFUSE',
         'Capacité limitée. Collectes irrégulières 4 derniers mois. Refusé — réévaluation 6 mois.',
         NOW()-INTERVAL '30 days', NOW()-INTERVAL '22 days', v_chef),

        (v_imf_id,v_ag_dla,v_ac3,'CLT025','Monique EBONGUE MENDO',
         700000,12,'Extension épicerie','COMMERCE',
         155000,58000,97000,'INSTRUCTION',
         NULL, NOW()-INTERVAL '5 days', NULL, v_chef);

    -- ── 9. Contrats crédit (dossiers DECAISSE + ACCORDE récents) ─────────────
    -- DECAISSE: 6 contrats
    FOR dc_id IN
        SELECT id FROM app.dossiers_credit
        WHERE imf_id = v_imf_id AND statut IN ('DECAISSE','ACCORDE')
        ORDER BY date_soumission
    LOOP
        BEGIN
            INSERT INTO app.contrats_credit (
                dossier_id, reference_contrat, date_signature,
                montant_final, taux_interet, frais_dossier, nb_echeances,
                periodicite, signatures_conformes, agent_saisie_id, statut
            )
            SELECT
                dc_id,
                'CTR-FINTECH-' || TO_CHAR(date_soumission, 'YYYY') || '-' || LPAD(dc_id::TEXT, 4, '0'),
                (date_soumission + INTERVAL '20 days')::DATE,
                montant_demande,
                0.1800, -- 18% annuel
                montant_demande * 0.01,
                duree_mois,
                'MENSUEL',
                TRUE,
                v_saisie,
                CASE WHEN statut = 'DECAISSE' THEN 'DECAISSE' ELSE 'SIGNE' END
            FROM app.dossiers_credit WHERE id = dc_id;
        EXCEPTION WHEN unique_violation THEN NULL;
        END;
    END LOOP;

    -- ── 10. Créances (prêts en cours / en retard) ────────────────────────────
    INSERT INTO app.creances (imf_id, agence_id, id_pret_externe, client_id_externe, client_informel_id,
        montant_initial, montant_impaye, capital_restant_du, interets_retard, penalites,
        date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
        date_ouverture_creance, jours_retard, categorie_par, classe_risque_cobac,
        taux_provision_cobac, montant_provision, type_garantie, valeur_garantie
    )
    SELECT
        v_imf_id,
        v_ag_yde,
        'PRE' || LPAD(dc.id::TEXT,3,'0'),
        dc.client_id,
        ci.id,
        montant_demande,
        -- capital restant ~60% pour les anciens, ~85% pour les récents
        ROUND(montant_demande * CASE WHEN date_soumission < NOW()-INTERVAL '8 months' THEN 0.40 ELSE 0.82 END, 0),
        ROUND(montant_demande * CASE WHEN date_soumission < NOW()-INTERVAL '8 months' THEN 0.38 ELSE 0.80 END, 0),
        0, 0,
        (date_soumission + INTERVAL '25 days')::DATE,
        (date_soumission + INTERVAL '55 days')::DATE,
        NULL,
        CURRENT_DATE,
        0,
        'COURANT',
        'A',
        0.00,
        0.00,
        'CAUTION_SOLIDAIRE',
        ROUND(montant_demande * 1.2, 0)
    FROM app.dossiers_credit dc
    JOIN app.clients_informels ci ON ci.client_id_externe = dc.client_id AND ci.imf_id = dc.imf_id
    WHERE dc.imf_id = v_imf_id AND dc.statut IN ('DECAISSE')
    ON CONFLICT DO NOTHING;

    -- Créances en retard (simulées)
    INSERT INTO app.creances (imf_id, agence_id, id_pret_externe, client_id_externe, client_informel_id,
        montant_initial, montant_impaye, capital_restant_du, interets_retard, penalites,
        date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
        date_ouverture_creance, jours_retard, categorie_par, classe_risque_cobac,
        taux_provision_cobac, montant_provision, type_garantie, valeur_garantie
    ) VALUES
        (v_imf_id,v_ag_dla,'PRE-RET-001','CLT008',
         (SELECT id FROM app.clients_informels WHERE client_id_externe='CLT008' AND imf_id=v_imf_id),
         1000000, 450000, 430000, 22500, 9000,
         CURRENT_DATE-INTERVAL '8 months',
         CURRENT_DATE-INTERVAL '7 months 15 days',
         CURRENT_DATE-INTERVAL '45 days',
         CURRENT_DATE-INTERVAL '45 days',
         45,'PAR30','B',10.00,43000,'NANTISSEMENT_PIROGUE',750000),

        (v_imf_id,v_ag_yde,'PRE-RET-002','CLT015',
         (SELECT id FROM app.clients_informels WHERE client_id_externe='CLT015' AND imf_id=v_imf_id),
         750000, 680000, 670000, 51000, 20400,
         CURRENT_DATE-INTERVAL '5 months',
         CURRENT_DATE-INTERVAL '4 months 15 days',
         CURRENT_DATE-INTERVAL '68 days',
         CURRENT_DATE-INTERVAL '68 days',
         68,'PAR60','C',20.00,136000,'CAUTION_SOLIDAIRE',400000),

        (v_imf_id,v_ag_dla,'PRE-RET-003','CLT017',
         (SELECT id FROM app.clients_informels WHERE client_id_externe='CLT017' AND imf_id=v_imf_id),
         500000, 500000, 498000, 42000, 16800,
         CURRENT_DATE-INTERVAL '4 months',
         CURRENT_DATE-INTERVAL '3 months 15 days',
         CURRENT_DATE-INTERVAL '92 days',
         CURRENT_DATE-INTERVAL '92 days',
         92,'PAR90','D',50.00,250000,'AUCUNE',0)
    ON CONFLICT DO NOTHING;

    -- ── 11. Dossiers de recouvrement ─────────────────────────────────────────
    INSERT INTO app.dossiers_recouvrement (imf_id, id_pret, nom_client, montant_impaye, jours_retard,
        phase, agent_responsable_id
    ) VALUES
        (v_imf_id,'PRE-RET-001','Richard TABI MONGO',450000,45,'RELANCE_AMIABLE',v_resp),
        (v_imf_id,'PRE-RET-002','Odette NGUELE ABATE',680000,68,'MISE_EN_DEMEURE',v_resp),
        (v_imf_id,'PRE-RET-003','Clémentine MBIDA ATANGANA',500000,92,'CONTENTIEUX',v_resp)
    ON CONFLICT DO NOTHING;

    -- ── 12. Facteurs macro-économiques ───────────────────────────────────────
    INSERT INTO app.facteurs_macro (indicateur, valeur, date_observation, source)
    VALUES
        ('TAUX_INFLATION_MENSUEL',0.60,'2025-10-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.50,'2025-09-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.70,'2025-08-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.40,'2025-07-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.80,'2025-06-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.60,'2025-05-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.50,'2025-04-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.70,'2025-03-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.60,'2025-02-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.50,'2025-01-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.90,'2024-12-01','BEAC'),
        ('TAUX_INFLATION_MENSUEL',0.70,'2024-11-01','BEAC'),
        ('TAUX_INFLATION_ANNUEL',6.80,'2025-10-01','BEAC'),
        ('TAUX_INFLATION_ANNUEL',6.50,'2025-07-01','BEAC'),
        ('TAUX_DIRECTEUR_BEAC',5.00,'2025-10-01','BEAC'),
        ('TAUX_DIRECTEUR_BEAC',5.00,'2025-07-01','BEAC'),
        ('TAUX_DIRECTEUR_BEAC',4.75,'2025-01-01','BEAC'),
        ('COURS_EUR_XAF',655.957,'2025-10-01','BEAC'),
        ('COURS_EUR_XAF',655.957,'2025-07-01','BEAC'),
        ('COURS_USD_XAF',622.40,'2025-10-01','BEAC'),
        ('COURS_USD_XAF',598.30,'2025-07-01','BEAC'),
        ('INDICE_PRODUCTION_AGRICOLE',112.4,'2025-10-01','INS_CAMEROUN'),
        ('INDICE_PRODUCTION_AGRICOLE',108.2,'2025-07-01','INS_CAMEROUN'),
        ('INDICE_PRODUCTION_AGRICOLE',105.8,'2025-04-01','INS_CAMEROUN'),
        ('INDICE_PRIX_CONSOMMATION',142.3,'2025-10-01','INS_CAMEROUN'),
        ('INDICE_PRIX_CONSOMMATION',140.1,'2025-07-01','INS_CAMEROUN')
    ON CONFLICT (indicateur, date_observation) DO NOTHING;

    -- ── 13. Staging ML — stg_clients ─────────────────────────────────────────
    INSERT INTO staging.stg_clients (
        imf_code, client_id_externe, nom_complet, telephone_principal,
        zone_id, agence_code, secteur_principal, revenu_mensuel_estime,
        latitude_activite, longitude_activite,
        date_premiere_collecte, date_premier_pret, anciennete_jours,
        nb_collectes_total, montant_total_collectes, nb_prets_total,
        taux_remboursement_historique
    )
    SELECT
        'FINTECH',
        ci.client_id_externe,
        ci.nom_complet,
        ci.telephone_principal,
        ci.zone_id,
        CASE WHEN ci.agence_id = v_ag_yde THEN 'YDE' ELSE 'DLA' END,
        ci.secteur_principal,
        ci.revenu_mensuel_estime,
        ci.latitude_activite,
        ci.longitude_activite,
        MIN(ce.date_collecte),
        MIN(cr.date_deblocage),
        EXTRACT(DAY FROM NOW() - MIN(ce.date_collecte))::INT,
        COUNT(ce.id),
        COALESCE(SUM(ce.montant_collecte), 0),
        COUNT(DISTINCT cr.id),
        CASE
            WHEN COUNT(DISTINCT cr.id) = 0 THEN NULL
            ELSE ROUND(1.0 - (
                SUM(CASE WHEN cr.jours_retard > 30 THEN cr.montant_impaye ELSE 0 END)
                / NULLIF(SUM(cr.montant_initial), 0)
            ), 4)
        END
    FROM app.clients_informels ci
    LEFT JOIN app.collectes_epargne ce ON ce.client_id_externe = ci.client_id_externe AND ce.imf_id = ci.imf_id AND ce.statut = 'VALIDEE'
    LEFT JOIN app.creances cr ON cr.client_id_externe = ci.client_id_externe AND cr.imf_id = ci.imf_id
    WHERE ci.imf_id = v_imf_id
    GROUP BY ci.id, ci.client_id_externe, ci.nom_complet, ci.telephone_principal,
             ci.zone_id, ci.agence_id, ci.secteur_principal, ci.revenu_mensuel_estime,
             ci.latitude_activite, ci.longitude_activite
    ON CONFLICT (imf_code, client_id_externe) DO UPDATE
        SET nb_collectes_total        = EXCLUDED.nb_collectes_total,
            montant_total_collectes   = EXCLUDED.montant_total_collectes,
            nb_prets_total            = EXCLUDED.nb_prets_total,
            taux_remboursement_historique = EXCLUDED.taux_remboursement_historique,
            _dbt_updated_at           = NOW();

    -- ── 14. Staging ML — stg_collectes_epargne ───────────────────────────────
    INSERT INTO staging.stg_collectes_epargne (
        uuid_mobile, imf_code, agence_code, agent_username,
        client_id_externe, montant_collecte, date_collecte,
        canal_paiement, statut_validation, hash_sha256
    )
    SELECT
        ce.uuid_mobile,
        'FINTECH',
        CASE WHEN ce.agence_id = v_ag_yde THEN 'YDE' ELSE 'DLA' END,
        u.username,
        ce.client_id_externe,
        ce.montant_collecte,
        ce.date_collecte,
        ce.canal_paiement,
        CASE WHEN ce.statut = 'VALIDEE' THEN 'VALIDE' ELSE ce.statut END,
        MD5(ce.uuid_mobile::TEXT)
    FROM app.collectes_epargne ce
    JOIN app.utilisateurs u ON u.id = ce.agent_id
    WHERE ce.imf_id = v_imf_id
    ON CONFLICT DO NOTHING;

    -- ── 15. Staging ML — stg_creances ─────────────────────────────────────────
    INSERT INTO staging.stg_creances (
        imf_code, id_pret, id_client, nom_client, agence_code,
        montant_initial, montant_rembourse, solde_restant, montant_impaye,
        interets_retard, date_deblocage, date_echeance, jours_retard, statut_pret
    )
    SELECT
        'FINTECH',
        cr.id_pret_externe,
        cr.client_id_externe,
        ci.nom_complet,
        CASE WHEN cr.agence_id = v_ag_yde THEN 'YDE' ELSE 'DLA' END,
        cr.montant_initial,
        cr.montant_initial - cr.capital_restant_du,
        cr.capital_restant_du,
        cr.montant_impaye,
        cr.interets_retard,
        cr.date_deblocage,
        cr.date_premiere_echeance,
        cr.jours_retard,
        CASE cr.categorie_par
            WHEN 'COURANT' THEN 'EN_COURS'
            WHEN 'PAR30'   THEN 'EN_RETARD'
            WHEN 'PAR60'   THEN 'EN_RETARD'
            WHEN 'PAR90'   THEN 'EN_CONTENTIEUX'
            ELSE 'EN_RETARD'
        END
    FROM app.creances cr
    LEFT JOIN app.clients_informels ci ON ci.client_id_externe = cr.client_id_externe AND ci.imf_id = cr.imf_id
    WHERE cr.imf_id = v_imf_id
    ON CONFLICT DO NOTHING;

    -- ── 16. Staging ML — stg_prets ──────────────────────────────────────────
    INSERT INTO staging.stg_prets (
        id_pret, id_client, nom_client, nom_agence, nom_produit,
        montant_pret, solde_restant, statut_pret, jours_retard
    )
    SELECT
        'PRE' || LPAD(dc.id::TEXT,3,'0'),
        dc.client_id,
        dc.client_nom,
        a.nom,
        dc.secteur_activite || ' — ' || dc.objet_financement,
        dc.montant_demande,
        ROUND(dc.montant_demande * CASE WHEN dc.statut='DECAISSE' THEN 0.65 ELSE 1.0 END, 0),
        CASE dc.statut
            WHEN 'DECAISSE' THEN 'EN_COURS'
            WHEN 'ACCORDE'  THEN 'DEBLOCAGE_INITIE'
            WHEN 'REFUSE'   THEN 'REFUSE'
            ELSE 'INSTRUCTION'
        END,
        0
    FROM app.dossiers_credit dc
    JOIN app.agences a ON a.id = dc.agence_id
    WHERE dc.imf_id = v_imf_id
    ON CONFLICT DO NOTHING;

    -- ── 17. KPI collecte snapshot ─────────────────────────────────────────────
    INSERT INTO app.kpi_collecte_snapshots (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode,
        nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
        objectif_montant, taux_realisation_pct, taux_ponctualite_pct,
        taux_rejet_pct, nb_doublons_detectes,
        montant_especes, montant_mtn, montant_orange, montant_wave, montant_autres
    )
    SELECT
        v_imf_id,
        v_ag_yde,       -- agence de rattachement (nullable — choisir Yaoundé)
        v_cycle_id,
        NULL::BIGINT,         -- agrégat toutes agences, pas d'agent spécifique
        DATE_TRUNC('week', date_collecte)::DATE,
        'HEBDOMADAIRE',
        COUNT(*)::INT,
        SUM(montant_collecte),
        ROUND(AVG(montant_collecte), 2),
        COUNT(DISTINCT client_id_externe)::INT,
        2000000.00,
        ROUND(SUM(montant_collecte) / 2000000.0, 4),
        ROUND(COUNT(CASE WHEN statut = 'VALIDEE' THEN 1 END)::NUMERIC / COUNT(*), 4),
        ROUND(COUNT(CASE WHEN statut = 'REJETEE' THEN 1 END)::NUMERIC / COUNT(*), 4),
        0::INT,
        SUM(CASE WHEN canal_paiement = 'ESPECES'          THEN montant_collecte ELSE 0 END),
        SUM(CASE WHEN canal_paiement = 'MTN' THEN montant_collecte ELSE 0 END),
        SUM(CASE WHEN canal_paiement = 'ORANGE'     THEN montant_collecte ELSE 0 END),
        0, 0
    FROM app.collectes_epargne
    WHERE imf_id = v_imf_id
    GROUP BY DATE_TRUNC('week', date_collecte)
    ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;

    -- ── 18. Délégations initiales ─────────────────────────────────────────────
    -- Directeur → Chef d'agence : délégation autorité validation
    INSERT INTO app.delegations (imf_id, delegant_id, delegataire_id, type_delegation,
        motif, role_delegue, montant_seuil, date_debut, date_fin, actif
    ) VALUES (
        v_imf_id, v_dir, v_chef, 'DELEGATION_AUTORITE',
        'Validation des dossiers crédit jusqu''à 2 500 000 FCFA pendant l''absence du directeur',
        'CHEF_AGENCE', 2500000.00,
        CURRENT_DATE, CURRENT_DATE + 30, TRUE
    ) ON CONFLICT DO NOTHING;

    -- Réassignation d'un dossier de ac1 vers ac3
    INSERT INTO app.delegations (imf_id, delegant_id, delegataire_id, type_delegation,
        objet_id, objet_type, motif, date_debut, actif
    )
    SELECT
        v_imf_id, v_dir, v_ac3, 'REASSIGNATION_DOSSIER',
        dc.id, 'DOSSIER_CREDIT',
        'Agent ac.nguema en congé — dossier réassigné à ac.fouda',
        CURRENT_DATE, TRUE
    FROM app.dossiers_credit dc
    WHERE dc.imf_id = v_imf_id AND dc.statut = 'EN_ANALYSE' AND dc.agent_credit_id = v_ac3
    LIMIT 1
    ON CONFLICT DO NOTHING;

    -- DSI → Agent saisie : délégation accès audit
    INSERT INTO app.delegations (imf_id, delegant_id, delegataire_id, type_delegation,
        motif, role_delegue, montant_seuil, date_debut, actif
    ) VALUES (
        v_imf_id, v_dsi, v_saisie, 'DELEGATION_AUTORITE',
        'Accès supervision saisie des contrats pour 3 mois',
        'AGENT_SAISIE', 0.00,
        CURRENT_DATE, TRUE
    ) ON CONFLICT DO NOTHING;

    -- ── 19. Alertes système initiales ─────────────────────────────────────────
    INSERT INTO app.alertes_systeme (type, titre, detail, severite, statut, source, created_at)
    VALUES
        ('CREANCE_EN_RETARD',
         'Créances en retard — Action requise',
         '3 créances FINTECH SARL avec retard > 30 jours nécessitent une action recouvrement immédiate',
         'CRITIQUE','ACTIVE','SYSTEME',NOW()),
        ('SCORE_ML_ALERTE',
         'Score MCRS critique détecté',
         '2 clients FINTECH SARL présentent un score de risque MCRS > 0.75 — surveillance renforcée',
         'AVERTISSEMENT','ACTIVE','ML_ENGINE',NOW()),
        ('COLLECTE_FAIBLE',
         'Collectes Douala en baisse',
         'Les collectes de l''agence Douala Akwa sont en baisse de 18% par rapport à la semaine précédente',
         'INFO','ACTIVE','KPI_ENGINE',NOW())
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'Seed FINTECH SARL terminé. IMF_ID=%', v_imf_id;
END $$;
