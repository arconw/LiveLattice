package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.model.dto.CreateDashboardRequest;
import io.livelattice.core.model.dto.DashboardResponse;
import io.livelattice.core.model.dto.UpdateDashboardRequest;
import io.livelattice.core.model.entity.Dashboard;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Widget;
import io.livelattice.core.model.enums.DashboardWidgetType;
import io.livelattice.core.repository.DashboardRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WidgetRepository;
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
class DashboardServiceTest {

    @Mock
    private DashboardRepository dashboardRepository;
    @Mock
    private WidgetRepository widgetRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private QuotaService quotaService;

    private DashboardService dashboardService;
    private final String userId = UUID.randomUUID().toString();
    private final String wsId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
            dashboardRepository, widgetRepository, userRepository,
            permissionService, quotaService
        );
    }

    @Test
    void create_shouldCreateDashboard() {
        CreateDashboardRequest request = new CreateDashboardRequest(
            wsId, "Analytics", null, Map.of("columns", 12, "widgets", List.of()), null, null
        );
        User user = new User(userId, "a@b.com", "User1");
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "dashboard:create");
        doNothing().when(quotaService).checkDashboardQuota(wsId);
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DashboardResponse response = dashboardService.create(request, userId);

        assertEquals("Analytics", response.title());
        assertEquals(wsId, response.workspaceId());
        assertEquals(12, response.layout().get("columns"));
        assertEquals("relative", response.timeRange().get("type"));
    }

    @Test
    void getById_shouldReturnDashboard() {
        Dashboard dashboard = new Dashboard(
            UUID.fromString(wsId), "Analytics", Map.of("columns", 12, "widgets", List.of()),
            Map.of("type", "relative", "value", "24h"), 0, UUID.fromString(userId)
        );
        User user = new User(userId, "a@b.com", "User1");
        when(dashboardRepository.findByIdAndDeletedAtIsNull(dashboard.getId())).thenReturn(Optional.of(dashboard));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "dashboard:read");

        DashboardResponse response = dashboardService.getById(dashboard.getId().toString(), userId);

        assertEquals("Analytics", response.title());
    }

    @Test
    void update_shouldModifyDashboard() {
        Dashboard dashboard = new Dashboard(
            UUID.fromString(wsId), "Old", Map.of("columns", 12, "widgets", List.of()),
            Map.of("type", "relative", "value", "24h"), 0, UUID.fromString(userId)
        );
        User user = new User(userId, "a@b.com", "User1");
        when(dashboardRepository.findByIdAndDeletedAtIsNull(dashboard.getId())).thenReturn(Optional.of(dashboard));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "dashboard:edit");
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DashboardResponse response = dashboardService.update(
            dashboard.getId().toString(),
            new UpdateDashboardRequest("New", null, null, null, 60, null),
            userId
        );

        assertEquals("New", response.title());
        assertEquals(60, response.autoRefresh());
    }

    @Test
    void duplicate_shouldCopyDashboardAndWidgets() {
        Dashboard source = new Dashboard(
            UUID.fromString(wsId), "Source", Map.of("columns", 12, "widgets", List.of()),
            Map.of("type", "relative", "value", "24h"), 30, UUID.fromString(userId)
        );
        Widget widget = new Widget(
            source.getId(), DashboardWidgetType.BAR_CHART, "Events", null,
            Map.of("metrics", List.of()), Map.of(), Map.of("x", 0, "y", 0, "w", 4, "h", 4)
        );
        User user = new User(userId, "a@b.com", "User1");
        when(dashboardRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "dashboard:create");
        doNothing().when(quotaService).checkDashboardQuota(wsId);
        when(widgetRepository.findByDashboardId(source.getId())).thenReturn(List.of(widget));
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(widgetRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DashboardResponse response = dashboardService.duplicate(source.getId().toString(), userId);

        assertEquals("Source (Copy)", response.title());
        assertEquals(30, response.autoRefresh());
    }
}
