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
 * Versioning : tous les @RestController du package controller
 * reçoivent automatiquement le préfixe /api/v1 — sauf InternalController
 * (pipeline interne, non versionnée publiquement).
 *
 * Les controllers exposent leurs chemins sans le préfixe /api/,
 * ex. @RequestMapping("/clients") → effectif : /api/v1/clients
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginRateLimitInterceptor loginRateLimitInterceptor;

    @Value("${app.upload.dir:/uploads}")
    private String uploadDir;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v1",
            c -> c.isAnnotationPresent(RestController.class)
                && c.getPackageName().equals("cm.imf.pipeline.controller")
                && !c.getSimpleName().equals("InternalController"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/login", "/api/v1/auth/refresh");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/v1/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
