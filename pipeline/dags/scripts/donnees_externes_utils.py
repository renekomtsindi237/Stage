"""
donnees_externes_utils.py — Collecte des données économiques et météo externes.

Appelé par dag_donnees_externes (04h00 chaque jour) pour alimenter :
- app.prix_produits       : prix marchés (saisie terrain + MINCOMMERCE)
- app.facteurs_macro      : indicateurs BEAC / INS Cameroun
- app.donnees_meteo       : données Open-Meteo par zone géographique
- app.evenements_calendrier : fêtes, marchés, saisons agricoles

Toutes les fonctions sont idempotentes (ON CONFLICT DO UPDATE).
Les erreurs réseau sont loggées sans exception pour garantir la robustesse du DAG.
"""

from __future__ import annotations

import logging
import os
from datetime import date, timedelta

import httpx

from pipeline.src.database import db_session, readonly_session

logger = logging.getLogger(__name__)

OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
MINCOMMERCE_URL_BASE = os.getenv(
    "MINCOMMERCE_API_URL", "https://mincommerce.cm/api/prix"
)
BEAC_API_URL = os.getenv("BEAC_API_URL", "https://www.beac.int/api/indicateurs")
INS_API_URL = os.getenv("INS_API_URL", "https://www.inseed.cm/api/indicateurs")

HTTP_TIMEOUT = int(os.getenv("FETCH_HTTP_TIMEOUT_SECONDS", "30"))


# ─── 1. Prix marchés — saisie terrain ────────────────────────────────────────


def fetch_prix_marche_agents_terrain(**ctx) -> int:
    """
    Récupère les prix marchés saisis par les agents terrain via l'API Spring Boot
    (endpoint /internal/prix-marche/batch) et les insère dans app.prix_produits.

    Ces prix sont saisis dans l'application mobile Flutter par les agents
    lors de leurs visites de terrain. Retourne le nombre de lignes insérées.
    """
    from pipeline.src.config import settings

    url = f"{settings.api.spring_base_url}/internal/prix-marche/batch"
    params = {"date": str(date.today())}
    headers = {"X-Api-Key": settings.api.api_key}

    try:
        with httpx.Client(timeout=HTTP_TIMEOUT) as client:
            resp = client.get(url, params=params, headers=headers)
            resp.raise_for_status()
        data: list[dict] = resp.json()
    except Exception as exc:
        logger.warning("fetch_prix_marche_agents_terrain — erreur API : %s", exc)
        return 0

    if not data:
        logger.info("Aucun prix terrain disponible pour le %s", date.today())
        return 0

    sql = """
        INSERT INTO app.prix_produits (
            produit_id, imf_id, agence_id, zone_id,
            prix_unitaire, unite, source, date_collecte, created_at
        ) VALUES (
            %(produit_id)s, %(imf_id)s, %(agence_id)s, %(zone_id)s,
            %(prix_unitaire)s, %(unite)s, 'AGENT_TERRAIN', %(date_collecte)s, NOW()
        )
        ON CONFLICT (produit_id, zone_id, source, date_collecte) DO UPDATE
          SET prix_unitaire = EXCLUDED.prix_unitaire
    """
    n = 0
    with db_session() as cur:
        for item in data:
            try:
                cur.execute(
                    sql,
                    {
                        "produit_id": item["produit_id"],
                        "imf_id": item.get("imf_id"),
                        "agence_id": item.get("agence_id"),
                        "zone_id": item.get("zone_id"),
                        "prix_unitaire": float(item["prix_unitaire"]),
                        "unite": item.get("unite", "kg"),
                        "date_collecte": item.get("date_collecte", str(date.today())),
                    },
                )
                n += 1
            except (KeyError, ValueError, TypeError) as exc:
                logger.debug("Prix terrain ignoré : %s", exc)

    logger.info("fetch_prix_marche_agents_terrain : %d prix insérés", n)
    return n


# ─── 2. Prix marchés — MINCOMMERCE ───────────────────────────────────────────


