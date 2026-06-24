package io.livelattice.core.service;

import io.livelattice.core.event.DashboardCreated;
import io.livelattice.core.event.DashboardDeleted;
import io.livelattice.core.event.DashboardUpdated;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.BadRequestException;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.dto.CreateDashboardRequest;
import io.livelattice.core.model.dto.DashboardResponse;
import io.livelattice.core.model.dto.UpdateDashboardRequest;
import io.livelattice.core.model.dto.WidgetResponse;
import io.livelattice.core.model.entity.Dashboard;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Widget;
import io.livelattice.core.repository.DashboardRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WidgetRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final WidgetRepository widgetRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final QuotaService quotaService;
    private final EventPublisher eventPublisher;

    public DashboardService(DashboardRepository dashboardRepository,
                              WidgetRepository widgetRepository,
                              UserRepository userRepository,
                              PermissionService permissionService,
                              QuotaService quotaService,
                              EventPublisher eventPublisher) {
        this.dashboardRepository = dashboardRepository;
        this.widgetRepository = widgetRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.quotaService = quotaService;
        this.eventPublisher = eventPublisher;
    }

    private User resolveUser(String externalSubject) {
        return userRepository.findByExternalSubject(externalSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + externalSubject));
    }

    private UUID resolveUserId(String externalSubject) {
        return resolveUser(externalSubject).getId();
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID for " + field + ": " + value);
        }
    }

    private Dashboard findActive(String id) {
        return dashboardRepository.findByIdAndDeletedAtIsNull(parseUuid(id, "dashboardId"))
            .orElseThrow(() -> new NotFoundException("Dashboard not found: " + id));
    }

    private Map<String, Object> defaultTimeRange() {
        return Map.of("type", "relative", "value", "24h");
    }

    private Map<String, Object> defaultLayout() {
        return Map.of("columns", 12, "gap", 16, "widgets", List.of());
    }

    public DashboardResponse create(CreateDashboardRequest request, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID workspaceUuid = parseUuid(request.workspaceId(), "workspaceId");
        permissionService.requirePermission(request.workspaceId(), internalUserId.toString(), "dashboard:create");

        quotaService.checkDashboardQuota(request.workspaceId());

        Map<String, Object> layout = request.layout() != null ? request.layout() : defaultLayout();
        Map<String, Object> timeRange = request.timeRange() != null ? request.timeRange() : defaultTimeRange();
        int autoRefresh = request.autoRefresh() != null ? request.autoRefresh() : 0;

        Dashboard dashboard = new Dashboard(workspaceUuid, request.title(), layout, timeRange, autoRefresh, internalUserId);
        if (request.description() != null) {
            dashboard.setDescription(request.description());
        }
        dashboard = dashboardRepository.save(dashboard);

        eventPublisher.publish(new DashboardCreated(dashboard.getId(), workspaceUuid, internalUserId));

        return DashboardResponse.from(dashboard);
    }

    @Transactional(readOnly = true)
    public List<DashboardResponse> listByWorkspace(String workspaceId, String userId, int limit, int offset) {
        UUID internalUserId = resolveUserId(userId);
        UUID workspaceUuid = parseUuid(workspaceId, "workspaceId");
        permissionService.requirePermission(workspaceId, internalUserId.toString(), "dashboard:read");

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return dashboardRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(workspaceUuid, safeLimit, safeOffset).stream()
            .map(DashboardResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse getById(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActive(id);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "dashboard:read");
        return DashboardResponse.from(dashboard);
    }

    public DashboardResponse update(String id, UpdateDashboardRequest request, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActive(id);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "dashboard:edit");

        if (request.title() != null) {
            dashboard.setTitle(request.title());
        }
        if (request.description() != null) {
            dashboard.setDescription(request.description());
        }
        if (request.layout() != null) {
            dashboard.setLayout(request.layout());
        }
        if (request.timeRange() != null) {
            dashboard.setTimeRange(request.timeRange());
        }
        if (request.autoRefresh() != null) {
            dashboard.setAutoRefresh(request.autoRefresh());
        }
        if (request.isPublic() != null) {
            dashboard.setPublic(request.isPublic());
        }
        dashboard.setUpdatedBy(internalUserId);
        dashboard.setUpdatedAt(Instant.now());
        dashboard = dashboardRepository.save(dashboard);

        eventPublisher.publish(new DashboardUpdated(dashboard.getId(), dashboard.getWorkspaceId(), internalUserId));

        return DashboardResponse.from(dashboard);
    }

    public void delete(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActive(id);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "dashboard:delete");

        List<Widget> widgets = widgetRepository.findByDashboardId(dashboard.getId());
        widgetRepository.deleteAll(widgets);

        dashboard.setDeletedAt(Instant.now());
        dashboard.setUpdatedBy(internalUserId);
        dashboard.setUpdatedAt(Instant.now());
        dashboardRepository.save(dashboard);

        eventPublisher.publish(new DashboardDeleted(dashboard.getId(), dashboard.getWorkspaceId(), internalUserId));
    }

    public DashboardResponse duplicate(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard source = findActive(id);
        permissionService.requirePermission(source.getWorkspaceId().toString(), internalUserId.toString(), "dashboard:create");

        quotaService.checkDashboardQuota(source.getWorkspaceId().toString());

        Dashboard copy = new Dashboard(
            source.getWorkspaceId(),
            source.getTitle() + " (Copy)",
            new HashMap<>(source.getLayout()),
            new HashMap<>(source.getTimeRange()),
            source.getAutoRefresh(),
            internalUserId
        );
        copy.setDescription(source.getDescription());
        copy.setPublic(source.isPublic());
        copy = dashboardRepository.save(copy);

        List<Widget> sourceWidgets = widgetRepository.findByDashboardId(source.getId());
        for (Widget widget : sourceWidgets) {
            Widget copyWidget = new Widget(
                copy.getId(),
                widget.getType(),
                widget.getTitle(),
                widget.getDataSourceId(),
                new HashMap<>(widget.getQuery()),
                new HashMap<>(widget.getOptions()),
                new HashMap<>(widget.getPosition())
            );
            widgetRepository.save(copyWidget);
        }

        eventPublisher.publish(new DashboardCreated(copy.getId(), copy.getWorkspaceId(), internalUserId));

        return DashboardResponse.from(copy);
    }

    @Transactional(readOnly = true)
    public List<WidgetResponse> listWidgets(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActive(id);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "dashboard:read");
        return widgetRepository.findByDashboardId(dashboard.getId()).stream()
            .map(WidgetResponse::from)
            .toList();
    }
}
