package io.livelattice.backgroundjobs.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayMigrationConfig {

    @Bean(name = "backgroundJobsFlyway")
    Flyway backgroundJobsFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .table("background_jobs_flyway_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
        flyway.migrate();
        return flyway;
    }
}
