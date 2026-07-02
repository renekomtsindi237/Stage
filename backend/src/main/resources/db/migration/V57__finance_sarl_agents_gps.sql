-- ============================================================
-- V57 — FINANCE SARL : 5 agents terrain supplémentaires avec
--        positions GPS temps réel sur Yaoundé
--
-- Agents créés :
--   agent.melen        → Yaoundé Melen (Marché Central)
--   agent.bastos       → Yaoundé Bastos (quartier résidentiel)
--   agent.mvogada      → Yaoundé Mvog-Ada (marché)
--   agent.omnisport    → Yaoundé Omnisport (zone commerciale)
--   agent.briqueterie  → Yaoundé Briqueterie (marché Islam)
--
-- Consentements GPS accordés, positions en temps réel seedées.
-- ============================================================

DO $$
DECLARE
    v_imf_id   BIGINT;
    v_uid      BIGINT;

BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'V57 : FINANCE SARL introuvable — migration ignorée';
        RETURN;
    END IF;

    -- ── 1. Créer les 5 agents ──────────────────────────────────────────────

    -- agent.melen — Melen, autour du marché central
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         latitude, longitude, precision_gps_m,
         derniere_position_at, position_active,
         must_change_password, created_at, updated_at)
    SELECT 'agent.melen',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.melen@finance-sarl.cm',
           v_imf_id, TRUE,
           3.8603, 11.5189, 8.0,
           NOW() - INTERVAL '4 minutes', TRUE,
           FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.melen@finance-sarl.cm'
    );

    -- agent.bastos — Bastos, rue du marché
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         latitude, longitude, precision_gps_m,
         derniere_position_at, position_active,
         must_change_password, created_at, updated_at)
    SELECT 'agent.bastos',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.bastos@finance-sarl.cm',
           v_imf_id, TRUE,
           3.8821, 11.5102, 10.5,
           NOW() - INTERVAL '11 minutes', TRUE,
           FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.bastos@finance-sarl.cm'
    );

    -- agent.mvogada — Mvog-Ada, marché populaire
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         latitude, longitude, precision_gps_m,
         derniere_position_at, position_active,
         must_change_password, created_at, updated_at)
    SELECT 'agent.mvogada',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.mvogada@finance-sarl.cm',
           v_imf_id, TRUE,
           3.8462, 11.5247, 14.0,
           NOW() - INTERVAL '22 minutes', FALSE,
           FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.mvogada@finance-sarl.cm'
    );

    -- agent.omnisport — Omnisport, zone commerciale Warda
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         latitude, longitude, precision_gps_m,
         derniere_position_at, position_active,
         must_change_password, created_at, updated_at)
    SELECT 'agent.omnisport',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.omnisport@finance-sarl.cm',
           v_imf_id, TRUE,
           3.8553, 11.5318, 6.5,
           NOW() - INTERVAL '7 minutes', TRUE,
           FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.omnisport@finance-sarl.cm'
    );

    -- agent.briqueterie — Briqueterie, marché Islam
    INSERT INTO app.utilisateurs
        (username, password_hash, role, email, imf_id, actif,
         latitude, longitude, precision_gps_m,
         derniere_position_at, position_active,
         must_change_password, created_at, updated_at)
    SELECT 'agent.briqueterie',
           '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
           'AGENT', 'agent.briqueterie@finance-sarl.cm',
           v_imf_id, TRUE,
           3.8735, 11.5154, 11.0,
           NOW() - INTERVAL '3 minutes', TRUE,
           FALSE, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.briqueterie@finance-sarl.cm'
    );

    -- ── 2. Accorder le consentement GPS à chaque nouvel agent ─────────────

    FOR v_uid IN
        SELECT id FROM app.utilisateurs
        WHERE email IN (
            'agent.melen@finance-sarl.cm',
            'agent.bastos@finance-sarl.cm',
            'agent.mvogada@finance-sarl.cm',
            'agent.omnisport@finance-sarl.cm',
            'agent.briqueterie@finance-sarl.cm'
        )
          AND imf_id = v_imf_id
    LOOP
        INSERT INTO app.consentements
            (imf_id, sujet_type, sujet_id, sujet_reference,
             finalite, accorde, date_consentement, created_at, updated_at)
        SELECT v_imf_id, 'AGENT', v_uid,
               (SELECT username FROM app.utilisateurs WHERE id = v_uid),
               'GEOLOCALISATION', TRUE, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM app.consentements
            WHERE imf_id     = v_imf_id
              AND sujet_type = 'AGENT'
              AND sujet_id   = v_uid
              AND finalite   = 'GEOLOCALISATION'
        );
    END LOOP;

    -- ── 3. Historique GPS pour chaque agent (trajet journée) ──────────────
    -- agent.melen — trajet Melen → marché central (mouvement nord)
    INSERT INTO app.positions_agents
        (imf_id, agent_id, latitude, longitude, precision_gps_m, vitesse_kmh, source, captured_at)
    SELECT v_imf_id,
           (SELECT id FROM app.utilisateurs WHERE email = 'agent.melen@finance-sarl.cm'),
           3.8603 - (pts.i * 0.0009),
           11.5189 + (pts.i * 0.0004),
           7.0 + pts.i,
           CASE WHEN pts.i = 0 THEN 0 ELSE 12.0 + pts.i * 1.5 END,
           'MOBILE',
           NOW() - ((8 - pts.i) * INTERVAL '7 minutes')
    FROM generate_series(0, 7) AS pts(i)
    WHERE EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.melen@finance-sarl.cm'
    )
      AND NOT EXISTS (
          SELECT 1 FROM app.positions_agents pa
          JOIN app.utilisateurs u ON pa.agent_id = u.id
          WHERE u.email = 'agent.melen@finance-sarl.cm'
            AND pa.captured_at::DATE = CURRENT_DATE
          LIMIT 1
      );

    -- agent.bastos — trajet quartier résidentiel → zone commerciale
    INSERT INTO app.positions_agents
        (imf_id, agent_id, latitude, longitude, precision_gps_m, vitesse_kmh, source, captured_at)
    SELECT v_imf_id,
           (SELECT id FROM app.utilisateurs WHERE email = 'agent.bastos@finance-sarl.cm'),
           3.8821 - (pts.i * 0.0006),
           11.5102 + (pts.i * 0.0007),
           9.0 + pts.i * 0.5,
           CASE WHEN pts.i < 2 THEN 0 ELSE 20.0 END,
           'MOBILE',
           NOW() - ((6 - pts.i) * INTERVAL '10 minutes')
    FROM generate_series(0, 5) AS pts(i)
    WHERE EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.bastos@finance-sarl.cm'
    )
      AND NOT EXISTS (
          SELECT 1 FROM app.positions_agents pa
          JOIN app.utilisateurs u ON pa.agent_id = u.id
          WHERE u.email = 'agent.bastos@finance-sarl.cm'
            AND pa.captured_at::DATE = CURRENT_DATE
          LIMIT 1
      );

    -- agent.mvogada — arrêté depuis > 15 min (position_active=FALSE)
    INSERT INTO app.positions_agents
        (imf_id, agent_id, latitude, longitude, precision_gps_m, vitesse_kmh, source, captured_at)
    SELECT v_imf_id,
           (SELECT id FROM app.utilisateurs WHERE email = 'agent.mvogada@finance-sarl.cm'),
           3.8462 + (pts.i * 0.0005),
           11.5247 - (pts.i * 0.0003),
           12.0,
           CASE WHEN pts.i < 4 THEN 18.0 ELSE 0 END,
           'MOBILE',
           NOW() - ((5 - pts.i) * INTERVAL '6 minutes')
    FROM generate_series(0, 4) AS pts(i)
    WHERE EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.mvogada@finance-sarl.cm'
    )
      AND NOT EXISTS (
          SELECT 1 FROM app.positions_agents pa
          JOIN app.utilisateurs u ON pa.agent_id = u.id
          WHERE u.email = 'agent.mvogada@finance-sarl.cm'
            AND pa.captured_at::DATE = CURRENT_DATE
          LIMIT 1
      );

    -- agent.omnisport — en déplacement rapide
    INSERT INTO app.positions_agents
        (imf_id, agent_id, latitude, longitude, precision_gps_m, vitesse_kmh, source, captured_at)
    SELECT v_imf_id,
           (SELECT id FROM app.utilisateurs WHERE email = 'agent.omnisport@finance-sarl.cm'),
           3.8553 + (pts.i * 0.0012),
           11.5318 - (pts.i * 0.0008),
           6.0,
           28.0 + pts.i * 2,
           'MOBILE',
           NOW() - ((4 - pts.i) * INTERVAL '5 minutes')
    FROM generate_series(0, 3) AS pts(i)
    WHERE EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.omnisport@finance-sarl.cm'
    )
      AND NOT EXISTS (
          SELECT 1 FROM app.positions_agents pa
          JOIN app.utilisateurs u ON pa.agent_id = u.id
          WHERE u.email = 'agent.omnisport@finance-sarl.cm'
            AND pa.captured_at::DATE = CURRENT_DATE
          LIMIT 1
      );

    -- agent.briqueterie — départ tout récent
    INSERT INTO app.positions_agents
        (imf_id, agent_id, latitude, longitude, precision_gps_m, vitesse_kmh, source, captured_at)
    SELECT v_imf_id,
           (SELECT id FROM app.utilisateurs WHERE email = 'agent.briqueterie@finance-sarl.cm'),
           3.8735 - (pts.i * 0.0007),
           11.5154 + (pts.i * 0.0010),
           10.0 + pts.i,
           CASE WHEN pts.i = 0 THEN 0 ELSE 15.0 + pts.i * 3 END,
           'MOBILE',
           NOW() - ((3 - pts.i) * INTERVAL '4 minutes')
    FROM generate_series(0, 2) AS pts(i)
    WHERE EXISTS (
        SELECT 1 FROM app.utilisateurs WHERE email = 'agent.briqueterie@finance-sarl.cm'
    )
      AND NOT EXISTS (
          SELECT 1 FROM app.positions_agents pa
          JOIN app.utilisateurs u ON pa.agent_id = u.id
          WHERE u.email = 'agent.briqueterie@finance-sarl.cm'
            AND pa.captured_at::DATE = CURRENT_DATE
          LIMIT 1
      );

    RAISE NOTICE 'V57 OK — FINANCE SARL : 5 agents terrain seedés avec positions GPS sur Yaoundé (imf_id=%)',
        v_imf_id;
END $$;
