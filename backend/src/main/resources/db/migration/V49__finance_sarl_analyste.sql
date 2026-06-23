c-- ============================================================
-- V49 — FINANCE SARL : réassigner l'analyste renekomtsindi559@gmail.com
--        de l'IMF FINTECH (id=1) vers FINANCE SARL
-- ============================================================

DO $$
DECLARE
    v_imf_id BIGINT;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'FINANCE SARL introuvable — V49 ignorée';
        RETURN;
    END IF;

    -- Réassigner l'analyste si il n'est pas encore dans FINANCE SARL
    UPDATE app.utilisateurs
    SET imf_id = v_imf_id
    WHERE email  = 'renekomtsindi559@gmail.com'
      AND imf_id != v_imf_id;

    RAISE NOTICE 'V49 OK — renekomtsindi559@gmail.com réassigné à FINANCE SARL (imf_id=%)', v_imf_id;
END $$;
