package cm.imf.pipeline.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Représente une ligne du fichier CSV d'import de clients.
 * Champs KPI historiques (nb_collectes_12m, etc.) alimentent staging.stg_clients
 * pour que le moteur ML/KPI dispose des données N-1.
 */
public record ClientImportRow(
        String     clientIdExterne,
        String     nomComplet,
        String     telephonePrincipal,
        String     telephoneSecondaire,
        String     sexe,
        LocalDate  dateNaissance,
        String     secteurPrincipal,
        String     sousSecteur,
        BigDecimal revenuMensuelEstime,
        Short      anneesExperience,
        String     niveauEducation,
        String     situationFamiliale,
        Short      nombrePersonnesCharge,
        String     zoneId,
        String     agenceCode,
        String     adresseActivite,
        String     marchePrincipal,
        String     frequenceMarche,
        BigDecimal latitudeActivite,
        BigDecimal longitudeActivite,
        String     agentEmail,
        // ── KPI historique N-1 ─────────────────────────────────────────────────
        Integer    nbCollectes12m,
        BigDecimal montantTotalCollectes12m,
        Double     regulariteCollectePct,
        Double     tauxRemboursementPct,
        Double     joursRetardMoyen,
        Double     joursRetardMax,
        Integer    nbIncidentsPaiement,
        BigDecimal montantImPayeCourant,
        LocalDate  datePremierPret,
        BigDecimal montantPretActif,
        BigDecimal encoursRestant,
        Integer    nbPretsTotal
) {}
