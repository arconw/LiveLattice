package io.livelattice.core.service.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class QueryEngineTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ClickHouseDataSourceFactory clickHouseDataSourceFactory;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private Statement statement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private ResultSetMetaData metaData;
    @Mock
    private ValueOperations<String, String> valueOps;

    private QueryEngine queryEngine;

    @BeforeEach
    void setUp() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        queryEngine = new QueryEngine(redisTemplate, clickHouseDataSourceFactory, new ObjectMapper());
    }

    @Test
    void executeWidget_shouldReturnCachedResult() throws Exception {
        Map<String, Object> cached = Map.of(
            "columns", List.of("type"),
            "rows", List.of(Map.of("type", "click")),
            "meta", Map.of("totalRows", 1, "executedAt", "2026-01-01T00:00:00Z")
        );
        when(valueOps.get(anyString())).thenReturn(new ObjectMapper().writeValueAsString(cached));

        CompletableFuture<Map<String, Object>> future = queryEngine.executeWidget(
            "w1", Map.of(), Map.of("type", "CLICKHOUSE"), null, "ws"
        );

        assertEquals(cached, future.join());
        verify(clickHouseDataSourceFactory, never()).create(any());
    }

    @Test
    void executeWidget_shouldReturnResultShape() throws Exception {
        when(clickHouseDataSourceFactory.create(any())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnLabel(1)).thenReturn("type");
        when(metaData.getColumnLabel(2)).thenReturn("count");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn("click");
        when(resultSet.getObject(2)).thenReturn(42);

        Map<String, Object> query = Map.of(
            "metrics", List.of(Map.of("expression", "count(*)", "alias", "count")),
            "dimensions", List.of(Map.of("field", "event_type", "alias", "type")),
            "filters", List.of(),
            "order_by", List.of(),
            "limit", 10
        );
        Map<String, Object> config = Map.of(
            "type", "CLICKHOUSE",
            "host", "localhost",
            "port", 8123,
            "database", "default",
            "table", "canvas_events"
        );

        CompletableFuture<Map<String, Object>> future = queryEngine.executeWidget(
            "w1", query, config, Map.of("type", "relative", "value", "24h"), "ws"
        );

        Map<String, Object> result = future.join();
        assertTrue(result.containsKey("columns"));
        assertTrue(result.containsKey("rows"));
        assertTrue(result.containsKey("meta"));
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        assertEquals(1, meta.get("totalRows"));
        assertFalse(meta.containsKey("warning"));
    }
}
