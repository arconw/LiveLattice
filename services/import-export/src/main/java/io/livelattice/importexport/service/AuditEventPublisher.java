package io.livelattice.importexport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.event.DomainAuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ImportExportAuthorizationService authorizationService;
    private final boolean enabled;
    private final String topic;

    public AuditEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper,
                               ImportExportAuthorizationService authorizationService,
                               @Value("${livelattice.audit.events.enabled:true}") boolean enabled,
                               @Value("${livelattice.audit.events.topic:livelattice.audit.events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.enabled = enabled;
        this.topic = topic;
    }

    public void publishCanvasImport(UUID workspaceId, UUID canvasId, String userSubject, Map<String, Object> metadata) {
        publish("canvas.import", workspaceId, canvasId, userSubject, metadata);
    }

    public void publishCanvasExport(UUID workspaceId, UUID canvasId, String userSubject, Map<String, Object> metadata) {
        publish("canvas.export", workspaceId, canvasId, userSubject, metadata);
    }

    private void publish(String action, UUID workspaceId, UUID canvasId, String userSubject, Map<String, Object> metadata) {
        if (!enabled) {
            return;
        }
        try {
            UUID eventId = UUID.randomUUID();
            UUID actorId = authorizationService.resolveUserId(userSubject);
            DomainAuditEvent event = new DomainAuditEvent(
                action,
                "canvas",
                eventId.toString(),
                canvasId.toString(),
                workspaceId.toString(),
                actorId.toString(),
                null,
                metadata == null ? Map.of() : metadata,
                Instant.now()
            );
            kafkaTemplate.send(topic, event.id(), objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
        }
    }
}
