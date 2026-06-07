package cm.imf.pipeline.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("IMF Pipeline API")
                        .description("""
                                Backend API — Plateforme de suivi des collectes digitales
                                et recouvrement de créances pour les IMF du Cameroun.

                                **Versioning** : tous les endpoints sont préfixés `/api/v1/`.
                                Les tokens JWT sont transmis via cookie httpOnly (header `Authorization` aussi accepté).

                                **Conformité** : Loi n° 2024/017 Cameroun (RGPD), Règlement COBAC 01/02 CEMAC.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Institut Universitaire Saint Jean")
                                .email("dsi@imf.cm"))
                        .license(new License()
                                .name("Propriétaire — usage interne IMF")))
                .servers(List.of(
                        new Server().url("/").description("Serveur courant"),
                        new Server().url("http://localhost:8080").description("Développement local")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT — obtenu via POST /api/v1/auth/login")));
    }
}
