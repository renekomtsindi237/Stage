package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.TypeDocumentKyc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InitierKycRequest(
        @NotBlank String clientId,
        @NotBlank String nomClient,
        String prenomClient,
        LocalDate dateNaissance,
        String lieuNaissance,
        String nationalite,
        String telephone,
        String email,
        String adresse,
        String ville,
        String profession,
        String employeur,
        BigDecimal revenuMensuelEstim,

        // Pièce d'identité
        TypeDocumentKyc typePieceIdentite,
        String numeroPiece,
        LocalDate dateEmissionPiece,
        LocalDate dateExpirationPiece,
        String lieuEmissionPiece,

        @NotNull NiveauKyc niveauDemande,
        boolean estPep,
        String observations
) {}
