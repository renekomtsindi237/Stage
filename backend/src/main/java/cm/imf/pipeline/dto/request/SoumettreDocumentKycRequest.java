package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.TypeDocumentKyc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SoumettreDocumentKycRequest(
        @NotNull  TypeDocumentKyc typeDocument,
        @NotBlank @Size(max = 255) String nomFichier,
        @NotBlank @Size(max = 20_000_000) String contenuBase64,
        String mimeType,
        Long tailleOctets,
        LocalDate dateExpirationDoc
) {}
