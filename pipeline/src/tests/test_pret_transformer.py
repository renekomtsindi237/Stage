"""
test_pret_transformer.py — Tests unitaires du transformateur COBAC PAR (pret_transformer).

Couvre :
  - calculate_jours_retard  : premier impayé, plusieurs échéances, cas limites
  - classify_par            : seuils NORMAL / PAR30 / PAR90 / PAR180
  - calculate_taux_recouvrement : cas normal, montant nul, dépassement 100%
  - pret_requires_alerte    : déclenchement à partir de PAR30
  - transform_pret          : pipeline complet depuis un dict brut
  - transform_batch         : tri et gestion des erreurs
"""

from __future__ import annotations

from datetime import date, timedelta
from decimal import Decimal

import pytest

from transformers.pret_transformer import (
    EcheanceInfo,
    PretTransformed,
    calculate_jours_retard,
    calculate_taux_recouvrement,
    classify_par,
    pret_requires_alerte,
    transform_batch,
    transform_pret,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _echeance(
    id_: int = 1,
    numero: int = 1,
    date_echeance: date | None = None,
    montant_du: str = "50000",
    montant_paye: str = "0",
    statut: str = "EN_RETARD",
    id_pret: str = "PRE-001",
) -> EcheanceInfo:
    return EcheanceInfo(
        id=id_,
        id_pret=id_pret,
        numero=numero,
        date_echeance=date_echeance or date.today() - timedelta(days=10),
        montant_du=Decimal(montant_du),
        montant_paye=Decimal(montant_paye),
        statut=statut,
    )


def _raw_pret(
    id_pret: str = "PRE-001",
    montant_initial: str = "1000000",
    montant_rembourse: str = "300000",
    statut: str = "ACTIF",
) -> dict:
    return {
        "id_pret": id_pret,
        "reference": f"REF-{id_pret}",
        "id_client": 1,
        "montant_initial": Decimal(montant_initial),
        "montant_rembourse": Decimal(montant_rembourse),
        "taux_interet": Decimal("12.5"),
        "statut": statut,
        "date_debut": "2024-01-01",
        "date_fin": "2025-01-01",
        "nombre_echeances": 12,
        "echeances_payees": 3,
    }


# ---------------------------------------------------------------------------
# calculate_jours_retard
# ---------------------------------------------------------------------------

class TestCalculateJoursRetard:

    def test_aucune_echeance(self):
        assert calculate_jours_retard([]) == 0

    def test_echeance_entierement_payee(self):
        e = _echeance(montant_du="50000", montant_paye="50000", statut="EN_RETARD")
        assert calculate_jours_retard([e]) == 0

    def test_echeance_en_attente_future(self):
        """Échéance future non encore due : ne compte pas."""
        e = _echeance(
            date_echeance=date.today() + timedelta(days=5),
            statut="EN_ATTENTE",
        )
        assert calculate_jours_retard([e]) == 0

    def test_une_echeance_impayee(self):
        ref = date.today()
        e = _echeance(
            date_echeance=ref - timedelta(days=45),
            montant_du="50000",
            montant_paye="0",
            statut="EN_RETARD",
        )
        jours = calculate_jours_retard([e], date_calcul=ref)
        assert jours == 45

    def test_premiere_echeance_impayee_retenue(self):
        """Deux échéances impayées : la date la plus ancienne est retenue."""
        ref = date.today()
        e1 = _echeance(id_=1, numero=1,
                       date_echeance=ref - timedelta(days=60),
                       montant_du="50000", montant_paye="0", statut="EN_RETARD")
        e2 = _echeance(id_=2, numero=2,
                       date_echeance=ref - timedelta(days=30),
                       montant_du="50000", montant_paye="0", statut="EN_RETARD")
        jours = calculate_jours_retard([e1, e2], date_calcul=ref)
        assert jours == 60

    def test_echeance_partiellement_payee_compte(self):
        ref = date.today()
        e = _echeance(
            date_echeance=ref - timedelta(days=20),
            montant_du="50000",
            montant_paye="10000",   # partiel → en retard
            statut="EN_ATTENTE",
        )
        jours = calculate_jours_retard([e], date_calcul=ref)
        assert jours == 20

    def test_statut_non_concerne_ignore(self):
        """Statuts PAYEE et ANNULEE ne doivent pas être comptés."""
        ref = date.today()
        for statut in ("PAYEE", "ANNULEE"):
            e = _echeance(
                date_echeance=ref - timedelta(days=15),
                montant_du="50000",
                montant_paye="0",
                statut=statut,
            )
            assert calculate_jours_retard([e], date_calcul=ref) == 0

    def test_date_calcul_explicit(self):
        ref = date(2025, 6, 1)
        e = _echeance(
            date_echeance=date(2025, 4, 1),   # 61 jours avant ref
            montant_du="50000",
            montant_paye="0",
            statut="EN_RETARD",
        )
        jours = calculate_jours_retard([e], date_calcul=ref)
        assert jours == 61


# ---------------------------------------------------------------------------
# classify_par
# ---------------------------------------------------------------------------

class TestClassifyPar:

    @pytest.mark.parametrize("jours,expected", [
        (0,   "NORMAL"),
        (-5,  "NORMAL"),
        (1,   "PAR30"),
        (30,  "PAR30"),
        (31,  "PAR90"),
        (90,  "PAR90"),
        (91,  "PAR180"),
        (365, "PAR180"),
    ])
    def test_seuils(self, jours: int, expected: str):
        assert classify_par(jours) == expected


# ---------------------------------------------------------------------------
# calculate_taux_recouvrement
# ---------------------------------------------------------------------------

class TestCalculateTauxRecouvrement:

    def test_recouvrement_partiel(self):
        taux = calculate_taux_recouvrement(Decimal("1000000"), Decimal("300000"))
        assert taux == Decimal("30.00")

    def test_recouvrement_complet(self):
        taux = calculate_taux_recouvrement(Decimal("500000"), Decimal("500000"))
        assert taux == Decimal("100.00")

    def test_recouvrement_depasse_100_capped(self):
        """Sur-remboursement (arrondi) ne peut pas dépasser 100%."""
        taux = calculate_taux_recouvrement(Decimal("100"), Decimal("200"))
        assert taux == Decimal("100.00")

    def test_montant_initial_nul(self):
        taux = calculate_taux_recouvrement(Decimal("0"), Decimal("50000"))
        assert taux == Decimal("0.00")

    def test_montant_rembourse_nul(self):
        taux = calculate_taux_recouvrement(Decimal("500000"), Decimal("0"))
        assert taux == Decimal("0.00")

    def test_arrondi_half_up(self):
        # 1/3 ≈ 33.333... → 33.33
        taux = calculate_taux_recouvrement(Decimal("300"), Decimal("100"))
        assert taux == Decimal("33.33")


# ---------------------------------------------------------------------------
# pret_requires_alerte
# ---------------------------------------------------------------------------

class TestPretRequiresAlerte:

    def test_normal_pas_alerte(self):
        assert pret_requires_alerte(0, "NORMAL") is False

    def test_par30_alerte(self):
        assert pret_requires_alerte(15, "PAR30") is True

    def test_par90_alerte(self):
        assert pret_requires_alerte(60, "PAR90") is True

    def test_par180_alerte(self):
        assert pret_requires_alerte(120, "PAR180") is True

    def test_jours_positif_statut_normal_pas_alerte(self):
        # Cas incohérent — la logique se base sur jours > 0 ET statut != NORMAL
        assert pret_requires_alerte(1, "NORMAL") is False


# ---------------------------------------------------------------------------
# transform_pret
# ---------------------------------------------------------------------------

class TestTransformPret:

    def test_pret_sans_retard(self):
        raw = _raw_pret()
        result = transform_pret(raw, [], date_calcul=date.today())
        assert isinstance(result, PretTransformed)
        assert result.jours_retard == 0
        assert result.statut_par == "NORMAL"
        assert result.alerte_requise is False

    def test_pret_par30(self):
        ref = date.today()
        e = _echeance(
            date_echeance=ref - timedelta(days=20),
            statut="EN_RETARD",
        )
        raw = _raw_pret(montant_initial="1000000", montant_rembourse="200000")
        result = transform_pret(raw, [e], date_calcul=ref)
        assert result.statut_par == "PAR30"
        assert result.alerte_requise is True

    def test_montant_restant_ne_devient_pas_negatif(self):
        raw = _raw_pret(montant_initial="100000", montant_rembourse="200000")
        result = transform_pret(raw, [], date_calcul=date.today())
        assert result.montant_restant == Decimal("0")

    def test_taux_recouvrement_calcule(self):
        raw = _raw_pret(montant_initial="1000000", montant_rembourse="500000")
        result = transform_pret(raw, [], date_calcul=date.today())
        assert result.taux_recouvrement == Decimal("50.00")

    def test_champs_de_base_renseignes(self):
        raw = _raw_pret(id_pret="PRE-999")
        result = transform_pret(raw, [], date_calcul=date.today())
        assert result.id_pret == "PRE-999"
        assert result.reference == "REF-PRE-999"
        assert result.id_client == 1

    def test_date_debut_parsee(self):
        raw = _raw_pret()
        result = transform_pret(raw, [], date_calcul=date.today())
        assert result.date_debut == date(2024, 1, 1)
        assert result.date_fin == date(2025, 1, 1)

    def test_date_invalide_donne_none(self):
        raw = _raw_pret()
        raw["date_debut"] = "not-a-date"
        raw["date_fin"] = None
        result = transform_pret(raw, [], date_calcul=date.today())
        assert result.date_debut is None
        assert result.date_fin is None


# ---------------------------------------------------------------------------
# transform_batch
# ---------------------------------------------------------------------------

class TestTransformBatch:

    def test_batch_vide(self):
        assert transform_batch([], {}) == []

    def test_batch_trie_par_retard_decroissant(self):
        ref = date.today()
        prets = [
            _raw_pret(id_pret="PRE-A"),
            _raw_pret(id_pret="PRE-B"),
            _raw_pret(id_pret="PRE-C"),
        ]
        echeances = {
            "PRE-A": [_echeance(date_echeance=ref - timedelta(days=5),  statut="EN_RETARD", id_pret="PRE-A")],
            "PRE-B": [_echeance(date_echeance=ref - timedelta(days=100), statut="EN_RETARD", id_pret="PRE-B")],
            "PRE-C": [],
        }
        results = transform_batch(prets, echeances, date_calcul=ref)
        assert results[0].id_pret == "PRE-B"   # 100 jours
        assert results[1].id_pret == "PRE-A"   # 5 jours
        assert results[2].id_pret == "PRE-C"   # 0 jours

    def test_batch_gere_erreur_sans_arreter(self):
        """Un prêt malformé ne doit pas interrompre les autres."""
        prets = [
            _raw_pret(id_pret="PRE-OK"),
            {"id_pret": "PRE-BAD", "montant_initial": "not-a-number"},  # lèvera une exception
        ]
        results = transform_batch(prets, {}, date_calcul=date.today())
        ids = [r.id_pret for r in results]
        assert "PRE-OK" in ids
        assert "PRE-BAD" not in ids

    def test_batch_sans_echeances_pour_un_pret(self):
        """Clé manquante dans echeances_par_pret → liste vide par défaut."""
        prets = [_raw_pret(id_pret="PRE-SOLO")]
        results = transform_batch(prets, {}, date_calcul=date.today())
        assert len(results) == 1
        assert results[0].jours_retard == 0
