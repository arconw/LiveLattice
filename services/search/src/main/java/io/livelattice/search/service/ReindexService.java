package io.livelattice.search.service;

import io.livelattice.search.config.AuthProperties;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.dto.ReindexResponse;
import io.livelattice.search.exception.ForbiddenException;
import io.livelattice.search.kafka.IndexEvent;
import io.livelattice.search.opensearch.IndexManager;
import io.livelattice.search.opensearch.IndexEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

    private final AuthProperties authProperties;
    private final IndexManager indexManager;
    private final PostgresReindexSource reindexSource;
    private final IndexEventProcessor processor;

    public ReindexService(AuthProperties authProperties,
                          IndexManager indexManager,
                          PostgresReindexSource reindexSource,
                          IndexEventProcessor processor) {
        this.authProperties = authProperties;
        this.indexManager = indexManager;
        this.reindexSource = reindexSource;
        this.processor = processor;
    }

    public ReindexResponse trigger(Map<String, String> headers) {
        requireAdmin(headers);
        CompletableFuture.runAsync(this::rebuild);
        return new ReindexResponse("accepted", "Reindex rebuild from PostgreSQL started");
    }

    private void requireAdmin(Map<String, String> headers) {
        String roles = headers.entrySet().stream()
            .filter(entry -> "x-auth-roles".equals(entry.getKey().toLowerCase(Locale.ROOT)))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        String adminRole = authProperties.getReindexAdminRole();
        boolean admin = roles != null && Arrays.stream(roles.split(","))
            .map(String::trim)
            .anyMatch(role -> role.equalsIgnoreCase(adminRole));
        if (!admin) {
            throw new ForbiddenException("Admin role is required");
        }
    }

    private void rebuild() {
        try {
            indexManager.recreateIndexes();
            for (IndexEvent event : reindexSource.events()) {
                processor.process(event);
            }
            processor.flush();
        } catch (Exception ex) {
            log.warn("Reindex rebuild failed", ex);
        }
    }
}
