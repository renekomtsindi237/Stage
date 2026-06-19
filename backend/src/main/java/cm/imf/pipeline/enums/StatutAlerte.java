package cm.imf.pipeline.enums;

public enum StatutAlerte {
    /** Alerte non encore traitée (valeur DB : ACTIVE) */
    ACTIVE,
    CLOTUREE,
    ESCALADEE,
    /** Alias frontend Angular/mobile — mappé sur ACTIVE en DB */
    NON_TRAITEE,
    /** Alias frontend Angular/mobile — mappé sur ESCALADEE en DB */
    EN_TRAITEMENT,
    /** Alias frontend Angular/mobile — mappé sur CLOTUREE en DB */
    RESOLUE
}
