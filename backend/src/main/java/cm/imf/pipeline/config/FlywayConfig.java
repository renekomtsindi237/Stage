package cm.imf.pipeline.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@Slf4j
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            boolean hasFailed = Arrays.stream(flyway.info().all())
                    .anyMatch(m -> m.getState() == MigrationState.FAILED);
            if (hasFailed) {
                log.warn("Flyway: migrations échouées détectées — repair() avant migrate()");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