def fetch_prix_mincommerce(
    url_base: str | None = None,
    produits_suivis: list[str] | None = None,
    **ctx,
) -> int:
    """
    Appelle l'API MINCOMMERCE (Direction de la Concurrence et du Commerce)
    pour récupérer les prix officiels des produits de grande consommation.

    produits_suivis : liste de codes produits (ex. ['mais', 'riz', 'haricot', 'manioc']).
    Retourne le nombre de prix insérés.
    """
    base = url_base or MINCOMMERCE_URL_BASE
    produits = produits_suivis or [
        "mais",
        "riz",
        "haricot",
        "manioc",
        "huile_palme",
        "sucre",
        "sel",
        "tomate",
        "oignon",
        "plantain",
    ]

    sql = """
        INSERT INTO app.prix_produits (
            produit_code, zone_id, prix_unitaire, unite, source, date_collecte, created_at
        ) VALUES (
            %(produit_code)s, %(zone_id)s, %(prix_unitaire)s, %(unite)s,
            'MINCOMMERCE', %(date_collecte)s, NOW()
        )
        ON CONFLICT (produit_code, zone_id, source, date_collecte) DO UPDATE
          SET prix_unitaire = EXCLUDED.prix_unitaire
    """
    n = 0
    today_str = str(date.today())

    for produit in produits:
        try:
            with httpx.Client(timeout=HTTP_TIMEOUT) as client:
                resp = client.get(f"{base}/{produit}", params={"date": today_str})
                if resp.status_code == 404:
                    logger.debug("MINCOMMERCE : produit '%s' non trouvé", produit)
                    continue
                resp.raise_for_status()
            items: list[dict] = resp.json().get("data", [])
        except Exception as exc:
            logger.warning("MINCOMMERCE fetch '%s' : %s", produit, exc)
            continue

        with db_session() as cur:
            for item in items:
                try:
                    cur.execute(
                        sql,
                        {
                            "produit_code": produit,
                            "zone_id": item.get("region_code", "CM"),
                            "prix_unitaire": float(item["prix"]),
                            "unite": item.get("unite", "kg"),
                            "date_collecte": item.get("date", today_str),
                        },
                    )
                    n += 1
                except (KeyError, ValueError, TypeError) as exc:
                    logger.debug("Prix MINCOMMERCE ignoré (%s) : %s", produit, exc)

    logger.info(
        "fetch_prix_mincommerce : %d prix insérés (%d produits)", n, len(produits)
    )
    return n


# ─── 3. Indicateurs BEAC ─────────────────────────────────────────────────────


def fetch_indicateurs_beac(
    indicateurs: list[str] | None = None,
    **ctx,
) -> int:
    """
    Récupère les indicateurs macro-économiques publiés par la Banque des États
    de l'Afrique Centrale (BEAC) : taux directeur, masse monétaire, inflation CEMAC.

    Retourne le nombre de lignes insérées dans app.facteurs_macro.
    """
    indics = indicateurs or [
        "taux_directeur",
        "inflation_cemac",
        "taux_change_eur_xaf",
        "reserve_change_cemac",
        "credit_economie_cmr",
    ]

    sql = """
        INSERT INTO app.facteurs_macro (
            indicateur, valeur, unite, source, pays, date_publication, created_at
        ) VALUES (
            %(indicateur)s, %(valeur)s, %(unite)s, 'BEAC', 'CM',
            %(date_publication)s, NOW()
        )
        ON CONFLICT (indicateur, source, date_publication) DO UPDATE
          SET valeur = EXCLUDED.valeur
    """

    today_str = str(date.today())
    n = 0

    for indic in indics:
        try:
            with httpx.Client(timeout=HTTP_TIMEOUT) as client:
                resp = client.get(
                    BEAC_API_URL,
                    params={"code": indic, "date": today_str},
                )
                if resp.status_code == 404:
                    logger.debug("BEAC : indicateur '%s' non disponible", indic)
                    continue
                resp.raise_for_status()
            data = resp.json()
        except Exception as exc:
            logger.warning("BEAC fetch '%s' : %s", indic, exc)
            continue

        try:
            with db_session() as cur:
                cur.execute(
                    sql,
                    {
                        "indicateur": indic,
                        "valeur": float(data["valeur"]),
                        "unite": data.get("unite", ""),
                        "date_publication": data.get("date", today_str),
                    },
                )
                n += 1
        except (KeyError, ValueError, TypeError) as exc:
            logger.warning("BEAC indicateur '%s' ignoré : %s", indic, exc)

    logger.info("fetch_indicateurs_beac : %d indicateurs insérés", n)
    return n


