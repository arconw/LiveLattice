package io.livelattice.core.service.query;

import com.clickhouse.jdbc.ClickHouseDataSource;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class ClickHouseDataSourceFactory {

    public DataSource create(Map<String, Object> config) throws Exception {
        String host = Objects.toString(config.getOrDefault("host", "localhost"));
        int port = Integer.parseInt(Objects.toString(config.getOrDefault("port", "8123")));
        String database = Objects.toString(config.getOrDefault("database", "default"));
        String url = String.format("jdbc:clickhouse://%s:%d/%s", host, port, database);
        Properties properties = new Properties();
        if (config.containsKey("user")) {
            properties.setProperty("user", Objects.toString(config.get("user")));
        }
        if (config.containsKey("password")) {
            properties.setProperty("password", Objects.toString(config.get("password")));
        }
        return new ClickHouseDataSource(url, properties);
    }
}
