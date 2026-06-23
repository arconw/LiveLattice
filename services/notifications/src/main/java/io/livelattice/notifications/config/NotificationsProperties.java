package io.livelattice.notifications.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.notifications")
public class NotificationsProperties {

    private String emailFrom = "notifications@livelattice.local";
    private Duration deduplicationTtl = Duration.ofMinutes(10);
    private Duration rateLimitWindow = Duration.ofHours(1);
    private int rateLimitMax = 1000;
    private int webhookMaxPerUser = 10;
    private int webhookMaxAttempts = 5;
    private String redisStreamKey = "notifications:in-app";
    private Duration digestScanInterval = Duration.ofMinutes(5);
    private Kafka kafka = new Kafka();

    public String getEmailFrom() {
        return emailFrom;
    }

    public void setEmailFrom(String emailFrom) {
        this.emailFrom = emailFrom;
    }

    public Duration getDeduplicationTtl() {
        return deduplicationTtl;
    }

    public void setDeduplicationTtl(Duration deduplicationTtl) {
        this.deduplicationTtl = deduplicationTtl;
    }

    public Duration getRateLimitWindow() {
        return rateLimitWindow;
    }

    public void setRateLimitWindow(Duration rateLimitWindow) {
        this.rateLimitWindow = rateLimitWindow;
    }

    public int getRateLimitMax() {
        return rateLimitMax;
    }

    public void setRateLimitMax(int rateLimitMax) {
        this.rateLimitMax = rateLimitMax;
    }

    public int getWebhookMaxPerUser() {
        return webhookMaxPerUser;
    }

    public void setWebhookMaxPerUser(int webhookMaxPerUser) {
        this.webhookMaxPerUser = webhookMaxPerUser;
    }

    public int getWebhookMaxAttempts() {
        return webhookMaxAttempts;
    }

    public void setWebhookMaxAttempts(int webhookMaxAttempts) {
        this.webhookMaxAttempts = webhookMaxAttempts;
    }

    public String getRedisStreamKey() {
        return redisStreamKey;
    }

    public void setRedisStreamKey(String redisStreamKey) {
        this.redisStreamKey = redisStreamKey;
    }

    public Duration getDigestScanInterval() {
        return digestScanInterval;
    }

    public void setDigestScanInterval(Duration digestScanInterval) {
        this.digestScanInterval = digestScanInterval;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public static class Kafka {
        private boolean enabled = true;
        private String topics = "member.invited,canvas.comment,canvas.@mention,canvas.export.complete,workspace.quota.warning,system.announcement";
        private String consumerGroupId = "livelattice-notifications";
        private String outputTopic = "notification.created";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTopics() {
            return topics;
        }

        public void setTopics(String topics) {
            this.topics = topics;
        }

        public String getConsumerGroupId() {
            return consumerGroupId;
        }

        public void setConsumerGroupId(String consumerGroupId) {
            this.consumerGroupId = consumerGroupId;
        }

        public String getOutputTopic() {
            return outputTopic;
        }

        public void setOutputTopic(String outputTopic) {
            this.outputTopic = outputTopic;
        }
    }
}
