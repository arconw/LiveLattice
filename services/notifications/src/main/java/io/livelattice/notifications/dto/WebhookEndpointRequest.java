package io.livelattice.notifications.dto;

import io.livelattice.notifications.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.hibernate.validator.constraints.URL;

public record WebhookEndpointRequest(
    @NotBlank @URL String url,
    @Size(max = 32) Set<NotificationType> events
) {
}
