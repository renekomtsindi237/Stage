"""Types partagés du module d'extraction de documents d'identité."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class TypePiece(str, Enum):
    """Types de pièces d'identité pris en charge par le pipeline d'extraction."""

    CNI_RECTO = "CNI_RECTO"
    CNI_VERSO = "CNI_VERSO"
    PASSEPORT = "PASSEPORT"
    PERMIS_CONDUIRE = "PERMIS_CONDUIRE"
    CARTE_SEJOUR = "CARTE_SEJOUR"


class NiveauKyc(str, Enum):
    """Niveaux KYC COBAC R-2005/01 — cf. docs/uml/24_etats_kyc_dossier.puml."""

    NIVEAU_1 = "NIVEAU_1"
    NIVEAU_2 = "NIVEAU_2"
    NIVEAU_3 = "NIVEAU_3"


@dataclass
class ChampExtrait:
    """Une valeur extraite, avec sa provenance et un score de confiance."""

    valeur: str | None
    confiance: (
        float  # 0.0 (aucune confiance) à 1.0 (certain, ex: check digit MRZ valide)
    )
    source: str  # "mrz" | "ocr_regex" | "ocr_layout"

    def to_dict(self) -> dict:
        return {
            "valeur": self.valeur,
            "confiance": round(self.confiance, 3),
            "source": self.source,
        }


@dataclass
class ResultatExtraction:
    """Résultat complet de l'extraction d'un document."""

    type_piece: TypePiece
    champs: dict[str, ChampExtrait] = field(default_factory=dict)
    texte_brut: str = ""
    mrz_valide: bool | None = (
        None  # None = pas de zone MRZ sur ce type/face de document
    )
    erreurs: list[str] = field(default_factory=list)

    @property
    def confiance_globale(self) -> float:
        if not self.champs:
            return 0.0
        return sum(c.confiance for c in self.champs.values()) / len(self.champs)

    def valeur(self, champ: str) -> str | None:
        c = self.champs.get(champ)
        return c.valeur if c else None

    def to_dict(self) -> dict:
        return {
            "typePiece": self.type_piece.value,
            "champs": {k: v.to_dict() for k, v in self.champs.items()},
            "texteBrut": self.texte_brut,
            "mrzValide": self.mrz_valide,
            "erreurs": self.erreurs,
            "confianceGlobale": round(self.confiance_globale, 3),
        }
