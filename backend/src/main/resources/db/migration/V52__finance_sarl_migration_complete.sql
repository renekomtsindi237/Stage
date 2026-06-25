-- ============================================================
-- V52 — FINANCE SARL : migration complète enrichie
--   · 30 nouveaux clients (CLF031–CLF060)
--   · Créances pour les 60 clients (CLF001–CLF060)
--   · Scores MCRS (ml.client_scores) pour les 60 clients
--   · 30 jours de collectes terrain (agent renekomtsindi99)
--   · KPI snapshots quotidiens pré-calculés
--   · Alertes impayes pour les clients PAR90+
-- ============================================================

DO $$
DECLARE
    v_imf_id    BIGINT;
    v_agent_id  BIGINT;
    v_analyste_id BIGINT;
    v_ag_yde    BIGINT;
    v_ag_dla    BIGINT;
    v_cycle_id  BIGINT;
BEGIN
    -- ── Résolution des IDs ────────────────────────────────────────────────────
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'FINANCE SARL introuvable — V52 ignorée';
        RETURN;
    END IF;

    SELECT id INTO v_agent_id
    FROM app.utilisateurs
    WHERE email = 'renekomtsindi99@gmail.com' AND imf_id = v_imf_id LIMIT 1;

    SELECT id INTO v_analyste_id
    FROM app.utilisateurs
    WHERE email = 'renekomtsindi559@gmail.com' AND imf_id = v_imf_id LIMIT 1;

    SELECT id INTO v_ag_yde
    FROM app.agences
    WHERE imf_id = v_imf_id AND (nom ILIKE '%Yaound%' OR nom ILIKE '%Nlongkak%') LIMIT 1;

    SELECT id INTO v_ag_dla
    FROM app.agences
    WHERE imf_id = v_imf_id AND nom ILIKE '%Douala%' LIMIT 1;

    IF v_ag_yde IS NULL THEN
        SELECT id INTO v_ag_yde FROM app.agences WHERE imf_id = v_imf_id LIMIT 1;
    END IF;
    IF v_ag_dla IS NULL THEN v_ag_dla := v_ag_yde; END IF;

    SELECT id INTO v_cycle_id
    FROM app.cycles_collecte WHERE imf_id = v_imf_id LIMIT 1;

    RAISE NOTICE 'V52 — imf=% agent=% analyste=% agYde=% agDla=% cycle=%',
        v_imf_id, v_agent_id, v_analyste_id, v_ag_yde, v_ag_dla, v_cycle_id;

    -- ══════════════════════════════════════════════════════════════════════════
    -- 1. NOUVEAUX CLIENTS CLF031–CLF060
    -- ══════════════════════════════════════════════════════════════════════════
    INSERT INTO app.clients_informels
        (imf_id, client_id_externe, nom_complet, telephone_principal, telephone_secondaire,
         agence_id, date_naissance, sexe, secteur_principal, sous_secteur,
         annees_experience, revenu_mensuel_estime, marche_principal, frequence_marche,
         niveau_education, situation_familiale, nombre_personnes_charge,
         latitude_activite, longitude_activite, adresse_activite)
    VALUES
    -- ── Clients faible risque CLF031–CLF040 ──
    (v_imf_id,'CLF031','NKWENTI Bridget','690441001','670221001',
     v_ag_yde,'1988-03-14','F','COMMERCE','Vente de denrées alimentaires',
     7,85000.00,'Marché Mokolo','QUOTIDIEN','SECONDAIRE','MARIE',3,
     3.8512,11.5034,'Quartier Nlongkak, Yaoundé'),
    (v_imf_id,'CLF032','MBARGA Théodore','677552002','652112002',
     v_ag_yde,'1991-07-22','M','AGRICOLE','Maraîchage',
     5,65000.00,'Marché Mvog-Mbi','HEBDOMADAIRE','PRIMAIRE','CELIBATAIRE',0,
     3.8201,11.4892,'Biyem-Assi, Yaoundé'),
    (v_imf_id,'CLF033','FOUDA Angeline','655443003','695553003',
     v_ag_dla,'1985-11-30','F','SERVICES','Salon de coiffure',
     9,145000.00,'Marché Sandaga','QUOTIDIEN','SUPERIEUR','MARIE',2,
     4.0622,9.7741,'Makepe, Douala'),
    (v_imf_id,'CLF034','TCHANGANI Paul','697334004','677774004',
     v_ag_dla,'1982-05-18','M','TRANSPORT','Transport urbain',
     12,110000.00,'Gare routière Douala','QUOTIDIEN','SECONDAIRE','MARIE',4,
     4.0450,9.7582,'Deido, Douala'),
    (v_imf_id,'CLF035','MVONDO Céline','658225005','670885005',
     v_ag_yde,'1993-09-08','F','COMMERCE','Vente de légumes',
     4,72000.00,'Marché Emombo','HEBDOMADAIRE','PRIMAIRE','DIVORCE',2,
     3.8344,11.5213,'Emombo, Yaoundé'),
    (v_imf_id,'CLF036','BELLO Ibrahim','699116006',NULL,
     v_ag_yde,'1979-12-03','M','ELEVAGE','Aviculture',
     15,95000.00,'Marché du Mfoundi','HEBDOMADAIRE','SECONDAIRE','MARIE',3,
     3.8605,11.5087,'Tsinga, Yaoundé'),
    (v_imf_id,'CLF037','NGUEMA Danielle','650007007','677337007',
     v_ag_dla,'1995-04-25','F','ARTISANAT','Couture et broderie',
     6,58000.00,'Grand Marché Bafoussam','BIMENSUEL','PRIMAIRE','CELIBATAIRE',0,
     5.4821,10.4213,'Centre ville, Bafoussam'),
    (v_imf_id,'CLF038','ESSOMBA Roger','688998008','655668008',
     v_ag_dla,'1977-08-11','M','COMMERCE','Commerce de textile',
     18,105000.00,'Marché Congo Douala','QUOTIDIEN','SECONDAIRE','MARIE',5,
     4.0534,9.7694,'Akwa, Douala'),
    (v_imf_id,'CLF039','NGAH Georgette','672889009',NULL,
     v_ag_yde,'1960-02-17','F','AGRICOLE','Cultures vivrières',
     22,55000.00,'Marché Mvog-Mbi','HEBDOMADAIRE','PRIMAIRE','VEUF',3,
     3.8418,11.4975,'Damas, Yaoundé'),
    (v_imf_id,'CLF040','KUETCHE Bertrand','695770010','677110010',
     v_ag_dla,'1983-06-29','M','SERVICES','Informatique et téléphonie',
     11,175000.00,'Centre commercial Bafoussam','QUOTIDIEN','SUPERIEUR','MARIE',2,
     5.4742,10.4098,'Tamdja, Bafoussam'),
    -- ── Clients risque modéré CLF041–CLF050 ──
    (v_imf_id,'CLF041','MBOHOU Pascaline','670661011','690441011',
     v_ag_yde,'1986-10-05','F','COMMERCE','Vente de poisson fumé',
     8,78000.00,'Marché Mokolo','QUOTIDIEN','SECONDAIRE','MARIE',4,
     3.8556,11.5112,'Melen, Yaoundé'),
    (v_imf_id,'CLF042','NJIFON Claude','697552012',NULL,
     v_ag_dla,'1990-03-17','M','TRANSPORT','Mototaxi',
     6,95000.00,'Gare de Bonaberi','QUOTIDIEN','SECONDAIRE','CELIBATAIRE',0,
     4.0789,9.7345,'Bonaberi, Douala'),
    (v_imf_id,'CLF043','ABANDA Suzanne','655443013','677773013',
     v_ag_dla,'1988-07-21','F','ARTISANAT','Vannerie et poterie',
     9,60000.00,'Marché Mboppi','BIMENSUEL','PRIMAIRE','MARIE',3,
     4.0678,9.7812,'Bepanda, Douala'),
    (v_imf_id,'CLF044','EKOTTO Ernest','677334014','652224014',
     v_ag_yde,'1975-01-09','M','COMMERCE','Commerce de quincaillerie',
     16,82000.00,'Marché Mvog-Ada','HEBDOMADAIRE','SECONDAIRE','DIVORCE',1,
     3.8289,11.5234,'Essos, Yaoundé'),
    (v_imf_id,'CLF045','MEYE Rose-Marie','699225015','670885015',
     v_ag_dla,'1982-09-13','F','SERVICES','Restauration',
     12,130000.00,'Centre ville Bafoussam','QUOTIDIEN','SUPERIEUR','MARIE',2,
     5.4698,10.4187,'Banengo, Bafoussam'),
    (v_imf_id,'CLF046','KAMENI Patrice','658116016',NULL,
     v_ag_yde,'1978-04-30','M','AGRICOLE','Arboriculture fruitière',
     17,68000.00,'Marché Mvog-Mbi','HEBDOMADAIRE','PRIMAIRE','MARIE',4,
     3.8140,11.5341,'Nkoabang, Yaoundé'),
    (v_imf_id,'CLF047','NDIKUM Florence','650007017','695337017',
     v_ag_dla,'1992-12-08','F','COMMERCE','Vente de pagnes',
     5,88000.00,'Marché Sandaga','HEBDOMADAIRE','SECONDAIRE','CELIBATAIRE',0,
     4.0478,9.7651,'Nkoulouloun, Douala'),
    (v_imf_id,'CLF048','ONANA Achille','688998018','655668018',
     v_ag_yde,'1985-05-24','M','TRANSPORT','Taxi interurbain',
     10,98000.00,'Gare Centrale Yaoundé','QUOTIDIEN','SECONDAIRE','MARIE',3,
     3.8623,11.5156,'Mimboman, Yaoundé'),
    (v_imf_id,'CLF049','NGOUMBA Jacqueline','672889019',NULL,
     v_ag_dla,'1980-11-15','F','ARTISANAT','Confection vêtements',
     14,55000.00,'Marché Congo','BIMENSUEL','PRIMAIRE','MARIE',2,
     4.0712,9.7528,'Cité des palmiers, Douala'),
    (v_imf_id,'CLF050','DJOULA Mathieu','695770020','677110020',
     v_ag_dla,'1974-08-03','M','ELEVAGE','Élevage de porcs',
     19,45000.00,'Marché de Bafoussam','MENSUEL','AUCUN','CELIBATAIRE',0,
     5.4614,10.4055,'Djeleng, Bafoussam'),
    -- ── Clients risque élevé CLF051–CLF055 ──
    (v_imf_id,'CLF051','BIYONG Henriette','670661021','690441021',
     v_ag_yde,'1981-02-28','F','COMMERCE','Vente de friperie',
     12,62000.00,'Marché Central Yaoundé','HEBDOMADAIRE','SECONDAIRE','MARIE',5,
     3.8445,11.5067,'Mvog-Ada, Yaoundé'),
    (v_imf_id,'CLF052','MANGA Dieudonné','697552022',NULL,
     v_ag_dla,'1976-06-14','M','AGRICOLE','Pêche artisanale',
     18,48000.00,'Marché aux poissons Douala','QUOTIDIEN','PRIMAIRE','MARIE',3,
     4.0589,9.7698,'Logpom, Douala'),
    (v_imf_id,'CLF053','EWODO Félicité','655443023','677773023',
     v_ag_yde,'1968-10-07','F','SERVICES','Blanchisserie',
     21,72000.00,'Quartier Bastos','HEBDOMADAIRE','SECONDAIRE','VEUF',4,
     3.8778,11.5223,'Nsimeyong, Yaoundé'),
    (v_imf_id,'CLF054','NKOA Simon','677334024','652224024',
     v_ag_dla,'1973-03-19','M','COMMERCE','Commerce de bois',
     20,85000.00,'Marché Sandaga','HEBDOMADAIRE','SECONDAIRE','MARIE',2,
     4.0401,9.7467,'Bonanjo, Douala'),
    (v_imf_id,'CLF055','ATEBA Cécile','699225025','670885025',
     v_ag_dla,'1990-09-02','F','ARTISANAT','Tressage perles',
     8,50000.00,'Marché artisanat Bafoussam','MENSUEL','PRIMAIRE','CELIBATAIRE',0,
     5.4855,10.4312,'Kouoptamo, Bafoussam'),
    -- ── Clients risque critique CLF056–CLF060 ──
    (v_imf_id,'CLF056','BEKOLO Martin','658116026',NULL,
     v_ag_yde,'1969-07-11','M','TRANSPORT','Location de véhicules',
     25,65000.00,'Gare Centrale Yaoundé','QUOTIDIEN','AUCUN','DIVORCE',3,
     3.8234,11.5178,'Etoug-Ebe, Yaoundé'),
    (v_imf_id,'CLF057','NLEND Patricia','650007027','695337027',
     v_ag_dla,'1977-01-26','F','COMMERCE','Vente de vivres locaux',
     16,70000.00,'Marché Mboppi','QUOTIDIEN','SECONDAIRE','MARIE',6,
     4.0612,9.7789,'New Bell, Douala'),
    (v_imf_id,'CLF058','MEPELE Rodrigue','688998028','655668028',
     v_ag_yde,'1994-04-18','M','AGRICOLE','Maraîchage périurbain',
     4,42000.00,'Marché Mvog-Mbi','HEBDOMADAIRE','PRIMAIRE','CELIBATAIRE',0,
     3.8056,11.4734,'Nkolbisson, Yaoundé'),
    (v_imf_id,'CLF059','BANOCK Philomène','672889029',NULL,
     v_ag_dla,'1965-12-09','F','SERVICES','Couture artisanale',
     28,68000.00,'Marché Congo','HEBDOMADAIRE','SECONDAIRE','VEUF',4,
     4.0534,9.7621,'Akwa Nord, Douala'),
    (v_imf_id,'CLF060','KOUM Joseph','695770030','677110030',
     v_ag_yde,'1971-08-22','M','COMMERCE','Vente de matériaux construction',
     22,75000.00,'Marché Central Yaoundé','HEBDOMADAIRE','PRIMAIRE','MARIE',3,
     3.8378,11.5045,'Nsam, Yaoundé')
    ON CONFLICT (imf_id, client_id_externe) DO NOTHING;

    -- ══════════════════════════════════════════════════════════════════════════
    -- 2. CRÉANCES CLF001–CLF060
    --    Distribution PAR : COURANT×20 · PAR30×14 · PAR60×8 · PAR90×9 · PAR180×5 · PERTE×4
    -- ══════════════════════════════════════════════════════════════════════════
    INSERT INTO app.creances
        (imf_id, agence_id, id_pret_externe, client_id_externe,
         client_informel_id,
         montant_initial, montant_impaye, capital_restant_du,
         interets_retard, penalites,
         date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
         date_ouverture_creance, jours_retard, categorie_par,
         classe_risque_cobac, taux_provision_cobac, montant_provision,
         type_garantie, valeur_garantie, statut)
    SELECT
        v_imf_id,
        CASE WHEN c.agence = 'DLA' THEN v_ag_dla ELSE v_ag_yde END,
        c.pret_id,
        c.cid,
        (SELECT ci.id FROM app.clients_informels ci
         WHERE ci.client_id_externe = c.cid AND ci.imf_id = v_imf_id LIMIT 1),
        c.montant_initial,
        c.montant_impaye,
        c.capital_restant,
        c.interets,
        c.penalites,
        CURRENT_DATE - c.jours_deblocage,
        CURRENT_DATE - c.jours_deblocage + 30,
        CASE WHEN c.jours_retard > 0 THEN CURRENT_DATE - c.jours_retard ELSE NULL END,
        CURRENT_DATE - c.jours_deblocage + 30,
        c.jours_retard,
        c.categorie_par,
        c.cobac,
        c.provision_taux,
        ROUND(c.montant_impaye * c.provision_taux, 2),
        c.garantie,
        c.val_garantie,
        c.statut
    FROM (VALUES
        -- ── COURANT (cobac A) — col 9=jours_deblocage, col 10=jours_retard ──
        ('CLF001','PRF001','YDE',450000,0,440000,0,0,180,0,'COURANT','A',0.00,'NANTISSEMENT',500000,'ACTIVE'),
        ('CLF002','PRF002','YDE',300000,0,290000,0,0,150,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF003','PRF003','DLA',680000,0,665000,0,0,165,0,'COURANT','A',0.00,'NANTISSEMENT',800000,'ACTIVE'),
        ('CLF004','PRF004','YDE',520000,0,508000,0,0,175,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF005','PRF005','DLA',390000,0,382000,0,0,155,0,'COURANT','A',0.00,'NANTISSEMENT',450000,'ACTIVE'),
        ('CLF006','PRF006','YDE',720000,0,705000,0,0,170,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF007','PRF007','DLA',285000,0,278000,0,0,145,0,'COURANT','A',0.00,'NANTISSEMENT',320000,'ACTIVE'),
        ('CLF008','PRF008','YDE',950000,0,930000,0,0,185,0,'COURANT','A',0.00,'HYPOTHEQUE',1200000,'ACTIVE'),
        ('CLF009','PRF009','DLA',410000,0,401000,0,0,160,0,'COURANT','A',0.00,'NANTISSEMENT',480000,'ACTIVE'),
        ('CLF010','PRF010','YDE',560000,0,548000,0,0,172,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF031','PRF031','YDE',380000,0,372000,0,0,140,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF032','PRF032','YDE',250000,0,245000,0,0,130,0,'COURANT','A',0.00,'NANTISSEMENT',300000,'ACTIVE'),
        ('CLF033','PRF033','DLA',840000,0,822000,0,0,158,0,'COURANT','A',0.00,'NANTISSEMENT',950000,'ACTIVE'),
        ('CLF034','PRF034','DLA',650000,0,636000,0,0,152,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF035','PRF035','YDE',320000,0,313000,0,0,135,0,'COURANT','A',0.00,'NANTISSEMENT',360000,'ACTIVE'),
        ('CLF036','PRF036','YDE',480000,0,469000,0,0,148,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF037','PRF037','DLA',290000,0,284000,0,0,125,0,'COURANT','A',0.00,'NANTISSEMENT',330000,'ACTIVE'),
        ('CLF038','PRF038','DLA',580000,0,567000,0,0,155,0,'COURANT','A',0.00,'CAUTION',0,'ACTIVE'),
        ('CLF039','PRF039','YDE',220000,0,215000,0,0,118,0,'COURANT','A',0.00,'NANTISSEMENT',250000,'ACTIVE'),
        ('CLF040','PRF040','DLA',1100000,0,1076000,0,0,165,0,'COURANT','A',0.00,'HYPOTHEQUE',1500000,'ACTIVE'),
        -- ── PAR30 (cobac B) — col 9=jours_deblocage(180), col 10=jours_retard ──
        ('CLF011','PRF011','YDE',500000,185000,312000,18500,7400,180,38,'PAR30','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        ('CLF012','PRF012','DLA',420000,168000,249000,16800,6720,180,45,'PAR30','B',0.20,'NANTISSEMENT',480000,'RECOUVREMENT_AMIABLE'),
        ('CLF013','PRF013','YDE',350000,132000,215000,13200,5280,180,35,'PAR30','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        ('CLF014','PRF014','DLA',780000,295000,478000,29500,11800,180,52,'PAR30','B',0.20,'NANTISSEMENT',900000,'RECOUVREMENT_AMIABLE'),
        ('CLF015','PRF015','YDE',460000,172000,283000,17200,6880,180,41,'PAR30','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        ('CLF016','PRF016','DLA',620000,233000,381000,23300,9320,180,48,'PAR30','B',0.20,'NANTISSEMENT',720000,'RECOUVREMENT_AMIABLE'),
        ('CLF041','PRF041','YDE',410000,148000,257000,14800,5920,180,37,'PAR30','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        ('CLF042','PRF042','DLA',540000,202000,334000,20200,8080,180,44,'PAR30','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        ('CLF043','PRF043','DLA',295000,108000,184000,10800,4320,180,32,'PAR30','B',0.20,'NANTISSEMENT',340000,'RECOUVREMENT_AMIABLE'),
        ('CLF044','PRF044','YDE',490000,183000,303000,18300,7320,180,39,'PAR30','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        -- ── PAR60 (cobac B/C) — col 9=jours_deblocage(210), col 10=jours_retard ──
        ('CLF017','PRF017','YDE',680000,348000,325000,41760,16704,210,62,'PAR60','B',0.20,'HYPOTHEQUE',800000,'RECOUVREMENT_AMIABLE'),
        ('CLF018','PRF018','DLA',520000,265000,248000,31800,12720,210,71,'PAR60','C',0.50,'NANTISSEMENT',600000,'MISE_EN_DEMEURE'),
        ('CLF045','PRF045','DLA',750000,384000,358000,46080,18432,210,68,'PAR60','C',0.50,'NANTISSEMENT',850000,'MISE_EN_DEMEURE'),
        ('CLF046','PRF046','YDE',340000,174000,162000,20880,8352,210,65,'PAR60','B',0.20,'CAUTION',0,'RECOUVREMENT_AMIABLE'),
        ('CLF047','PRF047','DLA',490000,251000,234000,30120,12048,210,77,'PAR60','C',0.50,'NANTISSEMENT',560000,'MISE_EN_DEMEURE'),
        -- ── PAR90 (cobac C) — col 9=jours_deblocage(270), col 10=jours_retard ──
        ('CLF019','PRF019','YDE',850000,612000,233000,73440,36720,270,95,'PAR90','C',0.50,'HYPOTHEQUE',950000,'MISE_EN_DEMEURE'),
        ('CLF020','PRF020','DLA',640000,461000,174000,55320,27660,270,108,'PAR90','C',0.50,'NANTISSEMENT',720000,'MISE_EN_DEMEURE'),
        ('CLF021','PRF021','YDE',570000,411000,153000,49320,24660,270,118,'PAR90','C',0.50,'CAUTION',0,'MISE_EN_DEMEURE'),
        ('CLF048','PRF048','YDE',620000,447000,168000,53640,26820,270,102,'PAR90','C',0.50,'NANTISSEMENT',700000,'MISE_EN_DEMEURE'),
        ('CLF049','PRF049','DLA',380000,274000,103000,32880,16440,270,115,'PAR90','C',0.50,'CAUTION',0,'MISE_EN_DEMEURE'),
        ('CLF050','PRF050','DLA',290000,209000,78000,25080,12540,270,125,'PAR90','C',0.50,'CAUTION',0,'MISE_EN_DEMEURE'),
        -- ── PAR180 (cobac D) — col 9=jours_deblocage(360), col 10=jours_retard ──
        ('CLF022','PRF022','DLA',960000,835000,121000,125250,62625,360,185,'PAR180','D',0.80,'HYPOTHEQUE',1100000,'CONTENTIEUX'),
        ('CLF023','PRF023','YDE',720000,626000,90000,93900,46950,360,198,'PAR180','D',0.80,'NANTISSEMENT',800000,'CONTENTIEUX'),
        ('CLF051','PRF051','YDE',540000,470000,68000,70500,35250,360,210,'PAR180','D',0.80,'CAUTION',0,'CONTENTIEUX'),
        ('CLF052','PRF052','DLA',415000,361000,52000,54150,27075,360,195,'PAR180','D',0.80,'NANTISSEMENT',460000,'CONTENTIEUX'),
        ('CLF053','PRF053','YDE',480000,418000,60000,62700,31350,360,225,'PAR180','D',0.80,'CAUTION',0,'CONTENTIEUX'),
        -- ── PERTE (cobac E) — col 9=jours_deblocage(450), col 10=jours_retard ──
        ('CLF024','PRF024','DLA',1200000,1200000,0,210000,105000,450,385,'PERTE','E',1.00,'HYPOTHEQUE',1300000,'IRRECOVERABLE'),
        ('CLF025','PRF025','YDE',880000,880000,0,154000,77000,450,412,'PERTE','E',1.00,'CAUTION',0,'IRRECOVERABLE'),
        ('CLF026','PRF026','DLA',650000,650000,0,113750,56875,450,395,'PERTE','E',1.00,'NANTISSEMENT',700000,'IRRECOVERABLE'),
        ('CLF027','PRF027','YDE',430000,430000,0,75250,37625,450,425,'PERTE','E',1.00,'CAUTION',0,'IRRECOVERABLE')
    ) AS c(cid, pret_id, agence, montant_initial, montant_impaye, capital_restant,
           interets, penalites, jours_deblocage, jours_retard, categorie_par, cobac,
           provision_taux, garantie, val_garantie, statut)
    ON CONFLICT (imf_id, id_pret_externe) DO NOTHING;

    -- Créances complémentaires pour CLF054–CLF060 (anciens clients CLF028-030 déjà couverts dans V46)
    INSERT INTO app.creances
        (imf_id, agence_id, id_pret_externe, client_id_externe,
         client_informel_id,
         montant_initial, montant_impaye, capital_restant_du,
         interets_retard, penalites,
         date_deblocage, date_premiere_echeance, date_premiere_echeance_impayee,
         date_ouverture_creance, jours_retard, categorie_par,
         classe_risque_cobac, taux_provision_cobac, montant_provision,
         type_garantie, valeur_garantie, statut)
    VALUES
    (v_imf_id, v_ag_dla,'PRF054','CLF054',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF054' AND imf_id=v_imf_id),
     850000,850000,0,148750,74375,
     CURRENT_DATE-400, CURRENT_DATE-370, CURRENT_DATE-370,
     CURRENT_DATE-370, 370,'PERTE','E',1.00,850000,'HYPOTHEQUE',950000,'IRRECOVERABLE'),
    (v_imf_id, v_ag_dla,'PRF055','CLF055',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF055' AND imf_id=v_imf_id),
     320000,320000,0,56000,28000,
     CURRENT_DATE-415, CURRENT_DATE-385, CURRENT_DATE-385,
     CURRENT_DATE-385, 385,'PERTE','E',1.00,320000,'CAUTION',0,'IRRECOVERABLE'),
    (v_imf_id, v_ag_yde,'PRF056','CLF056',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF056' AND imf_id=v_imf_id),
     680000,680000,0,119000,59500,
     CURRENT_DATE-405, CURRENT_DATE-375, CURRENT_DATE-375,
     CURRENT_DATE-375, 375,'PERTE','E',1.00,680000,'CAUTION',0,'IRRECOVERABLE'),
    (v_imf_id, v_ag_dla,'PRF057','CLF057',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF057' AND imf_id=v_imf_id),
     920000,920000,0,161000,80500,
     CURRENT_DATE-430, CURRENT_DATE-400, CURRENT_DATE-400,
     CURRENT_DATE-400, 400,'PERTE','E',1.00,920000,'NANTISSEMENT',1000000,'IRRECOVERABLE'),
    (v_imf_id, v_ag_yde,'PRF058','CLF058',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF058' AND imf_id=v_imf_id),
     280000,280000,0,49000,24500,
     CURRENT_DATE-420, CURRENT_DATE-390, CURRENT_DATE-390,
     CURRENT_DATE-390, 390,'PERTE','E',1.00,280000,'CAUTION',0,'IRRECOVERABLE'),
    (v_imf_id, v_ag_dla,'PRF059','CLF059',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF059' AND imf_id=v_imf_id),
     760000,760000,0,133000,66500,
     CURRENT_DATE-440, CURRENT_DATE-410, CURRENT_DATE-410,
     CURRENT_DATE-410, 410,'PERTE','E',1.00,760000,'NANTISSEMENT',850000,'IRRECOVERABLE'),
    (v_imf_id, v_ag_yde,'PRF060','CLF060',
     (SELECT id FROM app.clients_informels WHERE client_id_externe='CLF060' AND imf_id=v_imf_id),
     590000,590000,0,103250,51625,
     CURRENT_DATE-380, CURRENT_DATE-350, CURRENT_DATE-350,
     CURRENT_DATE-350, 350,'PERTE','E',1.00,590000,'CAUTION',0,'IRRECOVERABLE')
    ON CONFLICT (imf_id, id_pret_externe) DO NOTHING;

    -- ══════════════════════════════════════════════════════════════════════════
    -- 3. SCORES MCRS — ml.client_scores (60 clients)
    -- ══════════════════════════════════════════════════════════════════════════
    INSERT INTO ml.client_scores
        (imf_id, client_id_externe,
         score_crs, score_rps, score_csi, score_mcrs,
         niveau_risque, cobac_classe, cobac_provision_taux,
         probabilite_defaut_30j, probabilite_defaut_90j,
         score_mcrs_ic_bas, score_mcrs_ic_haut,
         action_recommandee, priorite_recouvrement,
         scored_at, valide_jusqu_au)
    SELECT v_imf_id, s.cid,
           s.crs, s.rps, s.csi, s.mcrs,
           s.niveau, s.cobac, s.prov,
           s.pd30, s.pd90,
           GREATEST(0, s.mcrs - 0.04), LEAST(1, s.mcrs + 0.04),
           s.action, s.priorite,
           NOW() - (s.row_n * INTERVAL '2 days'), CURRENT_DATE + 30
    FROM (VALUES
        -- FAIBLE (score > 0.70, COBAC A) ──────────────────────────────────────
        (1,'CLF001',0.8812,0.8645,0.8934,0.8797,'FAIBLE','A',0.00,0.0234,0.0512,'AUCUNE',5),
        (2,'CLF002',0.9012,0.8834,0.8712,0.8853,'FAIBLE','A',0.00,0.0198,0.0445,'AUCUNE',5),
        (3,'CLF003',0.8645,0.8512,0.8778,0.8645,'FAIBLE','A',0.00,0.0256,0.0578,'AUCUNE',5),
        (4,'CLF004',0.8923,0.8756,0.8834,0.8838,'FAIBLE','A',0.00,0.0212,0.0489,'AUCUNE',5),
        (5,'CLF005',0.8534,0.8412,0.8623,0.8523,'FAIBLE','A',0.00,0.0278,0.0612,'AUCUNE',5),
        (6,'CLF006',0.8756,0.8634,0.8867,0.8752,'FAIBLE','A',0.00,0.0241,0.0534,'AUCUNE',5),
        (7,'CLF007',0.9123,0.8934,0.8812,0.8956,'FAIBLE','A',0.00,0.0189,0.0423,'AUCUNE',5),
        (8,'CLF008',0.8434,0.8312,0.8545,0.8430,'FAIBLE','A',0.00,0.0289,0.0634,'AUCUNE',5),
        (9,'CLF009',0.8678,0.8534,0.8712,0.8641,'FAIBLE','A',0.00,0.0267,0.0589,'AUCUNE',5),
        (10,'CLF010',0.8812,0.8645,0.8834,0.8764,'FAIBLE','A',0.00,0.0223,0.0512,'AUCUNE',5),
        (11,'CLF031',0.8945,0.8778,0.8912,0.8878,'FAIBLE','A',0.00,0.0201,0.0456,'AUCUNE',5),
        (12,'CLF032',0.8523,0.8345,0.8634,0.8501,'FAIBLE','A',0.00,0.0284,0.0623,'AUCUNE',5),
        (13,'CLF033',0.9034,0.8867,0.8923,0.8941,'FAIBLE','A',0.00,0.0192,0.0434,'AUCUNE',5),
        (14,'CLF034',0.8712,0.8567,0.8745,0.8675,'FAIBLE','A',0.00,0.0259,0.0567,'AUCUNE',5),
        (15,'CLF035',0.8634,0.8478,0.8656,0.8589,'FAIBLE','A',0.00,0.0271,0.0601,'AUCUNE',5),
        (16,'CLF036',0.8867,0.8712,0.8845,0.8808,'FAIBLE','A',0.00,0.0218,0.0489,'AUCUNE',5),
        (17,'CLF037',0.8545,0.8389,0.8567,0.8500,'FAIBLE','A',0.00,0.0281,0.0618,'AUCUNE',5),
        (18,'CLF038',0.8778,0.8623,0.8812,0.8738,'FAIBLE','A',0.00,0.0234,0.0523,'AUCUNE',5),
        (19,'CLF039',0.8456,0.8312,0.8489,0.8419,'FAIBLE','A',0.00,0.0292,0.0645,'AUCUNE',5),
        (20,'CLF040',0.9156,0.8989,0.9012,0.9052,'FAIBLE','A',0.00,0.0178,0.0401,'AUCUNE',5),
        -- MODERE (score 0.50–0.69, COBAC B) ──────────────────────────────────
        (21,'CLF011',0.6234,0.5912,0.6145,0.6097,'MODERE','B',0.20,0.1234,0.2456,'RELANCE_PREVENTIVE',4),
        (22,'CLF012',0.5945,0.5623,0.5812,0.5793,'MODERE','B',0.20,0.1478,0.2834,'RELANCE_PREVENTIVE',4),
        (23,'CLF013',0.6412,0.6112,0.6234,0.6253,'MODERE','B',0.20,0.1189,0.2312,'RELANCE_PREVENTIVE',4),
        (24,'CLF014',0.5678,0.5389,0.5534,0.5534,'MODERE','B',0.20,0.1634,0.3089,'VISITE_TERRAIN',3),
        (25,'CLF015',0.6123,0.5834,0.6012,0.5990,'MODERE','B',0.20,0.1312,0.2612,'RELANCE_PREVENTIVE',4),
        (26,'CLF016',0.5834,0.5545,0.5712,0.5697,'MODERE','B',0.20,0.1523,0.2945,'VISITE_TERRAIN',3),
        (27,'CLF041',0.6345,0.6034,0.6178,0.6186,'MODERE','B',0.20,0.1156,0.2378,'RELANCE_PREVENTIVE',4),
        (28,'CLF042',0.6012,0.5712,0.5889,0.5871,'MODERE','B',0.20,0.1389,0.2712,'RELANCE_PREVENTIVE',4),
        (29,'CLF043',0.6234,0.5934,0.6067,0.6078,'MODERE','B',0.20,0.1245,0.2489,'RELANCE_PREVENTIVE',4),
        (30,'CLF044',0.5912,0.5612,0.5745,0.5756,'MODERE','B',0.20,0.1467,0.2867,'VISITE_TERRAIN',3),
        -- ELEVE (score 0.30–0.49, COBAC C) ───────────────────────────────────
        (31,'CLF017',0.4523,0.4212,0.4378,0.4371,'ELEVE','C',0.50,0.2956,0.4823,'VISITE_TERRAIN',3),
        (32,'CLF018',0.4234,0.3934,0.4089,0.4086,'ELEVE','C',0.50,0.3178,0.5056,'VISITE_TERRAIN',3),
        (33,'CLF019',0.3978,0.3645,0.3834,0.3819,'ELEVE','C',0.50,0.3456,0.5345,'RESTRUCTURATION',2),
        (34,'CLF020',0.3712,0.3412,0.3567,0.3564,'ELEVE','C',0.50,0.3689,0.5678,'RESTRUCTURATION',2),
        (35,'CLF021',0.4089,0.3778,0.3934,0.3934,'ELEVE','C',0.50,0.3312,0.5189,'VISITE_TERRAIN',3),
        (36,'CLF045',0.4156,0.3845,0.4012,0.4004,'ELEVE','C',0.50,0.3256,0.5112,'VISITE_TERRAIN',3),
        (37,'CLF046',0.4378,0.4067,0.4212,0.4219,'ELEVE','C',0.50,0.3023,0.4867,'VISITE_TERRAIN',3),
        (38,'CLF047',0.4012,0.3712,0.3856,0.3860,'ELEVE','C',0.50,0.3389,0.5278,'RESTRUCTURATION',2),
        (39,'CLF048',0.3845,0.3545,0.3689,0.3693,'ELEVE','C',0.50,0.3534,0.5456,'RESTRUCTURATION',2),
        (40,'CLF049',0.3634,0.3345,0.3478,0.3486,'ELEVE','C',0.50,0.3712,0.5645,'RESTRUCTURATION',2),
        -- CRITIQUE (score < 0.30, COBAC D/E) ─────────────────────────────────
        (41,'CLF022',0.2834,0.2534,0.2678,0.2682,'CRITIQUE','D',0.80,0.5234,0.7456,'MISE_EN_DEMEURE',1),
        (42,'CLF023',0.2545,0.2245,0.2389,0.2393,'CRITIQUE','D',0.80,0.5589,0.7812,'MISE_EN_DEMEURE',1),
        (43,'CLF024',0.1834,0.1567,0.1712,0.1704,'CRITIQUE','E',1.00,0.6934,0.8867,'ESCALADE_JURIDIQUE',1),
        (44,'CLF025',0.1523,0.1267,0.1412,0.1401,'CRITIQUE','E',1.00,0.7234,0.9112,'ESCALADE_JURIDIQUE',1),
        (45,'CLF026',0.1712,0.1445,0.1589,0.1582,'CRITIQUE','E',1.00,0.7012,0.8934,'ESCALADE_JURIDIQUE',1),
        (46,'CLF027',0.1934,0.1656,0.1812,0.1801,'CRITIQUE','E',1.00,0.6756,0.8712,'ESCALADE_JURIDIQUE',1),
        (47,'CLF050',0.2678,0.2378,0.2523,0.2526,'CRITIQUE','D',0.80,0.5389,0.7623,'MISE_EN_DEMEURE',1),
        (48,'CLF051',0.2312,0.2023,0.2156,0.2164,'CRITIQUE','D',0.80,0.5789,0.8034,'MISE_EN_DEMEURE',1),
        (49,'CLF052',0.2089,0.1812,0.1934,0.1945,'CRITIQUE','D',0.80,0.6056,0.8256,'MISE_EN_DEMEURE',1),
        (50,'CLF053',0.2234,0.1956,0.2078,0.2089,'CRITIQUE','D',0.80,0.5912,0.8134,'MISE_EN_DEMEURE',1),
        (51,'CLF054',0.1623,0.1356,0.1489,0.1489,'CRITIQUE','E',1.00,0.7178,0.9034,'ESCALADE_JURIDIQUE',1),
        (52,'CLF055',0.1789,0.1512,0.1645,0.1649,'CRITIQUE','E',1.00,0.6978,0.8878,'ESCALADE_JURIDIQUE',1),
        (53,'CLF056',0.1456,0.1189,0.1323,0.1323,'CRITIQUE','E',1.00,0.7356,0.9234,'ESCALADE_JURIDIQUE',1),
        (54,'CLF057',0.1934,0.1667,0.1812,0.1804,'CRITIQUE','E',1.00,0.6745,0.8678,'ESCALADE_JURIDIQUE',1),
        (55,'CLF058',0.1678,0.1412,0.1545,0.1545,'CRITIQUE','E',1.00,0.7112,0.8967,'ESCALADE_JURIDIQUE',1),
        (56,'CLF059',0.1812,0.1534,0.1678,0.1675,'CRITIQUE','E',1.00,0.6889,0.8812,'ESCALADE_JURIDIQUE',1),
        (57,'CLF060',0.2012,0.1734,0.1878,0.1875,'CRITIQUE','E',1.00,0.6612,0.8545,'ESCALADE_JURIDIQUE',1),
        -- CLF028, CLF029, CLF030 (anciens, CRITIQUE)
        (58,'CLF028',0.2145,0.1867,0.2012,0.2008,'CRITIQUE','D',0.80,0.6134,0.8312,'MISE_EN_DEMEURE',1),
        (59,'CLF029',0.1767,0.1489,0.1634,0.1630,'CRITIQUE','E',1.00,0.7023,0.8934,'ESCALADE_JURIDIQUE',1),
        (60,'CLF030',0.1589,0.1323,0.1456,0.1456,'CRITIQUE','E',1.00,0.7289,0.9156,'ESCALADE_JURIDIQUE',1)
    ) AS s(row_n, cid, crs, rps, csi, mcrs, niveau, cobac, prov, pd30, pd90, action, priorite)
    ON CONFLICT ON CONSTRAINT client_scores_client_imf_unique
    DO UPDATE SET
        score_crs = EXCLUDED.score_crs,
        score_rps = EXCLUDED.score_rps,
        score_csi = EXCLUDED.score_csi,
        score_mcrs = EXCLUDED.score_mcrs,
        niveau_risque = EXCLUDED.niveau_risque,
        cobac_classe = EXCLUDED.cobac_classe,
        cobac_provision_taux = EXCLUDED.cobac_provision_taux,
        probabilite_defaut_30j = EXCLUDED.probabilite_defaut_30j,
        probabilite_defaut_90j = EXCLUDED.probabilite_defaut_90j,
        action_recommandee = EXCLUDED.action_recommandee,
        priorite_recouvrement = EXCLUDED.priorite_recouvrement,
        scored_at = EXCLUDED.scored_at,
        updated_at = NOW();

    -- ══════════════════════════════════════════════════════════════════════════
    -- 4. COLLECTES TERRAIN — 30 derniers jours (agent renekomtsindi99)
    -- ══════════════════════════════════════════════════════════════════════════
    IF v_agent_id IS NOT NULL THEN
        INSERT INTO app.collectes_terrain
            (id_collecte_mobile, agent_id, imf_id, client_id, pret_id,
             date_collecte, montant_collecte, canal_paiement,
             statut, created_at)
        SELECT
            'FINM-' || TO_CHAR(d.day, 'YYYYMMDD') || '-' || LPAD(n.num::TEXT, 3, '0'),
            v_agent_id,
            v_imf_id,
            'CLF' || LPAD(( (EXTRACT(DOY FROM d.day)::INT + n.num * 7 - 1) % 30 + 1)::TEXT, 3, '0'),
            'PRF' || LPAD(( (EXTRACT(DOY FROM d.day)::INT + n.num * 7 - 1) % 30 + 1)::TEXT, 3, '0'),
            d.day::DATE,
            CASE n.num % 5
                WHEN 0 THEN 8500.00
                WHEN 1 THEN 12000.00
                WHEN 2 THEN 7500.00
                WHEN 3 THEN 15000.00
                WHEN 4 THEN 9500.00
            END,
            CASE n.num % 3
                WHEN 0 THEN 'ESPECES'
                WHEN 1 THEN 'MTN'
                WHEN 2 THEN 'ORANGE'
            END,
            'CONFIRMEE',
            d.day + INTERVAL '14 hours'
        FROM generate_series(CURRENT_DATE - 30, CURRENT_DATE - 1, '1 day'::INTERVAL) AS d(day),
             generate_series(1, 5) AS n(num)
        WHERE NOT EXISTS (
            SELECT 1 FROM app.collectes_terrain ct
            WHERE ct.id_collecte_mobile =
                'FINM-' || TO_CHAR(d.day, 'YYYYMMDD') || '-' || LPAD(n.num::TEXT, 3, '0')
        );
        RAISE NOTICE 'V52 — collectes terrain insérées pour agent %', v_agent_id;
    END IF;

    -- ══════════════════════════════════════════════════════════════════════════
    -- 5. ALERTES IMPAYES — PAR90+ (CLF019–CLF027 + CLF048–CLF060)
    -- ══════════════════════════════════════════════════════════════════════════
    INSERT INTO app.alertes_impayes
        (id_pret, imf_id, jours_retard, montant_en_retard, statut_alerte)
    SELECT a.id_pret, v_imf_id, a.jours, a.montant, a.statut
    FROM (VALUES
        ('PRF019', 95,  612000.00::NUMERIC, 'ACTIVE'),
        ('PRF020',108,  461000.00, 'ACTIVE'),
        ('PRF048',102,  447000.00, 'ACTIVE'),
        ('PRF049',115,  274000.00, 'ACTIVE'),
        ('PRF050',125,  209000.00, 'ACTIVE'),
        ('PRF022',185,  835000.00, 'ESCALADEE'),
        ('PRF023',198,  626000.00, 'ESCALADEE'),
        ('PRF051',210,  470000.00, 'ESCALADEE'),
        ('PRF052',195,  361000.00, 'ESCALADEE'),
        ('PRF053',225,  418000.00, 'ESCALADEE'),
        ('PRF024',385, 1200000.00, 'ESCALADEE'),
        ('PRF025',412,  880000.00, 'ESCALADEE'),
        ('PRF026',395,  650000.00, 'ESCALADEE'),
        ('PRF027',425,  430000.00, 'ESCALADEE'),
        ('PRF054',370,  850000.00, 'ESCALADEE'),
        ('PRF055',385,  320000.00, 'ESCALADEE'),
        ('PRF056',375,  680000.00, 'ESCALADEE'),
        ('PRF057',400,  920000.00, 'ESCALADEE'),
        ('PRF058',390,  280000.00, 'ESCALADEE'),
        ('PRF059',410,  760000.00, 'ESCALADEE'),
        ('PRF060',350,  590000.00, 'ESCALADEE')
    ) AS a(id_pret, jours, montant, statut)
    ON CONFLICT (id_pret, statut_alerte) DO NOTHING;

    -- ══════════════════════════════════════════════════════════════════════════
    -- 6. KPI COLLECTE SNAPSHOTS — pré-calculés pour le tableau de bord directeur
    -- ══════════════════════════════════════════════════════════════════════════
    IF v_agent_id IS NOT NULL AND v_ag_yde IS NOT NULL THEN
        INSERT INTO app.kpi_collecte_snapshots
            (imf_id, agence_id, cycle_id, agent_id,
             date_calcul, periode,
             nb_collectes, montant_total, montant_moyen, nb_clients_uniques,
             montant_especes, montant_mtn, montant_orange,
             objectif_montant, taux_realisation_pct,
             taux_ponctualite_pct, taux_rejet_pct, nb_doublons_detectes)
        SELECT
            v_imf_id, v_ag_yde, v_cycle_id, v_agent_id,
            d.day::DATE, 'QUOTIDIEN',
            5,
            CASE EXTRACT(DOW FROM d.day)::INT % 3
                WHEN 0 THEN 52500.00
                WHEN 1 THEN 44000.00
                ELSE     48500.00
            END,
            CASE EXTRACT(DOW FROM d.day)::INT % 3
                WHEN 0 THEN 10500.00
                WHEN 1 THEN  8800.00
                ELSE      9700.00
            END,
            5,
            -- montant_especes, montant_mtn, montant_orange
            17500.00, 17500.00, 17500.00,
            60000.00,
            CASE EXTRACT(DOW FROM d.day)::INT % 3
                WHEN 0 THEN 0.8750
                WHEN 1 THEN 0.7333
                ELSE     0.8083
            END,
            0.9600, 0.0200, 0
        FROM generate_series(CURRENT_DATE - 30, CURRENT_DATE - 1, '1 day'::INTERVAL) AS d(day)
        ON CONFLICT (imf_id, agence_id, cycle_id, agent_id, date_calcul, periode) DO NOTHING;
        RAISE NOTICE 'V52 — KPI snapshots quotidiens insérés';
    END IF;

    RAISE NOTICE 'V52 terminée — 30 clients + créances + scores + collectes + alertes + KPI pour FINANCE SARL (imf_id=%)', v_imf_id;

END $$;
