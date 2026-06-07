-- dim_date.sql
-- Dimension date — génération de 2020-01-01 à 2030-12-31
-- Inclut les jours fériés camerounais

{{
  config(
    materialized = 'table',
    schema = 'dw',
    tags = ['dw', 'dimension']
  )
}}

WITH series AS (
    SELECT generate_series(
        '2020-01-01'::date,
        '2030-12-31'::date,
        '1 day'::interval
    )::date AS date_valeur
),

-- Jours fériés officiels au Cameroun (fixes)
feries_fixes AS (
    SELECT unnest(ARRAY[
        '01-01', -- Jour de l'An
        '01-02', -- Fête de la jeunesse
        '05-01', -- Fête du Travail
        '05-20', -- Fête Nationale
        '08-15', -- Assomption
        '12-25'  -- Noël
    ]) AS mois_jour
)

SELECT
    TO_CHAR(s.date_valeur, 'YYYYMMDD')::integer         AS date_key,
    s.date_valeur,
    EXTRACT(YEAR  FROM s.date_valeur)::smallint          AS annee,
    EXTRACT(QUARTER FROM s.date_valeur)::smallint        AS trimestre,
    EXTRACT(MONTH FROM s.date_valeur)::smallint          AS mois,
    EXTRACT(WEEK  FROM s.date_valeur)::smallint          AS semaine,
    EXTRACT(DAY   FROM s.date_valeur)::smallint          AS jour_mois,
    EXTRACT(ISODOW FROM s.date_valeur)::smallint         AS jour_semaine,
    TO_CHAR(s.date_valeur, 'TMMonth')                    AS libelle_mois,
    TO_CHAR(s.date_valeur, 'TMDay')                      AS libelle_jour,
    EXTRACT(ISODOW FROM s.date_valeur) IN (6, 7)         AS est_week_end,
    TO_CHAR(s.date_valeur, 'MM-DD') IN (SELECT mois_jour FROM feries_fixes) AS est_ferie_cm
FROM series s
