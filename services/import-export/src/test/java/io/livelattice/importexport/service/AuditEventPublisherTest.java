package io.livelattice.importexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ObjectMapperConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class AuditEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapperConfig().objectMapper();

    @Test
    void publishCanvasImportSendsAuditContract() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ImportExportAuthorizationService authorizationService = mock(ImportExportAuthorizationService.class);
        UUID workspaceId = UUID.randomUUID();
        UUID canvasId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(authorizationService.resolveUserId("subject")).thenReturn(actorId);
        AuditEventPublisher publisher = new AuditEventPublisher(kafkaTemplate, objectMapper, authorizationService, true, "livelattice.audit.events");

        publisher.publishCanvasImport(workspaceId, canvasId, "subject", Map.of("mode", "sync"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("livelattice.audit.events"), anyString(), payload.capture());
        Map<String, Object> event = objectMapper.readValue(payload.getValue(), new TypeReference<>() {});
        assertThat(event)
            .containsEntry("eventType", "canvas.import")
            .containsEntry("targetType", "canvas")
            .containsEntry("targetId", canvasId.toString())
            .containsEntry("workspaceId", workspaceId.toString())
            .containsEntry("actorId", actorId.toString());
        assertThat(event.get("id")).isNotEqualTo(canvasId.toString());
        assertThat(event.get("occurredAt")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) event.get("metadata");
        assertThat(metadata).containsEntry("mode", "sync");
    }

    @Test
    void disabledPublisherDoesNotSend() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ImportExportAuthorizationService authorizationService = mock(ImportExportAuthorizationService.class);
        AuditEventPublisher publisher = new AuditEventPublisher(kafkaTemplate, objectMapper, authorizationService, false, "livelattice.audit.events");

        publisher.publishCanvasExport(UUID.randomUUID(), UUID.randomUUID(), "subject", Map.of());

        verifyNoInteractions(kafkaTemplate, authorizationService);
    }
}