# ─── 4. Indicateurs INS Cameroun ─────────────────────────────────────────────


def fetch_indicateurs_ins_cameroun(
    indicateurs: list[str] | None = None,
    **ctx,
) -> int:
    """
    Récupère les indicateurs socio-économiques de l'Institut National
    de la Statistique du Cameroun (INS) : IPC, IHPC, taux de chômage.

    Retourne le nombre de lignes insérées dans app.facteurs_macro.
    """
    indics = indicateurs or [
        "ipc_cameroun",
        "ihpc_zone_cemac",
        "taux_chomage_cameroun",
        "pib_croissance_cmr",
    ]

    sql = """
        INSERT INTO app.facteurs_macro (
            indicateur, valeur, unite, source, pays, date_publication, created_at
        ) VALUES (
            %(indicateur)s, %(valeur)s, %(unite)s, 'INS_CMR', 'CM',
            %(date_publication)s, NOW()
        )
        ON CONFLICT (indicateur, source, date_publication) DO UPDATE
          SET valeur = EXCLUDED.valeur
    """

    today_str = str(date.today())
    n = 0

    for indic in indics:
        try:
            with httpx.Client(timeout=HTTP_TIMEOUT) as client:
                resp = client.get(
                    INS_API_URL,
                    params={"indicateur": indic, "periode": today_str[:7]},  # YYYY-MM
                )
                if resp.status_code == 404:
                    continue
                resp.raise_for_status()
            data = resp.json()
        except Exception as exc:
            logger.warning("INS fetch '%s' : %s", indic, exc)
            continue

        try:
            with db_session() as cur:
                cur.execute(
                    sql,
                    {
                        "indicateur": indic,
                        "valeur": float(data["valeur"]),
                        "unite": data.get("unite", ""),
                        "date_publication": data.get("date_publication", today_str),
                    },
                )
                n += 1
        except (KeyError, ValueError, TypeError) as exc:
            logger.warning("INS indicateur '%s' ignoré : %s", indic, exc)

    logger.info("fetch_indicateurs_ins_cameroun : %d indicateurs insérés", n)
    return n


# ─── 5. Météo — Open-Meteo ────────────────────────────────────────────────────


# Coordonnées des villes principales — utilisées pour résoudre un zone_id
# réel (ex: "YDE-NLONGKAK", "DLA-BONABERI") vers des coordonnées, via le
# préfixe avant le tiret. Approximation délibérée : la météo ne varie pas
# assez à l'échelle d'un arrondissement pour justifier un géocodage par
# quartier, et app.marches_locaux (qui porterait des coordonnées plus
# fines par zone_id) est vide en pratique.
VILLES_PREFIXES: dict[str, tuple[float, float]] = {
    "YDE": (3.848, 11.502),  # Yaoundé
    "DLA": (4.050, 9.700),  # Douala
    "GAR": (9.301, 13.398),  # Garoua
    "BAF": (5.479, 10.418),  # Bafoussam
    "BTA": (4.578, 13.685),  # Bertoua
    "MAR": (10.591, 14.317),  # Maroua
    "EBO": (2.900, 11.150),  # Ebolowa
    "BAM": (5.961, 10.146),  # Bamenda
    "NGA": (7.328, 13.584),  # Ngaoundéré
    "BUE": (4.154, 9.243),  # Buea
}
VILLE_PAR_DEFAUT = (3.848, 11.502)  # Yaoundé, si le préfixe est inconnu


