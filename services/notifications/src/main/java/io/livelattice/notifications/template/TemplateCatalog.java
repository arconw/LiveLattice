package io.livelattice.notifications.template;

import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationType;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TemplateCatalog {

    private final Map<NotificationType, NotificationTemplate> templates = Map.ofEntries(
        Map.entry(NotificationType.MEMBER_INVITED, new NotificationTemplate(
            NotificationType.MEMBER_INVITED,
            "You've been invited to {workspaceName}",
            "{actorName} invited you to join {workspaceName}.",
            "{actionUrl}",
            "email/member-invited",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        )),
        Map.entry(NotificationType.MEMBER_JOINED, new NotificationTemplate(
            NotificationType.MEMBER_JOINED,
            "{actorName} joined {workspaceName}",
            "{actorName} is now a member of {workspaceName}.",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP)
        )),
        Map.entry(NotificationType.CANVAS_SHARED, new NotificationTemplate(
            NotificationType.CANVAS_SHARED,
            "{actorName} shared a canvas with you",
            "{actorName} shared {canvasName} with you.",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        )),
        Map.entry(NotificationType.CANVAS_COMMENT, new NotificationTemplate(
            NotificationType.CANVAS_COMMENT,
            "{actorName} commented on {canvasName}",
            "{commentPreview}",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        )),
        Map.entry(NotificationType.CANVAS_MENTION, new NotificationTemplate(
            NotificationType.CANVAS_MENTION,
            "{actorName} mentioned you in {canvasName}",
            "{commentPreview}",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        )),
        Map.entry(NotificationType.CANVAS_EXPORT_COMPLETE, new NotificationTemplate(
            NotificationType.CANVAS_EXPORT_COMPLETE,
            "Your export is ready for download",
            "{canvasName} has finished exporting.",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP)
        )),
        Map.entry(NotificationType.CANVAS_IMPORT_COMPLETE, new NotificationTemplate(
            NotificationType.CANVAS_IMPORT_COMPLETE,
            "Your import completed successfully",
            "{canvasName} is ready.",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP)
        )),
        Map.entry(NotificationType.DASHBOARD_SHARED, new NotificationTemplate(
            NotificationType.DASHBOARD_SHARED,
            "{actorName} shared a dashboard with you",
            "{actorName} shared {dashboardName} with you.",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        )),
        Map.entry(NotificationType.WORKSPACE_QUOTA_WARNING, new NotificationTemplate(
            NotificationType.WORKSPACE_QUOTA_WARNING,
            "You've reached 90% of your workspace quota",
            "{workspaceName} is approaching its quota.",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        )),
        Map.entry(NotificationType.SYSTEM_ANNOUNCEMENT, new NotificationTemplate(
            NotificationType.SYSTEM_ANNOUNCEMENT,
            "{title}",
            "{body}",
            "{actionUrl}",
            "email/default-notification",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        ))
    );

    public NotificationTemplate template(NotificationType type) {
        return templates.get(type);
    }

    public Set<NotificationType> types() {
        return templates.keySet();
    }
}
