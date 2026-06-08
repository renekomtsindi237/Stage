package cm.imf.pipeline.config;

import cm.imf.pipeline.security.LoginRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginRateLimitInterceptor)
                .addPathPatterns("/auth/login", "/auth/refresh", "/api/v1/auth/login", "/api/v1/auth/refresh");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**", "/api/v1/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
