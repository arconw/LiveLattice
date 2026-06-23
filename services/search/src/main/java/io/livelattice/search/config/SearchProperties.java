package io.livelattice.search.config;

import io.livelattice.search.model.SearchType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "livelattice.search")
public class SearchProperties {
    private String opensearchUrl = "http://localhost:9200";
    private List<String> indexNames = List.of("canvases", "comments", "documents", "dashboards", "templates", "users");
    private String canvasIndexName = "canvases";
    private String commentIndexName = "comments";
    private String documentIndexName = "documents";
    private String dashboardIndexName = "dashboards";
    private String templateIndexName = "templates";
    private String userIndexName = "users";
    private String lifecyclePolicyName = "livelattice-search-lifecycle";
    private Duration suggestCacheTtl = Duration.ofMinutes(5);
    private int suggestCacheSize = 10;
    private Duration bulkFlushInterval = Duration.ofSeconds(5);
    private int bulkBatchSize = 1000;
    private int searchTimeoutSeconds = 5;
    private int suggestTimeoutSeconds = 1;
    private int pageSize = 20;
    private int maxPageSize = 100;
    private boolean autoCreateIndexes = true;
    private Kafka kafka = new Kafka();

    public String getOpensearchUrl() {
        return opensearchUrl;
    }

    public void setOpensearchUrl(String opensearchUrl) {
        this.opensearchUrl = opensearchUrl;
    }

    public List<String> getIndexNames() {
        return indexNames;
    }

    public void setIndexNames(List<String> indexNames) {
        this.indexNames = indexNames;
    }

    public String getCanvasIndexName() {
        return canvasIndexName;
    }

    public void setCanvasIndexName(String canvasIndexName) {
        this.canvasIndexName = canvasIndexName;
    }

    public String getCommentIndexName() {
        return commentIndexName;
    }

    public void setCommentIndexName(String commentIndexName) {
        this.commentIndexName = commentIndexName;
    }

    public String getDocumentIndexName() {
        return documentIndexName;
    }

    public void setDocumentIndexName(String documentIndexName) {
        this.documentIndexName = documentIndexName;
    }

    public String getDashboardIndexName() {
        return dashboardIndexName;
    }

    public void setDashboardIndexName(String dashboardIndexName) {
        this.dashboardIndexName = dashboardIndexName;
    }

    public String getTemplateIndexName() {
        return templateIndexName;
    }

    public void setTemplateIndexName(String templateIndexName) {
        this.templateIndexName = templateIndexName;
    }

    public String getUserIndexName() {
        return userIndexName;
    }

    public void setUserIndexName(String userIndexName) {
        this.userIndexName = userIndexName;
    }

    public String getLifecyclePolicyName() {
        return lifecyclePolicyName;
    }

    public void setLifecyclePolicyName(String lifecyclePolicyName) {
        this.lifecyclePolicyName = lifecyclePolicyName;
    }

    public Duration getSuggestCacheTtl() {
        return suggestCacheTtl;
    }

    public void setSuggestCacheTtl(Duration suggestCacheTtl) {
        this.suggestCacheTtl = suggestCacheTtl;
    }

    public int getSuggestCacheSize() {
        return suggestCacheSize;
    }

    public void setSuggestCacheSize(int suggestCacheSize) {
        this.suggestCacheSize = suggestCacheSize;
    }

    public Duration getBulkFlushInterval() {
        return bulkFlushInterval;
    }

    public void setBulkFlushInterval(Duration bulkFlushInterval) {
        this.bulkFlushInterval = bulkFlushInterval;
    }

    public int getBulkBatchSize() {
        return bulkBatchSize;
    }

    public void setBulkBatchSize(int bulkBatchSize) {
        this.bulkBatchSize = bulkBatchSize;
    }

    public int getSearchTimeoutSeconds() {
        return searchTimeoutSeconds;
    }

    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) {
        this.searchTimeoutSeconds = searchTimeoutSeconds;
    }

    public int getSuggestTimeoutSeconds() {
        return suggestTimeoutSeconds;
    }

    public void setSuggestTimeoutSeconds(int suggestTimeoutSeconds) {
        this.suggestTimeoutSeconds = suggestTimeoutSeconds;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public boolean isAutoCreateIndexes() {
        return autoCreateIndexes;
    }

    public void setAutoCreateIndexes(boolean autoCreateIndexes) {
        this.autoCreateIndexes = autoCreateIndexes;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Map<SearchType, String> indexNamesByType() {
        Map<SearchType, String> indexes = new EnumMap<>(SearchType.class);
        indexes.put(SearchType.CANVAS, canvasIndexName);
        indexes.put(SearchType.COMMENT, commentIndexName);
        indexes.put(SearchType.DOCUMENT, documentIndexName);
        indexes.put(SearchType.DASHBOARD, dashboardIndexName);
        indexes.put(SearchType.TEMPLATE, templateIndexName);
        indexes.put(SearchType.USER, userIndexName);
        return indexes;
    }

    public String indexName(SearchType type) {
        return indexNamesByType().get(type);
    }

    public List<String> suggestIndexNames() {
        return List.of(canvasIndexName, documentIndexName, dashboardIndexName, templateIndexName, userIndexName);
    }

    public static class Kafka {
        private boolean enabled = true;
        private List<String> topics = List.of(
            "canvas.created",
            "canvas.updated",
            "canvas.deleted",
            "comment.added",
            "comment.deleted",
            "document.created",
            "document.updated",
            "document.deleted",
            "dashboard.created",
            "dashboard.updated",
            "dashboard.deleted",
            "template.created",
            "template.updated",
            "template.deleted",
            "user.created",
            "user.updated",
            "user.deleted"
        );
        private String consumerGroupId = "livelattice-search-indexer";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }

        public String getConsumerGroupId() {
            return consumerGroupId;
        }

        public void setConsumerGroupId(String consumerGroupId) {
            this.consumerGroupId = consumerGroupId;
        }
    }
}
