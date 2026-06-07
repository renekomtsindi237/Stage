"""
test_transformers.py — Tests des transformateurs PAR et collectes.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal

from transformers.collecte_transformer import (
    transform_collectes,
)
from transformers.par_transformer import (
    PAR30_DAYS,
    PAR90_DAYS,
    compute_par_summary,
    transform_prets_to_fact,
)

# ── Fixtures ──────────────────────────────────────────────────────────────────


def _pret(
    id_pret: str = "PRE-001",
    jours_retard: int = 0,
    solde_restant: str = "500000",
    nom_agence: str = "Agence Yaoundé",
) -> dict:
    return {
        "id_pret": id_pret,
        "id_client": "CLI-001",
        "nom_client": "Jean Kamga",
        "nom_agence": nom_agence,
        "nom_agent": "agent01",
        "montant_pret": Decimal("1000000"),
        "date_deblocage": date(2024, 1, 15),
        "date_echeance": date(2025, 1, 15),
        "montant_rembourse": Decimal("500000"),
        "solde_restant": Decimal(solde_restant),
        "statut_pret": "ACTIF",
        "jours_retard": jours_retard,
    }


def _collecte(
    id_: int = 1,
    id_pret: str = "PRE-001",
    montant: str = "50000",
    canal: str = "MTN_MOBILE_MONEY",
    jours_retard_date: int = 0,
) -> dict:
    return {
        "id": id_,
        "id_pret": id_pret,
        "agent_id": 1,
        "nom_agent": "agent01",
        "nom_agence": "Agence Test",
        "montant": Decimal(montant),
        "canal": canal,
        "latitude": 3.8480,
        "longitude": 11.5021,
        "date_collecte": date.today(),
        "statut": "CONFIRMEE",
        "created_at": date.today(),
    }


# ── Tests PAR Transformer ─────────────────────────────────────────────────────


class TestTransformPretsToFact:

    def test_pret_sans_retard_par_zero(self):
        prets = [_pret(jours_retard=0)]
        facts = transform_prets_to_fact(prets, date.today())
        assert len(facts) == 1
        assert facts[0].encours_par30 == Decimal("0")
        assert facts[0].encours_par90 == Decimal("0")

    def test_pret_par30(self):
        prets = [_pret(jours_retard=PAR30_DAYS, solde_restant="300000")]
        facts = transform_prets_to_fact(prets, date.today())
        assert facts[0].encours_par30 == Decimal("300000")
        assert facts[0].encours_par90 == Decimal("0")

    def test_pret_par90(self):
        prets = [_pret(jours_retard=PAR90_DAYS, solde_restant="200000")]
        facts = transform_prets_to_fact(prets, date.today())
        assert facts[0].encours_par30 == Decimal("200000")
        assert facts[0].encours_par90 == Decimal("200000")

    def test_jours_retard_negatif_skipped(self):
        prets = [_pret(jours_retard=-1)]
        facts = transform_prets_to_fact(prets, date.today())
        assert len(facts) == 0

    def test_multiple_prets(self):
        prets = [
            _pret("PRE-001", jours_retard=0),
            _pret("PRE-002", jours_retard=35),
            _pret("PRE-003", jours_retard=95),
        ]
        facts = transform_prets_to_fact(prets, date.today())
        assert len(facts) == 3
        assert facts[0].encours_par30 == Decimal("0")
        assert facts[1].encours_par30 > Decimal("0")
        assert facts[1].encours_par90 == Decimal("0")
        assert facts[2].encours_par90 > Decimal("0")

    def test_date_valeur_par_defaut_est_aujourd_hui(self):
        prets = [_pret()]
        facts = transform_prets_to_fact(prets)
        assert facts[0].date_valeur == date.today()

    def test_liste_vide(self):
        facts = transform_prets_to_fact([])
        assert facts == []


class TestComputePARSummary:

    def test_resume_par_agence(self):
        prets = [
            _pret(
                "PRE-001", jours_retard=0, solde_restant="500000", nom_agence="Douala"
            ),
            _pret(
                "PRE-002", jours_retard=35, solde_restant="200000", nom_agence="Douala"
            ),
            _pret(
                "PRE-003", jours_retard=95, solde_restant="100000", nom_agence="Yaoundé"
            ),
        ]
        facts = transform_prets_to_fact(prets, date.today())
        summaries = compute_par_summary(facts)

        assert "Douala" in summaries
        assert "Yaoundé" in summaries

        douala = summaries["Douala"]
        assert douala.nb_prets == 2
        assert douala.encours_par30 == Decimal("200000")
        assert douala.encours_par90 == Decimal("0")

        yaounde = summaries["Yaoundé"]
        assert yaounde.encours_par30 == Decimal("100000")
        assert yaounde.encours_par90 == Decimal("100000")

    def test_taux_par_calcule(self):
        prets = [
            _pret("PRE-A", jours_retard=0, solde_restant="800000", nom_agence="A"),
            _pret("PRE-B", jours_retard=35, solde_restant="200000", nom_agence="A"),
        ]
        facts = transform_prets_to_fact(prets, date.today())
        summaries = compute_par_summary(facts)
        a = summaries["A"]
        # PAR30 = 200000 / 1000000 = 20%
        assert a.taux_par30 == Decimal("20.00")

    def test_taux_par_zero_quand_encours_nul(self):
        prets = [_pret(solde_restant="0")]
        facts = transform_prets_to_fact(prets, date.today())
        summaries = compute_par_summary(facts)
        agence = list(summaries.values())[0]
        assert agence.taux_par30 == Decimal("0")


# ── Tests Collecte Transformer ────────────────────────────────────────────────


class TestTransformCollectes:

    def test_collecte_valide(self):
        collectes = [_collecte()]
        facts = transform_collectes(collectes)
        assert len(facts) == 1
        f = facts[0]
        assert f.source_id == 1
        assert f.canal == "MTN_MOBILE_MONEY"
        assert f.montant == Decimal("50000")

    def test_canal_invalide_skipped(self):
        collectes = [_collecte(canal="BITCOIN")]
        facts = transform_collectes(collectes)
        assert len(facts) == 0

    def test_montant_zero_skipped(self):
        collectes = [_collecte(montant="0")]
        facts = transform_collectes(collectes)
        assert len(facts) == 0

    def test_montant_negatif_skipped(self):
        collectes = [_collecte(montant="-1000")]
        facts = transform_collectes(collectes)
        assert len(facts) == 0

    def test_canal_especes_valide(self):
        collectes = [_collecte(canal="ESPECES")]
        facts = transform_collectes(collectes)
        assert len(facts) == 1

    def test_agence_map_resoud_id_agence(self):
        collectes = [_collecte()]
        agence_map = {"Agence Test": "AG-001"}
        facts = transform_collectes(collectes, agence_map=agence_map)
        assert facts[0].id_agence == "AG-001"

    def test_sans_agence_map_utilise_nom(self):
        collectes = [_collecte()]
        facts = transform_collectes(collectes, agence_map=None)
        assert facts[0].id_agence == "Agence Test"

    def test_liste_vide(self):
        assert transform_collectes([]) == []

    def test_mixte_valides_invalides(self):
        collectes = [
            _collecte(id_=1, canal="ESPECES", montant="30000"),
            _collecte(id_=2, canal="BITCOIN", montant="30000"),  # invalide
            _collecte(id_=3, canal="ORANGE_MONEY", montant="0"),  # invalide
            _collecte(id_=4, canal="VIREMENT", montant="80000"),
        ]
        facts = transform_collectes(collectes)
        assert len(facts) == 2
        source_ids = {f.source_id for f in facts}
        assert source_ids == {1, 4}
