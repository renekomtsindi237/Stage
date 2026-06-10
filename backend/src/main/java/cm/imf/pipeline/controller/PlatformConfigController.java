package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.PlatformConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/platform/config")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme", description = "Configuration système — SUPER_ADMIN uniquement")
public class PlatformConfigController {

    @Value("${jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:noreply@imf.cm}")
    private String smtpUser;

    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int dbPoolSize;

    private final Environment environment;

    public PlatformConfigController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    @Operation(summary = "Lire la configuration courante de la plateforme (lecture seule)")
    public ResponseEntity<ApiResponse<PlatformConfigResponse>> getConfig() {
        String profiles = Arrays.stream(environment.getActiveProfiles())
                .collect(Collectors.joining(", "));
        if (profiles.isBlank()) profiles = "default";

        return ResponseEntity.ok(ApiResponse.ok(new PlatformConfigResponse(
                accessTokenExpiryMs / 1000 / 60,
                refreshTokenExpiryMs / 1000 / 3600 / 24,
                cookieSecure,
                smtpHost,
                smtpPort,
                smtpUser,
                firebaseEnabled,
                dbPoolSize,
                profiles
        )));
    }
}
