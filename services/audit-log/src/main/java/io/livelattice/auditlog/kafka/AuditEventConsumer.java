package io.livelattice.auditlog.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.model.AuditEventEntity;
import io.livelattice.auditlog.service.AuditEventService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditEventService auditEventService, ObjectMapper objectMapper) {
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "#{'${livelattice.audit.kafka-topics:livelattice.audit.events}'.split(',')}", groupId = "${livelattice.audit.kafka-group-id:livelattice-audit-log}")
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            AuditEventEntity event = new AuditEventEntity();
            event.setId(UUID.randomUUID().toString());
            event.setWorkspaceId(resolveText(root, "workspaceId", "workspace_id", "00000000-0000-0000-0000-000000000000"));
            event.setActorId(resolveText(root, "actorId", "actor_id", "authorId", "userId", "00000000-0000-0000-0000-000000000000"));
            event.setAction(resolveAction(root));
            event.setTargetType(resolveText(root, "targetType", "target_type", "entityType", "unknown"));
            event.setTargetId(resolveText(root, "targetId", "target_id", "id", "00000000-0000-0000-0000-000000000000"));
            event.setChanges(jsonOrEmpty(root.path("changes")));
            event.setMetadata(jsonOrEmpty(root.path("metadata")));
            event.setOccurredAt(resolveInstant(root));
            auditEventService.ingest(event);
        } catch (Exception ex) {
            log.warn("Failed to process audit event: {}", ex.getMessage());
        }
    }

    private String resolveAction(JsonNode root) {
        String action = resolveText(root, "eventType", "type", "action", "unknown");
        return action.toLowerCase();
    }

    private String jsonOrEmpty(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String resolveText(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode node = root.path(key);
            if (!node.isMissingNode() && !node.isNull()) {
                String value = node.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return keys[keys.length - 1];
    }

    private Instant resolveInstant(JsonNode root) {
        JsonNode node = root.path("occurredAt");
        if (!node.isMissingNode() && !node.isNull()) {
            try {
                return Instant.parse(node.asText());
            } catch (Exception ex) {
                return Instant.now();
            }
        }
        return Instant.now();
    }
}
