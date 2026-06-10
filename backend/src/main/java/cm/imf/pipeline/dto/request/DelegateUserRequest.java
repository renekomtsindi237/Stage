package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload pour la délégation (relégation) d'un utilisateur vers un autre de la même IMF.
 * L'utilisateur source est suspendu ; le destinataire hérite de son rôle.
 */
public record DelegateUserRequest(
        @NotNull UUID toUserUid
) {}
