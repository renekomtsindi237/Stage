package cm.imf.pipeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CanalPaiement {
    MTN,
    ORANGE,
    ESPECES,
    WAVE,
    VIREMENT;

    @JsonCreator
    public static CanalPaiement fromJson(String raw) {
        return RecouvrementEnumCodes.canal(raw);
    }
}
