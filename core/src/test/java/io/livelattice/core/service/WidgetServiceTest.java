package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.livelattice.core.event.DashboardUpdated;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.model.dto.CreateWidgetRequest;
import io.livelattice.core.model.dto.UpdateWidgetRequest;
import io.livelattice.core.model.dto.WidgetResponse;
import io.livelattice.core.model.entity.Dashboard;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Widget;
import io.livelattice.core.model.enums.DashboardWidgetType;
import io.livelattice.core.repository.DashboardRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WidgetRepository;
import io.livelattice.core.service.query.QueryEngine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WidgetServiceTest {

    @Mock
    private WidgetRepository widgetRepository;
    @Mock
    private DashboardRepository dashboardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DataSourceService dataSourceService;
    @Mock
    private QueryEngine queryEngine;
    @Mock
    private PermissionService permissionService;
    @Mock
    private EventPublisher eventPublisher;

    private WidgetService widgetService;
    private final String userId = UUID.randomUUID().toString();
    private final UUID workspaceId = UUID.randomUUID();
    private User user;
    private Dashboard dashboard;

    @BeforeEach
    void setUp() {
        widgetService = new WidgetService(
            widgetRepository,
            dashboardRepository,
            userRepository,
            dataSourceService,
            queryEngine,
            permissionService,
            eventPublisher
        );
        user = new User(userId, "a@b.com", "User1");
        dashboard = new Dashboard(
            workspaceId,
            "Analytics",
            Map.of("columns", 12, "widgets", List.of()),
            Map.of("type", "relative", "value", "24h"),
            0,
            user.getId()
        );
    }

    @Test
    void create_shouldPublishDashboardUpdateAuditEvent() {
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(dashboardRepository.findByIdAndDeletedAtIsNull(dashboard.getId())).thenReturn(Optional.of(dashboard));
        doNothing().when(permissionService).requirePermission(workspaceId.toString(), user.getId().toString(), "widget:create");
        when(widgetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreateWidgetRequest request = new CreateWidgetRequest(
            "MARKDOWN",
            "Notes",
            null,
            Map.of("markdown", "hello"),
            Map.of(),
            Map.of("x", 0, "y", 0, "w", 4, "h", 4)
        );

        WidgetResponse response = widgetService.create(dashboard.getId().toString(), request, userId);

        assertEquals("Notes", response.title());
        verifyDashboardUpdated();
    }

    @Test
    void update_shouldPublishDashboardUpdateAuditEvent() {
        Widget widget = widget();
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(dashboardRepository.findByIdAndDeletedAtIsNull(dashboard.getId())).thenReturn(Optional.of(dashboard));
        when(widgetRepository.findById(widget.getId())).thenReturn(Optional.of(widget));
        doNothing().when(permissionService).requirePermission(workspaceId.toString(), user.getId().toString(), "widget:edit");
        when(widgetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateWidgetRequest request = new UpdateWidgetRequest(
            null,
            "Updated",
            null,
            null,
            null,
            null
        );

        WidgetResponse response = widgetService.update(dashboard.getId().toString(), widget.getId().toString(), request, userId);

        assertEquals("Updated", response.title());
        verify(queryEngine).invalidate(widget.getId().toString());
        verifyDashboardUpdated();
    }

    @Test
    void delete_shouldPublishDashboardUpdateAuditEvent() {
        Widget widget = widget();
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(dashboardRepository.findByIdAndDeletedAtIsNull(dashboard.getId())).thenReturn(Optional.of(dashboard));
        when(widgetRepository.findById(widget.getId())).thenReturn(Optional.of(widget));
        doNothing().when(permissionService).requirePermission(workspaceId.toString(), user.getId().toString(), "widget:delete");

        widgetService.delete(dashboard.getId().toString(), widget.getId().toString(), userId);

        verify(widgetRepository).delete(widget);
        verify(queryEngine).invalidate(widget.getId().toString());
        verifyDashboardUpdated();
    }

    private Widget widget() {
        return new Widget(
            dashboard.getId(),
            DashboardWidgetType.MARKDOWN,
            "Notes",
            null,
            Map.of("markdown", "hello"),
            Map.of(),
            Map.of("x", 0, "y", 0, "w", 4, "h", 4)
        );
    }

    private void verifyDashboardUpdated() {
        verify(eventPublisher).publish(argThat((DashboardUpdated event) ->
            event.dashboardId().equals(dashboard.getId())
                && event.workspaceId().equals(workspaceId)
                && event.userId().equals(user.getId())
        ));
    }
}
