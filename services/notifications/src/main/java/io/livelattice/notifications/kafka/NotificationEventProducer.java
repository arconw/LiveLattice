package io.livelattice.notifications.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.model.NotificationEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationsProperties properties;

    public NotificationEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, NotificationsProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publishCreated(NotificationEntity notification) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", notification.getId());
            event.put("workspaceId", notification.getWorkspaceId());
            event.put("recipientId", notification.getRecipientId());
            event.put("type", notification.getType());
            event.put("channel", notification.getChannel());
            event.put("status", notification.getStatus());
            event.put("createdAt", notification.getCreatedAt());
            kafkaTemplate.send(properties.getKafka().getOutputTopic(), notification.getId().toString(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Failed to publish notification created event {}", notification.getId(), ex);
        }
    }
}
