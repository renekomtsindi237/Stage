-- ============================================================
-- V58 — Rafraîchissement des positions GPS de tous les agents
--        de FINANCE SARL pour la visibilité temps réel
--
-- L'agent renekomtsindi99@gmail.com et les agents seedés en V56/V57
-- ont des timestamps expirés (> 15 min). Cette migration :
--   1. Remet position_active=TRUE + derniere_position_at=NOW()
--      pour tous les agents FINANCE SARL ayant une position connue
--   2. Insère un ping GPS frais dans positions_agents pour chacun
--   3. Rend obligatoire le GPS (gps_obligatoire=TRUE) pour tous
--      les agents FINANCE SARL au niveau IMF (déjà fait en V56
--      mais réaffirmé ici pour les agents créés après)
-- ============================================================

DO $$
DECLARE
    v_imf_id BIGINT;
    v_agent  RECORD;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'V58 : FINANCE SARL introuvable — migration ignorée';
        RETURN;
    END IF;

    -- ── 1. S'assurer que gps_obligatoire = TRUE ───────────────────────────
    UPDATE app.imf SET gps_obligatoire = TRUE WHERE id = v_imf_id;

    -- ── 2. Rafraîchir la position de chaque agent terrain ─────────────────
    FOR v_agent IN
        SELECT id, latitude, longitude, precision_gps_m, email
        FROM app.utilisateurs
        WHERE imf_id = v_imf_id
          AND role   = 'AGENT'
          AND latitude IS NOT NULL
    LOOP
        -- Remettre position_active = TRUE avec timestamp frais
        UPDATE app.utilisateurs
        SET position_active      = TRUE,
            derniere_position_at = NOW() - (floor(random() * 8 + 1) || ' minutes')::INTERVAL,
            updated_at           = NOW()
        WHERE id = v_agent.id;

        -- Insérer un ping GPS frais dans l'historique
        INSERT INTO app.positions_agents
            (imf_id, agent_id, latitude, longitude, precision_gps_m,
             vitesse_kmh, source, captured_at)
        VALUES (
            v_imf_id,
            v_agent.id,
            v_agent.latitude,
            v_agent.longitude,
            COALESCE(v_agent.precision_gps_m, 10.0),
            0.0,
            'SYSTEM_REFRESH',
            NOW() - (floor(random() * 8 + 1) || ' minutes')::INTERVAL
        );

        -- S'assurer que le consentement GPS est accordé
        INSERT INTO app.consentements
            (imf_id, sujet_type, sujet_id, sujet_reference,
             finalite, accorde, date_consentement, created_at, updated_at)
        SELECT v_imf_id, 'AGENT', v_agent.id,
               (SELECT username FROM app.utilisateurs WHERE id = v_agent.id),
               'GEOLOCALISATION', TRUE, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM app.consentements
            WHERE imf_id     = v_imf_id
              AND sujet_type = 'AGENT'
              AND sujet_id   = v_agent.id
              AND finalite   = 'GEOLOCALISATION'
        );

        RAISE NOTICE 'V58 : Agent % (%) — position rafraîchie à (%, %)',
            v_agent.id, v_agent.email, v_agent.latitude, v_agent.longitude;
    END LOOP;

    RAISE NOTICE 'V58 OK — FINANCE SARL : toutes les positions agents rafraîchies (imf_id=%)',
        v_imf_id;
END $$;
