package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequest(@NotBlank String token) {}
