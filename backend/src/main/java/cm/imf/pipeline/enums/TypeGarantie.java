package cm.imf.pipeline.enums;

/**
 * Types de garanties adossées aux prêts dans les EMF camerounais.
 * La caution solidaire est la plus répandue dans les coopératives (catégorie 1 COBAC).
 */
public enum TypeGarantie {

    /** Groupe d'emprunteurs mutuellement responsables — très pratiqué dans les mutuelles */
    CAUTION_SOLIDAIRE,

    /** Cosignataire individuel (cautionnaire personnel) */
    CAUTIONNAIRE_PERSONNEL,

    /** Gage sur bien meuble : moto, équipement, stock */
    NANTISSEMENT,

    /** Garantie immobilière (terrain, bâtiment) */
    HYPOTHEQUE,

    /** Espèces bloquées sur un compte garantie */
    DEPOT_GARANTIE
}
