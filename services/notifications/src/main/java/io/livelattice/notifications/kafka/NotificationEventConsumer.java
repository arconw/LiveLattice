package io.livelattice.notifications.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.notifications.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "#{'${livelattice.notifications.kafka.topics:member.invited,canvas.comment,canvas.@mention,canvas.export.complete,workspace.quota.warning,system.announcement}'.split(',')}",
        groupId = "${livelattice.notifications.kafka.consumer-group-id:livelattice-notifications}",
        autoStartup = "${livelattice.notifications.kafka.enabled:true}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            NotificationDomainEvent event = objectMapper.readValue(record.value(), NotificationDomainEvent.class).withDefaults(record.topic());
            notificationService.handleDomainEvent(event);
        } catch (Exception ex) {
            log.warn("Failed to process notification event from topic {}", record.topic(), ex);
        }
    }
}
