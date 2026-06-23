package io.livelattice.notifications.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum NotificationType {
    MEMBER_INVITED("member.invited"),
    MEMBER_JOINED("member.joined"),
    CANVAS_SHARED("canvas.shared"),
    CANVAS_COMMENT("canvas.comment"),
    CANVAS_MENTION("canvas.@mention"),
    CANVAS_EXPORT_COMPLETE("canvas.export.complete"),
    CANVAS_IMPORT_COMPLETE("canvas.import.complete"),
    DASHBOARD_SHARED("dashboard.shared"),
    WORKSPACE_QUOTA_WARNING("workspace.quota.warning"),
    SYSTEM_ANNOUNCEMENT("system.announcement");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static NotificationType fromValue(String value) {
        return Arrays.stream(values())
            .filter(type -> type.value.equals(value) || type.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported notification type: " + value));
    }
}