def fetch_meteo_open_meteo(
    zones: list[dict] | dict | None = None,
    variables: list[str] | None = None,
    jours_historique: int = 32,
    **ctx,
) -> int:
    """
    Appelle l'API gratuite Open-Meteo (`past_days` — pas de clé requise) pour
    les zones géographiques réellement utilisées par les clients
    (`app.clients_informels.zone_id`), et upsert dans app.donnees_meteo.

    zones : si fourni, liste de dicts {'nom', 'latitude', 'longitude'} —
            sinon résolu dynamiquement via `_recuperer_zones_actives()`.
            Le paramètre dict {'NOM': {'lat', 'lon'}} historique (utilisé par
            un ancien câblage du DAG avec des noms de ville plutôt que les
            vrais zone_id clients, ce qui ne joignait jamais rien côté
            features) reste accepté pour compatibilité mais n'est plus la
            source par défaut.
    jours_historique : fenêtre de rappel (Open-Meteo `past_days`, max 92) —
            32 jours donne une marge suffisante pour la moyenne glissante de
            30 jours utilisée par le calcul d'indice de sécheresse ci-dessous.

    Retourne le nombre de lignes upsertées dans app.donnees_meteo.
    """
    if isinstance(zones, dict):
        zones = [
            {"nom": nom, "latitude": coords.get("lat"), "longitude": coords.get("lon")}
            for nom, coords in zones.items()
        ]
    zones_cible = zones or _recuperer_zones_actives()

    vars_meteo = variables or [
        "precipitation_sum",
        "temperature_2m_max",
        "temperature_2m_min",
    ]

    sql_upsert = """
        INSERT INTO app.donnees_meteo (
            zone_id, date_observation, temperature_min, temperature_max,
            precipitation_mm, source, created_at
        ) VALUES (
            %(zone_id)s, %(date_observation)s, %(temperature_min)s,
            %(temperature_max)s, %(precipitation_mm)s, 'OPEN_METEO', NOW()
        )
        ON CONFLICT (zone_id, date_observation) DO UPDATE SET
            temperature_min  = EXCLUDED.temperature_min,
            temperature_max  = EXCLUDED.temperature_max,
            precipitation_mm = EXCLUDED.precipitation_mm
    """

    n = 0
    for zone in zones_cible:
        try:
            params = {
                "latitude": zone["latitude"],
                "longitude": zone["longitude"],
                "daily": ",".join(vars_meteo),
                "timezone": "Africa/Douala",
                "past_days": jours_historique,
                "forecast_days": 1,
            }
            with httpx.Client(timeout=HTTP_TIMEOUT) as client:
                resp = client.get(OPEN_METEO_URL, params=params)
                resp.raise_for_status()
            payload = resp.json()
        except Exception as exc:
            logger.warning("Open-Meteo zone '%s' : %s", zone.get("nom"), exc)
            continue

        daily = payload.get("daily", {})
        dates = daily.get("time", [])
        precip = daily.get("precipitation_sum", [])
        temp_max = daily.get("temperature_2m_max", [])
        temp_min = daily.get("temperature_2m_min", [])

        with db_session() as cur:
            for i, d in enumerate(dates):
                if i >= len(precip) or precip[i] is None:
                    continue
                try:
                    cur.execute(
                        sql_upsert,
                        {
                            "zone_id": zone["nom"],
                            "date_observation": d,
                            "temperature_min": (
                                temp_min[i] if i < len(temp_min) else None
                            ),
                            "temperature_max": (
                                temp_max[i] if i < len(temp_max) else None
                            ),
                            "precipitation_mm": float(precip[i]),
                        },
                    )
                    n += 1
                except (TypeError, ValueError, IndexError) as exc:
                    logger.debug("Météo ignorée (%s/%s) : %s", zone["nom"], d, exc)

    if n:
        _maj_indice_secheresse([z["nom"] for z in zones_cible])

    logger.info(
        "fetch_meteo_open_meteo : %d observations upsertées (%d zones)",
        n,
        len(zones_cible),
    )
    return n


