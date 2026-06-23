package io.livelattice.search.opensearch;

import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.kafka.IndexEvent;
import io.livelattice.search.model.SearchType;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class IndexEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IndexEventProcessor.class);

    private final OpenSearchClient client;
    private final SearchProperties properties;
    private final SearchDocumentMapper mapper;
    private final BlockingQueue<IndexEvent> queue = new LinkedBlockingQueue<>();
    private final Object lock = new Object();

    public IndexEventProcessor(OpenSearchClient client, SearchProperties properties, SearchDocumentMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
    }

    public void process(IndexEvent event) {
        queue.offer(event);
        synchronized (lock) {
            if (queue.size() >= properties.getBulkBatchSize()) {
                flush();
            }
        }
    }

    public void flush() {
        List<IndexEvent> batch = new ArrayList<>();
        queue.drainTo(batch, properties.getBulkBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = batch.stream()
            .map(this::operation)
            .flatMap(Optional::stream)
            .toList();
        if (operations.isEmpty()) {
            return;
        }
        try {
            BulkResponse response = client.bulk(builder -> builder.operations(operations));
            if (response.errors()) {
                log.warn("Bulk indexing completed with item errors");
            }
        } catch (IOException ex) {
            log.warn("Bulk indexing failed", ex);
        }
    }

    private Optional<BulkOperation> operation(IndexEvent event) {
        Optional<SearchType> type = mapper.resolveType(event);
        if (type.isEmpty() || event.id() == null || event.id().isBlank()) {
            return Optional.empty();
        }
        String index = properties.indexName(type.get());
        if (event.isDeletedEvent() && type.get() == SearchType.COMMENT) {
            return Optional.of(BulkOperation.of(builder -> builder.delete(delete -> delete
                .index(index)
                .id(event.id())
            )));
        }
        Map<String, Object> document = event.isDeletedEvent()
            ? mapper.deletedDocument(event, type.get())
            : mapper.toDocument(event, type.get());
        return Optional.of(BulkOperation.of(builder -> builder.index(indexOperation -> indexOperation
            .index(index)
            .id(event.id())
            .document(document)
        )));
    }
}
