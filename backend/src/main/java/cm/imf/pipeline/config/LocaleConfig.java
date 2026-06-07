package cm.imf.pipeline.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;
import java.util.Locale;

/**
 * Configuration i18n du backend.
 *
 * Le but ici est de permettre à l'API de renvoyer ses messages d'erreur
 * dans la langue de l'utilisateur. La locale est lue depuis le header
 * HTTP Accept-Language envoyé automatiquement par le client Angular.
 *
 * Si le header est absent ou contient une langue non supportée, on
 * tombe sur le français par défaut.
 *
 * Langues supportées : fr (défaut), en
 *
 * Pour les tests via Swagger ou Postman, on peut aussi passer ?lang=en
 * dans l'URL pour forcer l'anglais sans modifier les headers.
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.FRENCH,
            Locale.ENGLISH
    );

    /**
     * Résolveur de locale basé sur le header Accept-Language.
     * J'utilise AcceptHeaderLocaleResolver plutôt que SessionLocaleResolver
     * parce que l'API est stateless (pas de session HTTP).
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.FRENCH);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    /**
     * Source des messages.
     * Spring va chercher messages.properties (français) et messages_en.properties
     * dans src/main/resources. Le rechargement toutes les 5 min évite de
     * redémarrer le serveur quand on modifie une traduction en dev.
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.FRENCH);
        source.setCacheSeconds(300);
        return source;
    }

    /**
     * Intercepteur qui permet de changer la langue via ?lang=en.
     * Pratique pour tester rapidement les messages en anglais depuis Swagger
     * sans avoir à modifier les headers.
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