def _maj_indice_secheresse(zone_ids: list[str]) -> None:
    """
    Classe indice_secheresse par comparaison au cumul de précipitation
    glissant sur 30 jours (hors jour courant), zone par zone — pas de table
    de "normales saisonnières" séparée à maintenir : la moyenne mobile
    s'auto-améliore au fil des jours à mesure que l'historique s'accumule
    dans app.donnees_meteo lui-même.
    """
    sql = """
        WITH cible AS (
            SELECT id, zone_id, date_observation, precipitation_mm
            FROM app.donnees_meteo
            WHERE zone_id = ANY(%(zone_ids)s)
        ),
        moyenne_glissante AS (
            SELECT
                c.id,
                c.precipitation_mm,
                AVG(prev.precipitation_mm) AS moy_30j
            FROM cible c
            LEFT JOIN app.donnees_meteo prev
                ON prev.zone_id = c.zone_id
                AND prev.date_observation BETWEEN c.date_observation - 30 AND c.date_observation - 1
            GROUP BY c.id, c.precipitation_mm
        )
        UPDATE app.donnees_meteo dm
        SET indice_secheresse = CASE
            WHEN mg.moy_30j IS NULL OR mg.moy_30j = 0 THEN 'NORMAL'
            WHEN mg.precipitation_mm >= mg.moy_30j * 3.0 THEN 'INONDATION_SEVERE'
            WHEN mg.precipitation_mm >= mg.moy_30j * 2.0 THEN 'INONDATION'
            WHEN mg.precipitation_mm <= mg.moy_30j * 0.40 THEN 'SECHERESSE_SEVERE'
            WHEN mg.precipitation_mm <= mg.moy_30j * 0.60 THEN 'SECHERESSE_MODEREE'
            WHEN mg.precipitation_mm <= mg.moy_30j * 0.80 THEN 'SECHERESSE_LEGERE'
            ELSE 'NORMAL'
        END
        FROM moyenne_glissante mg
        WHERE dm.id = mg.id
    """
    with db_session() as cur:
        cur.execute(sql, {"zone_ids": zone_ids})
        n = cur.rowcount
    logger.info("Indice de sécheresse recalculé : %d observations", n)


def _recuperer_zones_actives() -> list[dict]:
    """
    Résout les zone_id réellement utilisés par les clients
    (app.clients_informels.zone_id, ex: "YDE-NLONGKAK") vers des
    coordonnées, via le préfixe ville (VILLES_PREFIXES) — pas app.agences
    (aucune colonne latitude/longitude) ni app.imfs (n'existe pas, la vraie
    table est app.imf, singulier).
    """
    sql = "SELECT DISTINCT zone_id FROM app.clients_informels WHERE zone_id IS NOT NULL"
    try:
        with readonly_session() as cur:
            cur.execute(sql)
            zone_ids = [row["zone_id"] for row in cur.fetchall()]
    except Exception as exc:
        logger.warning("Impossible de récupérer les zone_id actifs : %s", exc)
        zone_ids = []

    if not zone_ids:
        return [{"nom": "YDE-CENTRE", "latitude": 3.848, "longitude": 11.502}]

    zones = []
    for zone_id in zone_ids:
        prefixe = zone_id.split("-")[0].upper()
        lat, lon = VILLES_PREFIXES.get(prefixe, VILLE_PAR_DEFAUT)
        zones.append({"nom": zone_id, "latitude": lat, "longitude": lon})
    return zones


# ─── 6. Événements calendrier ─────────────────────────────────────────────────


