package io.livelattice.auditlog.kafka;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.service.AuditEventService;
import org.junit.jupiter.api.Test;

class AuditEventConsumerTest {

    private final AuditEventService auditEventService = mock(AuditEventService.class);
    private final AuditEventConsumer consumer = new AuditEventConsumer(auditEventService, new ObjectMapper());

    @Test
    void mapsDomainAuditEventWithoutMutatingAction() {
        String message = """
            {
              "eventType": "canvas.create",
              "targetType": "canvas",
              "id": "canvas-1",
              "workspaceId": "ws-1",
              "actorId": "actor-1",
              "changes": {"version": 1},
              "metadata": {},
              "occurredAt": "2026-06-24T00:00:00Z"
            }
            """;
        consumer.consume(message);
        verify(auditEventService).ingest(argThat(event ->
            "canvas.create".equals(event.getAction())
                && "canvas".equals(event.getTargetType())
                && "canvas-1".equals(event.getTargetId())
                && "ws-1".equals(event.getWorkspaceId())
                && "actor-1".equals(event.getActorId())
                && event.getOccurredAt() != null
        ));
    }

    @Test
    void usesUnknownActionWhenEventTypeMissing() {
        String message = "{\"id\":\"x\",\"workspaceId\":\"ws-1\",\"actorId\":\"a\"}";
        consumer.consume(message);
        verify(auditEventService).ingest(argThat(event ->
            "unknown".equals(event.getAction())
                && "x".equals(event.getTargetId())
                && "{}".equals(event.getChanges())
                && "{}".equals(event.getMetadata())
        ));
    }

    @Test
    void prefersExplicitTargetIdOverEventId() {
        String message = """
            {
              "eventType": "auth.logout",
              "targetType": "auth",
              "id": "event-1",
              "targetId": "user-1",
              "workspaceId": "user-1",
              "actorId": "user-1"
            }
            """;
        consumer.consume(message);
        verify(auditEventService).ingest(argThat(event ->
            "auth.logout".equals(event.getAction())
                && "auth".equals(event.getTargetType())
                && "user-1".equals(event.getTargetId())
                && "user-1".equals(event.getActorId())
        ));
    }
}
