-- Alignement sync mobile : canaux Flutter (MTN/ORANGE/WAVE) + prêt optionnel.
-- Sans ça, INSERT collectes_terrain échoue → tx rollback-only → HTTP 500.

DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'app'
          AND rel.relname = 'collectes_terrain'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%canal_paiement%'
    LOOP
        EXECUTE format('ALTER TABLE app.collectes_terrain DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE app.collectes_terrain
    ALTER COLUMN canal_paiement TYPE VARCHAR(30);

ALTER TABLE app.collectes_terrain
    ADD CONSTRAINT collectes_terrain_canal_check
    CHECK (canal_paiement IN (
        'MTN', 'ORANGE', 'ESPECES', 'WAVE', 'VIREMENT',
        'MTN_MOBILE_MONEY', 'ORANGE_MONEY', 'CHEQUE'
    ));

ALTER TABLE app.collectes_terrain
    ALTER COLUMN pret_id DROP NOT NULL;

ALTER TABLE app.collectes_terrain
    ALTER COLUMN pret_id SET DEFAULT 'SANS_PRET';

UPDATE app.collectes_terrain
SET pret_id = 'SANS_PRET'
WHERE pret_id IS NULL OR btrim(pret_id) = '';

ALTER TABLE app.collectes_terrain
    ALTER COLUMN client_id TYPE VARCHAR(100);
