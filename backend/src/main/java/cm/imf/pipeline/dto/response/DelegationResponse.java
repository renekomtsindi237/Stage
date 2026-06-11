package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.Delegation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DelegationResponse(
        UUID uid,
        String typeDelegation,
        Long delegantId,
        Long delegataireId,
        Long objetId,
        String objetType,
        String motif,
        String roleDelegue,
        BigDecimal montantSeuil,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean actif,
        OffsetDateTime createdAt
) {
    public static DelegationResponse from(Delegation d) {
        return new DelegationResponse(
                d.getUid(),
                d.getTypeDelegation(),
                d.getDelegantId(),
                d.getDelegataireId(),
                d.getObjetId(),
                d.getObjetType(),
                d.getMotif(),
                d.getRoleDelegue(),
                d.getMontantSeuil(),
                d.getDateDebut(),
                d.getDateFin(),
                d.isActif(),
                d.getCreatedAt()
        );
    }
}
