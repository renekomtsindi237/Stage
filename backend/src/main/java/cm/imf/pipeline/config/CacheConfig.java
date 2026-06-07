package cm.imf.pipeline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${imf.cache.kpi-ttl-seconds:3600}")
    private long kpiTtlSeconds;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(kpiTtlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "kpi-par",        defaultConfig.entryTtl(Duration.ofHours(1)),
                        "kpi-collectes",  defaultConfig.entryTtl(Duration.ofHours(1)),
                        "kpi-dashboard",  defaultConfig.entryTtl(Duration.ofMinutes(30)),
                        "agents-agence",  defaultConfig.entryTtl(Duration.ofHours(6)),
                        "agents-list",    defaultConfig.entryTtl(Duration.ofHours(6)),
                        "agents-search",  defaultConfig.entryTtl(Duration.ofHours(6))
                ))
                .build();
    }
}
