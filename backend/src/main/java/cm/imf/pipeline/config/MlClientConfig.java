package cm.imf.pipeline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class MlClientConfig {

    @Value("${imf.pipeline.ml-api-url:http://ml-api:8090}")
    private String mlApiUrl;

    @Value("${imf.pipeline.ml-api-timeout-seconds:5}")
    private int timeoutSeconds;

    /** Partagée avec pipeline/src/ml/api_service.py — vide = service ml-api en mode dégradé (ouvert). */
    @Value("${imf.pipeline.ml-internal-api-key:}")
    private String mlInternalApiKey;

    @Bean("mlRestClient")
    public RestClient mlRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds((long) timeoutSeconds * 2));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(mlApiUrl)
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json");
        if (mlInternalApiKey != null && !mlInternalApiKey.isBlank()) {
            builder.defaultHeader("X-Internal-Key", mlInternalApiKey);
        }
        return builder.build();
    }
}
