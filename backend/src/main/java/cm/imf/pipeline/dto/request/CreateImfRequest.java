package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.NiveauKyc;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateImfRequest(

        // ── Étape 1 — Identité & constitution ────────────────────────────────

        @NotBlank
        @Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "Code : lettres majuscules/chiffres, 2-20 caractères")
        String code,

        @NotBlank @Size(min = 3, max = 100)
        String nom,

        @NotBlank @Size(min = 3, max = 200, message = "Dénomination sociale requise (min. 3 car.)")
        String denominationSociale,

        @NotBlank @Size(max = 50, message = "Forme juridique requise")
        String formeJuridique,

        @Size(max = 50)
        String pays,

        @NotBlank @Size(min = 5, max = 500, message = "Adresse du siège requise (min. 5 car.)")
        String adresseSiege,

        @Size(max = 100)
        String numAgrement,

        @Size(max = 20)
        String telephone,

        @Email(message = "Adresse email invalide")
        @Size(max = 100)
        String email,

        // ── Étape 2 — Capital & segmentation ─────────────────────────────────

        @NotNull(message = "Le capital social est requis")
        @DecimalMin(value = "0.01", message = "Le capital social doit être positif")
        BigDecimal capitalSocial,

        @Size(max = 200)
        String segmentsClients,

        @Size(max = 200)
        String typesGaranties,

        // ── Étape 3 — Paramètres métier ───────────────────────────────────────

        @NotNull(message = "Le taux d'intérêt annuel est requis")
        @DecimalMin(value = "0.0", message = "Le taux doit être positif ou nul")
        @DecimalMax(value = "100.0", message = "Le taux ne peut dépasser 100 %")
        BigDecimal tauxInteretAnnuel,

        @NotNull(message = "La durée maximale de crédit est requise")
        @Min(value = 1, message = "Durée minimale : 1 mois")
        @Max(value = 360, message = "Durée maximale : 360 mois (30 ans)")
        Integer dureeMaxCreditMois,

        @NotNull(message = "Le taux de pénalité est requis")
        @DecimalMin(value = "0.0", message = "Le taux de pénalité doit être positif ou nul")
        BigDecimal tauxPenaliteRetard,

        @NotNull(message = "Le seuil de relance est requis")
        @Min(value = 1, message = "Le seuil de relance doit être d'au moins 1 jour")
        Integer seuilRelanceJours,

        // Épargne (optionnel)
        @DecimalMin(value = "0.0")
        BigDecimal tauxEpargne,

        @DecimalMin(value = "0.0")
        BigDecimal soldeMinEpargne,

        @DecimalMin(value = "0.0")
        BigDecimal fraisTenueCompte,

        // ── Étape 4 — Paramètres opérationnels (optionnels — valeurs par défaut si null) ──

        /** Taille max d'un document KYC en octets. null → 5 Mo par défaut. */
        @Min(value = 1_048_576, message = "Taille minimale : 1 Mo")
        @Max(value = 20_971_520, message = "Taille maximale : 20 Mo")
        Long maxDocumentKycOctets,

        /** Niveau KYC minimal obligatoire pour accorder un crédit. null → NIVEAU_1. */
        NiveauKyc niveauKycMinimal,

        /** Max tentatives de connexion par IP/minute. null → 5 par défaut. */
        @Min(value = 1, message = "Au moins 1 tentative autorisée")
        @Max(value = 20, message = "Maximum 20 tentatives")
        Integer maxTentativesConnexion

) {}
