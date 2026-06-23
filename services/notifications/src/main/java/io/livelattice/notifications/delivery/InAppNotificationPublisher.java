package io.livelattice.notifications.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.model.NotificationEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class InAppNotificationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationsProperties properties;

    public InAppNotificationPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, NotificationsProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(NotificationEntity notification) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("id", notification.getId().toString());
            payload.put("recipientId", notification.getRecipientId().toString());
            payload.put("type", notification.getType());
            payload.put("title", notification.getTitle());
            payload.put("body", notification.getBody());
            payload.put("actionUrl", notification.getActionUrl() == null ? "" : notification.getActionUrl());
            payload.put("data", objectMapper.writeValueAsString(notification.getData()));
            MapRecord<String, String, String> record = StreamRecords.mapBacked(payload).withStreamKey(properties.getRedisStreamKey());
            redisTemplate.opsForStream().add(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize in-app notification", ex);
        }
    }
}
