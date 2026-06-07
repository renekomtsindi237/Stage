package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.StatutAlerte;
import jakarta.validation.constraints.NotNull;

public record AlerteUpdateRequest(@NotNull StatutAlerte statut) {}
