package io.livelattice.notifications.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayMigrationConfig {

    @Bean(name = "notificationsFlyway")
    Flyway notificationsFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .table("notifications_flyway_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
        flyway.migrate();
        return flyway;
    }
}
