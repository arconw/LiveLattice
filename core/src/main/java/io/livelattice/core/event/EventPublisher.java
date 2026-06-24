package io.livelattice.core.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.Comment;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CommentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class EventPublisher {

    private final ApplicationEventPublisher publisher;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CanvasRepository canvasRepository;
    private final CommentRepository commentRepository;
    private final boolean searchEventsEnabled;
    private final boolean auditEventsEnabled;

    public EventPublisher(ApplicationEventPublisher publisher,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper,
                          CanvasRepository canvasRepository,
                          CommentRepository commentRepository,
                          @Value("${livelattice.search.events.enabled:true}") boolean searchEventsEnabled,
                          @Value("${livelattice.audit.events.enabled:true}") boolean auditEventsEnabled) {
        this.publisher = publisher;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.canvasRepository = canvasRepository;
        this.commentRepository = commentRepository;
        this.searchEventsEnabled = searchEventsEnabled;
        this.auditEventsEnabled = auditEventsEnabled;
    }

    public void publish(CanvasCreated event) {
        publisher.publishEvent(event);
        publishSearchEvent("canvas.created", canvasEvent("canvas.created", event.canvasId(), event.userId(), false));
        publishAuditEvent(DomainAuditEvent.canvasCreated(event.canvasId(), event.workspaceId(), event.userId()));
    }

    public void publish(CanvasUpdated event) {
        publisher.publishEvent(event);
        publishSearchEvent("canvas.updated", canvasEvent("canvas.updated", event.canvasId(), event.userId(), false));
        publishAuditEvent(DomainAuditEvent.canvasUpdated(event.canvasId(), event.workspaceId(), event.userId(), event.version()));
    }

    public void publish(CanvasDeleted event) {
        publisher.publishEvent(event);
        publishSearchEvent("canvas.deleted", canvasEvent("canvas.deleted", event.canvasId(), event.userId(), true));
        publishAuditEvent(DomainAuditEvent.canvasDeleted(event.canvasId(), event.workspaceId(), event.userId()));
    }

    public void publish(CanvasRestored event) {
        publisher.publishEvent(event);
        publishSearchEvent("canvas.updated", canvasEvent("canvas.updated", event.canvasId(), event.userId(), false));
        publishAuditEvent(DomainAuditEvent.canvasRestored(event.canvasId(), event.workspaceId(), event.userId(), event.restoredSnapshotVersion(), event.version()));
    }

    public void publish(CommentAdded event) {
        publisher.publishEvent(event);
        publishSearchEvent("comment.added", commentEvent("comment.added", event.commentId(), event.canvasId(), event.authorId(), false));
        publishAuditEvent(DomainAuditEvent.commentAdded(event.commentId(), event.canvasId(), resolveWorkspaceId(event.canvasId()), event.authorId()));
    }

    public void publish(CommentDeleted event) {
        publisher.publishEvent(event);
        publishSearchEvent("comment.deleted", commentEvent("comment.deleted", event.commentId(), event.canvasId(), event.userId(), true));
        publishAuditEvent(DomainAuditEvent.commentDeleted(event.commentId(), event.canvasId(), event.workspaceId(), event.userId()));
    }

    public void publish(WorkspaceCreated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.workspaceCreated(event.workspaceId(), event.userId()));
    }

    public void publish(WorkspaceUpdated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.workspaceUpdated(event.workspaceId(), event.userId()));
    }

    public void publish(WorkspaceDeleted event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.workspaceDeleted(event.workspaceId(), event.userId()));
    }

    public void publish(MemberInvited event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.memberInvited(event.workspaceId(), event.invitedUserId(), event.inviterUserId()));
    }

    public void publish(MemberRoleChanged event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.memberRoleChanged(event.workspaceId(), event.targetUserId(), event.actorUserId()));
    }

    public void publish(MemberRemoved event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.memberRemoved(event.workspaceId(), event.targetUserId(), event.actorUserId()));
    }

    public void publish(DashboardCreated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.dashboardCreated(event.dashboardId(), event.workspaceId(), event.userId()));
    }

    public void publish(DashboardUpdated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.dashboardUpdated(event.dashboardId(), event.workspaceId(), event.userId()));
    }

    public void publish(DashboardDeleted event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.dashboardDeleted(event.dashboardId(), event.workspaceId(), event.userId()));
    }

    public void publish(DataSourceCreated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.dataSourceCreated(event.dataSourceId(), event.workspaceId(), event.userId()));
    }

    public void publish(DataSourceUpdated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.dataSourceUpdated(event.dataSourceId(), event.workspaceId(), event.userId()));
    }

    public void publish(DataSourceDeleted event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.dataSourceDeleted(event.dataSourceId(), event.workspaceId(), event.userId()));
    }

    public void publish(ApiKeyCreated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.apiKeyCreated(event.keyId(), event.workspaceId(), event.userId()));
    }

    public void publish(ApiKeyRevoked event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.apiKeyRevoked(event.keyId(), event.workspaceId(), event.userId()));
    }

    public void publish(SettingsUpdated event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.settingsUpdated(event.workspaceId(), event.userId()));
    }

    public void publish(TierChanged event) {
        publisher.publishEvent(event);
        publishAuditEvent(DomainAuditEvent.tierChanged(event.workspaceId(), event.userId(), event.tier()));
    }

    private SearchIndexEvent canvasEvent(String eventType, java.util.UUID canvasId, java.util.UUID userId, boolean deleted) {
        return canvasRepository.findById(canvasId)
            .map(canvas -> new SearchIndexEvent(
                eventType,
                "canvas",
                canvas.getId().toString(),
                canvas.getWorkspaceId().toString(),
                canvas.getTitle(),
                textContent(canvas.getContent()),
                tags(canvas.getContent()),
                userId.toString(),
                null,
                canvas.getCreatedAt(),
                canvas.getUpdatedAt(),
                deleted,
                false,
                Map.of("version", canvas.getVersion())
            ))
            .orElseGet(() -> new SearchIndexEvent(
                eventType,
                "canvas",
                canvasId.toString(),
                null,
                null,
                null,
                List.of(),
                userId.toString(),
                null,
                null,
                null,
                deleted,
                false,
                Map.of()
            ));
    }

    private SearchIndexEvent commentEvent(String eventType, java.util.UUID commentId, java.util.UUID canvasId, java.util.UUID userId, boolean deleted) {
        Comment comment = commentRepository.findById(commentId).orElse(null);
        Canvas canvas = canvasRepository.findById(canvasId).orElse(null);
        return new SearchIndexEvent(
            eventType,
            "comment",
            commentId.toString(),
            canvas == null ? null : canvas.getWorkspaceId().toString(),
            null,
            comment == null ? null : comment.getContent(),
            List.of(),
            comment == null ? userId.toString() : comment.getAuthorId().toString(),
            canvasId.toString(),
            comment == null ? null : comment.getCreatedAt(),
            comment == null ? null : comment.getUpdatedAt(),
            deleted,
            comment != null && comment.isResolved(),
            metadata(comment)
        );
    }

    private Map<String, Object> metadata(@Nullable Comment comment) {
        if (comment == null || comment.getTargetElementId() == null || comment.getTargetElementId().isBlank()) {
            return Map.of();
        }
        return Map.of("target_element_id", comment.getTargetElementId());
    }

    private void publishSearchEvent(String topic, SearchIndexEvent event) {
        if (!searchEventsEnabled) {
            return;
        }
        Runnable task = () -> send(topic, event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void send(String topic, SearchIndexEvent event) {
        try {
            kafkaTemplate.send(topic, event.id(), objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
        }
    }

    private void publishAuditEvent(DomainAuditEvent event) {
        if (!auditEventsEnabled) {
            return;
        }
        Runnable task = () -> sendAudit(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void sendAudit(DomainAuditEvent event) {
        try {
            kafkaTemplate.send("livelattice.audit.events", event.id(), objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
        }
    }

    private UUID resolveWorkspaceId(UUID canvasId) {
        return canvasRepository.findById(canvasId)
            .map(Canvas::getWorkspaceId)
            .orElse(null);
    }

    private String textContent(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream()
                .map(this::textContent)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                String text = textContent(item);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return String.join(" ", values);
        }
        return String.valueOf(value);
    }

    private List<String> tags(Map<String, Object> content) {
        Object tags = content.get("tags");
        if (!(tags instanceof Iterable<?>)) {
            Object metadata = content.get("metadata");
            if (metadata instanceof Map<?, ?> metadataMap) {
                tags = metadataMap.get("tags");
            }
        }
        if (!(tags instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object tag : iterable) {
            if (tag != null && !String.valueOf(tag).isBlank()) {
                values.add(String.valueOf(tag));
            }
        }
        return values;
    }
}
