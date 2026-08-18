"""
Exigences d'extraction par niveau KYC (COBAC R-2005/01).

Formalise, côté extraction, ce qui est attendu à chaque niveau — sert à la
fois à piloter la validation automatique et à afficher côté UI les éléments
encore manquants pour compléter un niveau. Les niveaux eux-mêmes (workflow,
statuts EN_ATTENTE/APPROUVE/...) restent gérés côté backend Java
(KycDossier/KycServiceImpl) — ce module ne fait qu'exposer les règles
d'extraction associées.

Cf. docs/uml/24_etats_kyc_dossier.puml pour le workflow complet et
cahier_des_charges/04_exigences_fonctionnelles.md pour la définition métier
des 3 niveaux.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from .schema import NiveauKyc, ResultatExtraction, TypePiece

# Un "jeu" de pièces d'identité acceptées pour prouver l'identité à un niveau
# donné — CNI (recto+verso) OU passeport seul (MRZ) OU carte de séjour seule.
PIECES_IDENTITE_ACCEPTEES: list[list[TypePiece]] = [
    [TypePiece.CNI_RECTO, TypePiece.CNI_VERSO],
    [TypePiece.PASSEPORT],
    [TypePiece.CARTE_SEJOUR],
]


@dataclass
class ExigenceNiveauKyc:
    niveau: NiveauKyc
    champs_identite_requis: list[str]
    exige_mrz_valide: bool
    documents_complementaires_requis: list[str] = field(default_factory=list)
    description: str = ""


EXIGENCES_KYC: dict[NiveauKyc, ExigenceNiveauKyc] = {
    NiveauKyc.NIVEAU_1: ExigenceNiveauKyc(
        niveau=NiveauKyc.NIVEAU_1,
        champs_identite_requis=["nom", "prenom", "dateNaissance", "numeroPiece"],
        exige_mrz_valide=False,
        documents_complementaires_requis=[],
        description="Identité de base — < 150 000 FCFA/mois (Règlement COBAC R-2005/01)",
    ),
    NiveauKyc.NIVEAU_2: ExigenceNiveauKyc(
        niveau=NiveauKyc.NIVEAU_2,
        champs_identite_requis=[
            "nom",
            "prenom",
            "dateNaissance",
            "numeroPiece",
            "dateExpirationPiece",
        ],
        exige_mrz_valide=False,
        documents_complementaires_requis=[
            "JUSTIFICATIF_DOMICILE",
            "DECLARATION_ACTIVITE",
        ],
        description="Identité renforcée + domicile + activité — usage standard",
    ),
    NiveauKyc.NIVEAU_3: ExigenceNiveauKyc(
        niveau=NiveauKyc.NIVEAU_3,
        champs_identite_requis=[
            "nom",
            "prenom",
            "dateNaissance",
            "numeroPiece",
            "dateExpirationPiece",
            "lieuNaissance",
        ],
        exige_mrz_valide=True,
        documents_complementaires_requis=[
            "JUSTIFICATIF_DOMICILE",
            "DECLARATION_SOURCE_FONDS",
        ],
        description="Diligence renforcée PPE/LBC/FT — risque élevé",
    ),
}


def exigences_pour_niveau(niveau: NiveauKyc) -> ExigenceNiveauKyc:
    return EXIGENCES_KYC[niveau]


@dataclass
class RapportCompletude:
    niveau: NiveauKyc
    complet: bool
    champs_manquants: list[str]
    champs_faible_confiance: list[str]  # présents mais confiance < seuil
    mrz_requise_mais_absente_ou_invalide: bool
    documents_complementaires_manquants: list[str]

    def to_dict(self) -> dict:
        return {
            "niveau": self.niveau.value,
            "complet": self.complet,
            "champsManquants": self.champs_manquants,
            "champsFaibleConfiance": self.champs_faible_confiance,
            "mrzRequiseMaisAbsenteOuInvalide": self.mrz_requise_mais_absente_ou_invalide,
            "documentsComplementairesManquants": self.documents_complementaires_manquants,
        }


def evaluer_completude(
    resultats: list[ResultatExtraction],
    niveau: NiveauKyc,
    documents_complementaires_fournis: list[str] | None = None,
    seuil_confiance: float = 0.6,
) -> RapportCompletude:
    """
    Fusionne les champs extraits de tous les documents d'identité d'un dossier
    et évalue ce qu'il manque encore pour satisfaire le niveau KYC demandé.
    """
    exigence = exigences_pour_niveau(niveau)
    documents_complementaires_fournis = documents_complementaires_fournis or []

    champs_fusionnes: dict[str, float] = {}
    mrz_valide_qqpart = False
    mrz_presente_qqpart = False
    for r in resultats:
        if r.mrz_valide is True:
            mrz_valide_qqpart = True
        if r.mrz_valide is not None:
            mrz_presente_qqpart = True
        for nom_champ, champ in r.champs.items():
            if champ.valeur and champ.confiance > champs_fusionnes.get(nom_champ, -1):
                champs_fusionnes[nom_champ] = champ.confiance

    manquants = [
        c for c in exigence.champs_identite_requis if c not in champs_fusionnes
    ]
    faible_confiance = [
        c
        for c in exigence.champs_identite_requis
        if c in champs_fusionnes and champs_fusionnes[c] < seuil_confiance
    ]

    mrz_ko = exigence.exige_mrz_valide and (
        not mrz_presente_qqpart or not mrz_valide_qqpart
    )

    docs_manquants = [
        d
        for d in exigence.documents_complementaires_requis
        if d not in documents_complementaires_fournis
    ]

    complet = (
        not manquants and not faible_confiance and not mrz_ko and not docs_manquants
    )

    return RapportCompletude(
        niveau=niveau,
        complet=complet,
        champs_manquants=manquants,
        champs_faible_confiance=faible_confiance,
        mrz_requise_mais_absente_ou_invalide=mrz_ko,
        documents_complementaires_manquants=docs_manquants,
    )
