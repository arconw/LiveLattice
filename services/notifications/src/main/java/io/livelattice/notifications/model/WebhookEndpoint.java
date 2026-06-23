package io.livelattice.notifications.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class WebhookEndpoint {

    private UUID id;
    private String url;
    private String secret;
    private Set<String> events = new LinkedHashSet<>();

    public WebhookEndpoint() {
    }

    public WebhookEndpoint(UUID id, String url, String secret, Set<String> events) {
        this.id = id;
        this.url = url;
        this.secret = secret;
        this.events = events == null ? new LinkedHashSet<>() : new LinkedHashSet<>(events);
    }

    public boolean matches(NotificationType type) {
        return events == null || events.isEmpty() || events.contains(type.value());
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Set<String> getEvents() {
        return events;
    }

    public void setEvents(Set<String> events) {
        this.events = events == null ? new LinkedHashSet<>() : new LinkedHashSet<>(events);
    }
}
