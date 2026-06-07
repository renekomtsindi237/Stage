package cm.imf.pipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Executor dédié pour les requêtes HTTP asynchrones (SSE, Callable, DeferredResult).
 *
 * Sans cette configuration, Spring MVC utilise SimpleAsyncTaskExecutor qui crée
 * un nouveau thread pour chaque connexion SSE — conduisant à un épuisement de
 * threads sous charge. Ce pool borné (20-50 threads, queue 200) garantit que
 * les connexions SSE n'impactent pas le pool Tomcat principal.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    @Bean(name = "asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("imf-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(asyncExecutor());
        // Timeout aligné sur le timeout SSE (5 min) + marge
        configurer.setDefaultTimeout(330_000L);
    }
}
