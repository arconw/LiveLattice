package io.livelattice.core.service.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueryEngine {

    private static final int MAX_ROWS = 10000;
    private static final int DEFAULT_TTL_SECONDS = 30;
    private static final int EMPTY_TTL_SECONDS = 5;
    private static final int MAX_CONCURRENT = 10;

    private final StringRedisTemplate redisTemplate;
    private final ClickHouseDataSourceFactory clickHouseDataSourceFactory;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    @Autowired
    public QueryEngine(StringRedisTemplate redisTemplate,
                       ClickHouseDataSourceFactory clickHouseDataSourceFactory) {
        this(redisTemplate, clickHouseDataSourceFactory, new ObjectMapper());
    }

    QueryEngine(StringRedisTemplate redisTemplate,
                ClickHouseDataSourceFactory clickHouseDataSourceFactory,
                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.clickHouseDataSourceFactory = clickHouseDataSourceFactory;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
    }

    public CompletableFuture<Map<String, Object>> executeWidget(String widgetId,
                                                              Map<String, Object> query,
                                                              Map<String, Object> dataSourceConfig,
                                                              Map<String, Object> timeRange,
                                                              String workspaceId) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = cacheKey(widgetId, timeRange);
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return parseJson(cached);
            }

            Map<String, Object> result;
            try {
                String type = Objects.toString(dataSourceConfig.get("type"), "CLICKHOUSE");
                if ("CLICKHOUSE".equalsIgnoreCase(type)) {
                    result = executeClickHouse(query, dataSourceConfig, timeRange, workspaceId);
                } else if ("REST_API".equalsIgnoreCase(type)) {
                    result = executeRest(query, dataSourceConfig);
                } else {
                    result = Map.of(
                        "columns", List.of(),
                        "rows", List.of(),
                        "meta", Map.of("totalRows", 0, "executedAt", Instant.now().toString(),
                            "warning", "Unsupported data source type: " + type)
                    );
                }
            } catch (Exception ex) {
                result = Map.of(
                    "columns", List.of(),
                    "rows", List.of(),
                    "meta", Map.of("totalRows", 0, "executedAt", Instant.now().toString(),
                        "error", ex.getMessage())
                );
            }

            int totalRows = (Integer) ((Map<String, Object>) result.get("meta")).get("totalRows");
            int ttl = totalRows == 0 ? EMPTY_TTL_SECONDS : DEFAULT_TTL_SECONDS;
            redisTemplate.opsForValue().set(cacheKey, toJson(result), java.time.Duration.ofSeconds(ttl));
            return result;
        }, executor);
    }

    public void invalidate(String widgetId) {
        String pattern = "query:" + widgetId + ":*";
        java.util.Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Map<String, Object> executeClickHouse(Map<String, Object> query,
                                                 Map<String, Object> dataSourceConfig,
                                                 Map<String, Object> timeRange,
                                                 String workspaceId) throws Exception {
        String sql = renderClickHouseSql(query, dataSourceConfig, timeRange, workspaceId);
        DataSource dataSource = clickHouseDataSourceFactory.create(dataSourceConfig);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return mapResultSet(rs);
        }
    }

    private Map<String, Object> executeRest(Map<String, Object> query,
                                          Map<String, Object> dataSourceConfig) throws Exception {
        String url = Objects.toString(dataSourceConfig.get("url"), "");
        String method = Objects.toString(query.get("method"), "GET");
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String response;
            if ("POST".equalsIgnoreCase(method)) {
                HttpPost post = new HttpPost(URI.create(url));
                String body = objectMapper.writeValueAsString(query.getOrDefault("body", Map.of()));
                post.setEntity(new StringEntity(body, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
                response = client.execute(post, res -> EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
            } else {
                HttpGet get = new HttpGet(URI.create(url));
                response = client.execute(get, res -> EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
            }
            return parseJson(response);
        }
    }

    private String renderClickHouseSql(Map<String, Object> query,
                                       Map<String, Object> dataSourceConfig,
                                       Map<String, Object> timeRange,
                                       String workspaceId) {
        String table = tableName(dataSourceConfig);
        List<Map<String, Object>> metrics = castList(query.get("metrics"));
        List<Map<String, Object>> dimensions = castList(query.get("dimensions"));
        List<Map<String, Object>> filters = castList(query.get("filters"));
        List<Map<String, Object>> orderBy = castList(query.get("order_by"));
        int requestedLimit = limit(query.get("limit"));
        int fetchLimit = requestedLimit >= MAX_ROWS ? MAX_ROWS + 1 : requestedLimit;

        List<String> selectParts = new ArrayList<>();
        for (Map<String, Object> dim : dimensions) {
            String field = identifier(Objects.toString(dim.get("field")));
            String alias = identifier(Objects.toString(dim.getOrDefault("alias", field)));
            selectParts.add(field + " AS " + quote(alias));
        }
        for (Map<String, Object> metric : metrics) {
            String alias = identifier(Objects.toString(metric.getOrDefault("alias", "value")));
            selectParts.add(metricExpression(Objects.toString(metric.get("expression"))) + " AS " + quote(alias));
        }
        if (selectParts.isEmpty()) {
            selectParts.add("count(*) AS " + quote("value"));
        }

        List<String> whereParts = new ArrayList<>();
        if (workspaceId != null && !workspaceId.isBlank()) {
            whereParts.add("workspace_id = " + quoteLiteral(workspaceId));
        }
        Map<String, String> range = resolveTimeRange(timeRange);
        if (range != null) {
            whereParts.add("timestamp BETWEEN " + quoteLiteral(range.get("from")) + " AND " + quoteLiteral(range.get("to")));
        }
        for (Map<String, Object> filter : filters) {
            whereParts.add(renderFilter(Objects.toString(filter.get("field")), Objects.toString(filter.get("operator")), filter.get("value")));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append(" FROM ").append(table);
        if (!whereParts.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereParts));
        }
        if (!dimensions.isEmpty()) {
            sql.append(" GROUP BY ").append(dimensions.stream().map(d -> identifier(Objects.toString(d.get("field")))).collect(Collectors.joining(", ")));
        }
        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy.stream().map(o -> identifier(Objects.toString(o.get("field"))) + " " + direction(Objects.toString(o.getOrDefault("direction", "DESC")))).collect(Collectors.joining(", ")));
        }
        sql.append(" LIMIT ").append(fetchLimit);
        return sql.toString();
    }

    private Map<String, String> resolveTimeRange(Map<String, Object> timeRange) {
        if (timeRange == null) {
            return null;
        }
        String type = Objects.toString(timeRange.get("type"), "");
        Instant now = Instant.now();
        if ("relative".equalsIgnoreCase(type)) {
            String value = Objects.toString(timeRange.get("value"), "24h");
            Instant from = now.minus(parseDuration(value));
            return Map.of("from", from.toString().replace("T", " ").substring(0, 19), "to", now.toString().replace("T", " ").substring(0, 19));
        } else if ("absolute".equalsIgnoreCase(type)) {
            String start = Objects.toString(timeRange.get("start"), "");
            String end = Objects.toString(timeRange.get("end"), "");
            if (start.isBlank() || end.isBlank()) {
                return null;
            }
            return Map.of("from", start, "to", end);
        }
        return null;
    }

    private java.time.Duration parseDuration(String value) {
        String v = value.trim().toLowerCase();
        if (v.endsWith("h")) {
            return java.time.Duration.ofHours(Long.parseLong(v.substring(0, v.length() - 1)));
        }
        if (v.endsWith("d")) {
            return java.time.Duration.ofDays(Long.parseLong(v.substring(0, v.length() - 1)));
        }
        if (v.endsWith("m")) {
            return java.time.Duration.ofMinutes(Long.parseLong(v.substring(0, v.length() - 1)));
        }
        return java.time.Duration.ofHours(24);
    }

    private String renderFilter(String field, String operator, Object value) {
        String safeField = identifier(field);
        String op = operator == null ? "EQ" : operator.toUpperCase();
        return switch (op) {
            case "EQ" -> safeField + " = " + quoteLiteral(Objects.toString(value));
            case "NEQ", "NE" -> safeField + " != " + quoteLiteral(Objects.toString(value));
            case "GT" -> safeField + " > " + quoteLiteral(Objects.toString(value));
            case "GTE" -> safeField + " >= " + quoteLiteral(Objects.toString(value));
            case "LT" -> safeField + " < " + quoteLiteral(Objects.toString(value));
            case "LTE" -> safeField + " <= " + quoteLiteral(Objects.toString(value));
            case "LIKE" -> safeField + " LIKE " + quoteLiteral(Objects.toString(value));
            case "IN" -> safeField + " IN (" + inClause(value) + ")";
            default -> throw new IllegalArgumentException("Unsupported filter operator: " + operator);
        };
    }

    private String inClause(Object value) {
        List<?> list = castListRaw(value);
        return list.stream().map(v -> quoteLiteral(Objects.toString(v))).collect(Collectors.joining(", "));
    }

    private String tableName(Map<String, Object> dataSourceConfig) {
        String table = identifier(Objects.toString(dataSourceConfig.getOrDefault("table", "canvas_events")));
        if (dataSourceConfig.containsKey("database")) {
            return identifier(Objects.toString(dataSourceConfig.get("database"))) + "." + table;
        }
        return table;
    }

    private int limit(Object value) {
        if (value instanceof Number number) {
            return Math.max(1, Math.min(MAX_ROWS, number.intValue()));
        }
        return 100;
    }

    private String direction(String value) {
        return "ASC".equalsIgnoreCase(value) ? "ASC" : "DESC";
    }

    private String metricExpression(String expression) {
        String candidate = expression == null ? "" : expression.trim().toLowerCase();
        if ("count(*)".equals(candidate)) {
            return "count(*)";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(sum|avg|min|max|count)\\(([a-zA-Z_][a-zA-Z0-9_]*)\\)$").matcher(candidate);
        if (matcher.matches()) {
            return matcher.group(1) + "(" + identifier(matcher.group(2)) + ")";
        }
        throw new IllegalArgumentException("Unsupported metric expression: " + expression);
    }

    private String identifier(String value) {
        String candidate = value == null ? "" : value.trim();
        if (!candidate.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid identifier: " + value);
        }
        return candidate;
    }

    private String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String quote(String value) {
        return "\"" + value + "\"";
    }

    private Map<String, Object> mapResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int count = 0;
        boolean truncated = false;
        while (rs.next()) {
            if (count >= MAX_ROWS) {
                truncated = true;
                break;
            }
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
            count++;
        }

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("totalRows", count);
        metaMap.put("executedAt", Instant.now().toString());
        if (truncated) {
            metaMap.put("warning", "Results truncated to " + MAX_ROWS + " rows");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("meta", metaMap);
        return result;
    }

    private String cacheKey(String widgetId, Map<String, Object> timeRange) {
        return "query:" + widgetId + ":" + (timeRange == null ? "none" : toJson(timeRange).hashCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> (Map<String, Object>) item).toList();
        }
        return List.of();
    }

    private List<?> castListRaw(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
