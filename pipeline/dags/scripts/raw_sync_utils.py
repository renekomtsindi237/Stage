"""
raw_sync_utils.py — Alimente raw.export_cbs / raw.collectes_terrain.

app.* et raw.* vivent dans la même base Postgres : la synchronisation se
fait entièrement en SQL (INSERT ... SELECT ... ON CONFLICT), sans aller-
retour Python. Ce n'est pas un export CBS ni une synchronisation mobile
externe réelle — aucune des deux n'est connectée à ce jour (limite
documentée dans docs/V0/06_Doc_Systeme/MCRS_Deploiement_Modele.md). Ce
job recopie les données déjà réelles de app.creances/app.clients_informels
et app.collectes_terrain vers la forme texte brut que les modèles dbt
staging (stg_clients, stg_creances, stg_collectes_epargne) attendent
depuis l'origine du projet, pour que la couche raw -> staging fonctionne
avec de la donnée à jour plutôt que le seed figé qui l'alimentait jusque-là.

Appelé par dag_raw_sync, avant dag_ml_scoring (feat_comportemental a besoin
de stg_creances/stg_collectes_epargne à jour).
"""

from __future__ import annotations

import logging

from pipeline.src.database import db_session

logger = logging.getLogger(__name__)


def sync_export_cbs(**ctx) -> int:
    """Recopie app.creances + app.clients_informels vers raw.export_cbs."""
    sql = """
        INSERT INTO raw.export_cbs (
            imf_code, id_pret, id_client, nom_client, telephone_client,
            agence_code, nom_agence, produit_code, agent_cbs_code,
            montant_pret, montant_rembourse, solde_restant, montant_impaye,
            date_deblocage, date_echeance, date_derniere_echeance_impayee,
            jours_retard, statut_pret, type_garantie, valeur_garantie,
            nom_caution, date_ingestion, statut_ingestion, recu_at
        )
        SELECT
            i.code,
            cr.id_pret_externe,
            cr.client_id_externe,
            ci.nom_complet,
            ci.telephone_principal,
            a.nom,
            a.nom,
            NULL,
            u.username,
            cr.montant_initial::TEXT,
            (cr.montant_initial - COALESCE(cr.capital_restant_du, cr.montant_initial))::TEXT,
            cr.capital_restant_du::TEXT,
            cr.montant_impaye::TEXT,
            cr.date_deblocage::TEXT,
            cr.date_premiere_echeance::TEXT,
            cr.date_premiere_echeance_impayee::TEXT,
            cr.jours_retard::TEXT,
            cr.statut,
            cr.type_garantie,
            cr.valeur_garantie::TEXT,
            cr.nom_caution,
            NOW()::TEXT,
            'BRUT',
            NOW()
        FROM app.creances cr
        JOIN app.imf i ON i.id = cr.imf_id
        LEFT JOIN app.clients_informels ci
            ON ci.client_id_externe = cr.client_id_externe AND ci.imf_id = cr.imf_id
        LEFT JOIN app.agences a ON a.id = cr.agence_id
        LEFT JOIN app.utilisateurs u ON u.id = cr.agent_responsable_id
        ON CONFLICT (imf_code, id_pret) DO UPDATE SET
            nom_client                     = EXCLUDED.nom_client,
            telephone_client                = EXCLUDED.telephone_client,
            agence_code                     = EXCLUDED.agence_code,
            nom_agence                      = EXCLUDED.nom_agence,
            agent_cbs_code                  = EXCLUDED.agent_cbs_code,
            montant_pret                    = EXCLUDED.montant_pret,
            montant_rembourse               = EXCLUDED.montant_rembourse,
            solde_restant                   = EXCLUDED.solde_restant,
            montant_impaye                  = EXCLUDED.montant_impaye,
            date_deblocage                  = EXCLUDED.date_deblocage,
            date_echeance                   = EXCLUDED.date_echeance,
            date_derniere_echeance_impayee  = EXCLUDED.date_derniere_echeance_impayee,
            jours_retard                    = EXCLUDED.jours_retard,
            statut_pret                     = EXCLUDED.statut_pret,
            type_garantie                   = EXCLUDED.type_garantie,
            valeur_garantie                 = EXCLUDED.valeur_garantie,
            nom_caution                     = EXCLUDED.nom_caution,
            date_ingestion                  = EXCLUDED.date_ingestion,
            recu_at                         = EXCLUDED.recu_at
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("raw.export_cbs synchronisé : %d lignes", n)
    return n


def sync_collectes_terrain(**ctx) -> int:
    """Recopie app.collectes_terrain (statut CONFIRMEE) vers raw.collectes_terrain."""
    sql = """
        INSERT INTO raw.collectes_terrain (
            uuid_mobile, imf_code, agent_username, client_id_externe,
            montant_collecte, date_collecte, canal_paiement,
            reference_transaction, latitude, longitude, observation,
            hash_sha256, statut_ingestion, recu_at
        )
        SELECT
            ct.id_collecte_mobile,
            i.code,
            u.username,
            ct.client_id,
            ct.montant_collecte::TEXT,
            ct.date_collecte::TEXT,
            ct.canal_paiement,
            ct.reference_transaction,
            ct.latitude::TEXT,
            ct.longitude::TEXT,
            ct.observation,
            ENCODE(SHA256(ct.id_collecte_mobile::BYTEA), 'hex'),
            'RECU',
            NOW()
        FROM app.collectes_terrain ct
        JOIN app.imf i ON i.id = ct.imf_id
        LEFT JOIN app.utilisateurs u ON u.id = ct.agent_id
        WHERE ct.statut = 'CONFIRMEE'
        ON CONFLICT (hash_sha256) DO UPDATE SET
            montant_collecte       = EXCLUDED.montant_collecte,
            date_collecte           = EXCLUDED.date_collecte,
            canal_paiement           = EXCLUDED.canal_paiement,
            reference_transaction     = EXCLUDED.reference_transaction,
            latitude                   = EXCLUDED.latitude,
            longitude                   = EXCLUDED.longitude,
            observation                  = EXCLUDED.observation,
            recu_at                       = EXCLUDED.recu_at
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("raw.collectes_terrain synchronisé : %d lignes", n)
    return n
