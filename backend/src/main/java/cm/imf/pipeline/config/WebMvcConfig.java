package cm.imf.pipeline.config;

import cm.imf.pipeline.security.LoginRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration MVC globale.
 *
 * Les controllers exposent directement leurs chemins déclarés dans
 * leurs annotations @RequestMapping/@GetMapping/etc.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginRateLimitInterceptor loginRateLimitInterceptor;

    @Value("${app.upload.dir:/uploads}")
    private String uploadDir;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Ajoute /api/v1 uniquement sur nos propres controllers (cm.imf).
        // Exclure les controllers tiers (ex: springdoc OpenApiWebMvcResource) pour éviter
        // que /api-docs se retrouve déplacé à /api/v1/api-docs, cassant le Swagger UI.
        configurer.addPathPrefix("/api/v1",
                c -> c.isAnnotationPresent(RestController.class)
                        && c.getPackageName().startsWith("cm.imf"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginRateLimitInterceptor)
                .addPathPatterns("/auth/login", "/auth/refresh", "/api/v1/auth/login", "/api/v1/auth/refresh");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**", "/api/v1/uploads/**", "/api/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
