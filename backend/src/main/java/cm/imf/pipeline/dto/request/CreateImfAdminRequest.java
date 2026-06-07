package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête pour créer le compte DSI initial d'une IMF.
 * Utilisé par le SUPER_ADMIN via POST /api/platform/imf/{imfId}/admin.
 */
public record CreateImfAdminRequest(
        @NotBlank @Size(min = 3, max = 50)
        String username,

        @NotBlank @Size(min = 8, max = 100)
        String password
) {}