def fetch_evenements_calendrier(horizon_jours: int = 30, **ctx) -> int:
    """
    Insère dans app.evenements_calendrier les événements prévisibles
    sur les prochains horizon_jours :
    - Fêtes nationales camerounaises
    - Saisons agricoles (semis, récoltes)
    - Marchés hebdomadaires périodiques

    Ces événements alimentent la feature CSI 'indicateur_evenements'.
    Retourne le nombre d'événements insérés.
    """
    sql = """
        INSERT INTO app.evenements_calendrier (
            date_evenement, type_evenement, nom, impact_collecte,
            zone_id, recurrent, created_at
        ) VALUES (
            %(date_evenement)s, %(type_evenement)s, %(nom)s,
            %(impact_collecte)s, %(zone_id)s, %(recurrent)s, NOW()
        )
        ON CONFLICT (date_evenement, type_evenement, zone_id) DO NOTHING
    """

    today = date.today()
    horizon = today + timedelta(days=horizon_jours)
    evenements = _generer_evenements_cameroun(today, horizon)

    n = 0
    with db_session() as cur:
        for evt in evenements:
            try:
                cur.execute(sql, evt)
                n += 1
            except Exception as exc:
                logger.debug("Événement ignoré (%s) : %s", evt.get("nom"), exc)

    logger.info(
        "fetch_evenements_calendrier : %d événements insérés (horizon=%dj)",
        n,
        horizon_jours,
    )
    return n


def _generer_evenements_cameroun(debut: date, fin: date) -> list[dict]:
    """Génère la liste des événements calendrier pour le Cameroun."""
    evenements: list[dict] = []
    annee = debut.year

    # Fêtes nationales fixes (impact positif sur collectes : les gens ont des liquidités)
    fetes_fixes = [
        (1, 1, "Nouvel An", "POSITIF"),
        (2, 11, "Fête de la Jeunesse", "POSITIF"),
        (5, 1, "Fête du Travail", "POSITIF"),
        (5, 20, "Fête Nationale", "POSITIF"),
        (8, 15, "Assomption", "NEUTRE"),
        (12, 25, "Noël", "POSITIF"),
    ]

    for mois, jour, nom, impact in fetes_fixes:
        try:
            d = date(annee, mois, jour)
            if debut <= d <= fin:
                evenements.append(
                    {
                        "date_evenement": d,
                        "type_evenement": "FETE_NATIONALE",
                        "nom": nom,
                        "impact_collecte": impact,
                        "zone_id": "CM",
                        "recurrent": True,
                    }
                )
        except ValueError:
            pass

    # Saisons agricoles (impact sur CSI : les producteurs ont des revenus à la récolte)
    saisons = [
        (3, 15, "Début semis maïs nord", "NEGATIF"),  # dépenses semences
        (7, 1, "Récolte maïs nord", "POSITIF"),  # revenus
        (9, 1, "Début semis cacao", "NEGATIF"),
        (11, 1, "Récolte cacao début", "POSITIF"),
        (4, 1, "Récolte café arabica", "POSITIF"),
        (8, 1, "Récolte café robusta", "POSITIF"),
    ]

    for mois, jour, nom, impact in saisons:
        try:
            d = date(annee, mois, jour)
            if debut <= d <= fin:
                evenements.append(
                    {
                        "date_evenement": d,
                        "type_evenement": "SAISON_AGRICOLE",
                        "nom": nom,
                        "impact_collecte": impact,
                        "zone_id": "CM",
                        "recurrent": True,
                    }
                )
        except ValueError:
            pass

    # Marchés hebdomadaires périodiques (tous les 8 jours dans les zones rurales)
    from datetime import timedelta as td

    marche_debut = debut
    while marche_debut <= fin:
        if marche_debut.weekday() in (3, 5):  # jeudi et samedi
            evenements.append(
                {
                    "date_evenement": marche_debut,
                    "type_evenement": "MARCHE_PERIODIQUE",
                    "nom": f"Marché {marche_debut.strftime('%A')}",
                    "impact_collecte": "POSITIF",
                    "zone_id": "CM",
                    "recurrent": True,
                }
            )
        marche_debut += td(days=1)

    return evenements


# ─── 7. Mapping et propagation ────────────────────────────────────────────────


