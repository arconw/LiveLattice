package io.livelattice.notifications.delivery;

import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationType;
import io.livelattice.notifications.template.NotificationTemplate;
import io.livelattice.notifications.template.RenderedNotification;
import io.livelattice.notifications.template.TemplateCatalog;
import io.livelattice.notifications.template.TemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryService {

    private final JavaMailSender mailSender;
    private final TemplateCatalog templateCatalog;
    private final TemplateRenderer templateRenderer;
    private final NotificationsProperties properties;

    public EmailDeliveryService(JavaMailSender mailSender,
                                TemplateCatalog templateCatalog,
                                TemplateRenderer templateRenderer,
                                NotificationsProperties properties) {
        this.mailSender = mailSender;
        this.templateCatalog = templateCatalog;
        this.templateRenderer = templateRenderer;
        this.properties = properties;
    }

    public DeliveryResult send(NotificationEntity notification) {
        String recipientEmail = recipientEmail(notification.getData());
        if (recipientEmail == null) {
            return DeliveryResult.failure("Missing recipient email");
        }
        NotificationType type = NotificationType.fromValue(notification.getType());
        NotificationTemplate template = templateCatalog.template(type);
        RenderedNotification rendered = new RenderedNotification(
            notification.getTitle(),
            notification.getBody(),
            notification.getActionUrl(),
            notification.getData()
        );
        String html = templateRenderer.renderEmail(template, rendered);
        return sendMessage(recipientEmail, notification.getTitle(), html);
    }

    public DeliveryResult sendDigest(UUID recipientId, List<NotificationEntity> notifications) {
        if (notifications.isEmpty()) {
            return DeliveryResult.success();
        }
        String recipientEmail = recipientEmail(notifications.get(0).getData());
        if (recipientEmail == null) {
            return DeliveryResult.failure("Missing recipient email for " + recipientId);
        }
        List<RenderedNotification> rendered = notifications.stream()
            .map(notification -> new RenderedNotification(notification.getTitle(), notification.getBody(), notification.getActionUrl(), notification.getData()))
            .toList();
        String html = templateRenderer.renderDigest(rendered);
        return sendMessage(recipientEmail, "LiveLattice notification digest", html);
    }

    private DeliveryResult sendMessage(String recipientEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(properties.getEmailFrom());
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            return DeliveryResult.success();
        } catch (MessagingException | RuntimeException ex) {
            return DeliveryResult.failure(ex.getMessage());
        }
    }

    private String recipientEmail(Map<String, Object> data) {
        Object value = data.get("recipientEmail");
        if (value == null) {
            value = data.get("email");
        }
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
