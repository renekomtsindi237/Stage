package cm.imf.pipeline.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SyncCollectesRequest(

    @NotEmpty(message = "La liste des collectes ne peut pas être vide")
    @Valid
    List<CollecteEpargneRequest> collectes
) {}
