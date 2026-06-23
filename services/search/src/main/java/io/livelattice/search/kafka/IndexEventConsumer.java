package io.livelattice.search.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.search.opensearch.IndexEventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IndexEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndexEventConsumer.class);

    private final IndexEventProcessor processor;
    private final ObjectMapper objectMapper;

    public IndexEventConsumer(IndexEventProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "#{'${livelattice.search.kafka.topics:canvas.created,canvas.updated,canvas.deleted,comment.added,comment.deleted,document.created,document.updated,document.deleted,dashboard.created,dashboard.updated,dashboard.deleted,template.created,template.updated,template.deleted,user.created,user.updated,user.deleted}'.split(',')}",
        groupId = "${livelattice.search.kafka.consumer-group-id:livelattice-search-indexer}",
        autoStartup = "${livelattice.search.kafka.enabled:true}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            IndexEvent event = objectMapper.readValue(record.value(), IndexEvent.class).withDefaults(record.topic());
            processor.process(event);
        } catch (Exception e) {
            log.warn("Failed to process index event: {}", record.value(), e);
        }
    }
}
