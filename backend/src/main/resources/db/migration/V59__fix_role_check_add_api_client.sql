-- ============================================================
-- V59__fix_role_check_add_api_client.sql
--
-- Bug : ApiClientService.create() insère un utilisateur système avec
-- role = 'API_CLIENT' (cf. ExternalApiController, @PreAuthorize
-- hasRole('API_CLIENT')), mais la contrainte utilisateurs_role_check
-- (V50) ne liste pas 'API_CLIENT' parmi les valeurs autorisées :
--   ERROR: new row for relation "utilisateurs" violates check
--   constraint "utilisateurs_role_check"
-- Conséquence : toute création de clé API pour un système externe
-- (BluCash, CBS) échouait en 500, empêchant l'intégration externe
-- documentée (api_docs/02_integration_blucash.md) de fonctionner.
-- ============================================================

ALTER TABLE app.utilisateurs DROP CONSTRAINT IF EXISTS utilisateurs_role_check;
ALTER TABLE app.utilisateurs
    ADD CONSTRAINT utilisateurs_role_check
    CHECK (role IN (
        'SUPER_ADMIN',
        'DIRECTEUR',
        'RESPONSABLE_RECOUVREMENT',
        'ANALYSTE',
        'DSI',
        'SUPPORT',
        'AGENT',
        'AGENT_CREDIT',
        'CHEF_AGENCE',
        'ANALYSTE_ENGAGEMENTS',
        'AGENT_SAISIE',
        'CAISSIER',
        'API_CLIENT'
    ));