def mapper_produits_generiques(**ctx) -> int:
    """
    Mappe les libellés CBS/terrain vers les produits génériques de la table app.produits.

    Ex : "MAIS_GRAIN" / "maïs grain" / "maïs" → produit_id=1.
    Retourne le nombre de prix_produits mis à jour avec un produit_id résolu.
    """
    sql = """
        UPDATE app.prix_produits pp
        SET produit_id = p.id
        FROM app.produits p
        WHERE pp.produit_id IS NULL
          AND pp.produit_code IS NOT NULL
          AND LOWER(pp.produit_code) = ANY(p.alias_codes)
        RETURNING pp.id
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("mapper_produits_generiques : %d lignes mappées", n)
    return n


def maj_app_prix_produits(**ctx) -> int:
    """
    Agrège les prix du jour (toutes sources) par produit et zone
    et met à jour app.prix_produits_consolides (vue matérialisée).

    Retourne le nombre de lignes agrégées.
    """
    sql = """
        INSERT INTO app.prix_produits_consolides (
            produit_id, zone_id, date_prix,
            prix_median, prix_min, prix_max, n_observations
        )
        SELECT
            produit_id,
            zone_id,
            date_collecte                           AS date_prix,
            PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY prix_unitaire) AS prix_median,
            MIN(prix_unitaire)                      AS prix_min,
            MAX(prix_unitaire)                      AS prix_max,
            COUNT(*)                                AS n_observations
        FROM app.prix_produits
        WHERE date_collecte = CURRENT_DATE
          AND produit_id IS NOT NULL
        GROUP BY produit_id, zone_id, date_collecte
        ON CONFLICT (produit_id, zone_id, date_prix) DO UPDATE SET
            prix_median    = EXCLUDED.prix_median,
            prix_min       = EXCLUDED.prix_min,
            prix_max       = EXCLUDED.prix_max,
            n_observations = EXCLUDED.n_observations
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("maj_app_prix_produits : %d lignes consolidées", n)
    return n


def maj_app_facteurs_macro(**ctx) -> int:
    """
    Copie les indicateurs macro du jour dans la table consolidée
    app.facteurs_macro_consolides (une ligne par indicateur, valeur la plus récente).

    Retourne le nombre de lignes mises à jour.
    """
    sql = """
        INSERT INTO app.facteurs_macro_consolides (
            indicateur, valeur, unite, source, date_publication, updated_at
        )
        SELECT
            indicateur,
            valeur,
            unite,
            source,
            MAX(date_publication),
            NOW()
        FROM app.facteurs_macro
        WHERE date_publication >= CURRENT_DATE - INTERVAL '7 days'
        GROUP BY indicateur, valeur, unite, source
        ON CONFLICT (indicateur, source) DO UPDATE SET
            valeur           = EXCLUDED.valeur,
            date_publication = EXCLUDED.date_publication,
            updated_at       = EXCLUDED.updated_at
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("maj_app_facteurs_macro : %d indicateurs consolidés", n)
    return n


def maj_app_donnees_meteo(**ctx) -> int:
    """
    No-op de compatibilité, conservé comme tâche Airflow à part entière
    (dag_donnees_externes.py) pour ne pas modifier le graphe de tâches.

    Le calcul d'indice_secheresse est désormais fait directement par
    fetch_meteo_open_meteo()/_maj_indice_secheresse() au moment de
    l'upsert, via une moyenne glissante 30 jours calculée sur
    app.donnees_meteo lui-même — plus besoin d'une table de "normales
    saisonnières" séparée (app.donnees_meteo_normales) qui n'a d'ailleurs
    jamais existé, et l'ancienne implémentation ciblait un format narrow
    (zone_nom/variable/valeur/anomalie_pct) sans rapport avec le schéma
    réellement migré (zone_id/precipitation_mm/indice_secheresse, V21).
    """
    logger.info(
        "maj_app_donnees_meteo : no-op — indice_secheresse déjà calculé par fetch_meteo_open_meteo()."
    )
    return 0
