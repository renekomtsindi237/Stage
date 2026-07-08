{#
  Surcharge du macro standard de dbt : sans elle, dbt préfixe systématiquement
  tout schéma personnalisé (+schema: ml/intermediate/dw/staging) par le schéma
  cible du profil (profiles.yml: schema: staging) — ex. "staging_ml" au lieu
  de "ml". Aucun de ces schémas doublés n'existe réellement dans la base
  (confirmé : ml.client_scores, ml.features_client, app.* existent bien
  sous leur nom simple), donc tout dbt run échouait avec "relation does not
  exist" dès qu'un modèle référençait une source/un ref dans un autre schéma.
  Pattern standard documenté par dbt : https://docs.getdbt.com/docs/build/custom-schemas
#}
{% macro generate_schema_name(custom_schema_name, node) -%}
    {%- if custom_schema_name is none -%}
        {{ target.schema }}
    {%- else -%}
        {{ custom_schema_name | trim }}
    {%- endif -%}
{%- endmacro %}
