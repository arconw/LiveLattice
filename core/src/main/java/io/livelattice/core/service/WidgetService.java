package io.livelattice.core.service;

import io.livelattice.core.exception.BadRequestException;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.dto.CreateWidgetRequest;
import io.livelattice.core.model.dto.DashboardDataResponse;
import io.livelattice.core.model.dto.UpdateWidgetRequest;
import io.livelattice.core.model.dto.WidgetResponse;
import io.livelattice.core.model.entity.Dashboard;
import io.livelattice.core.model.entity.DataSource;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Widget;
import io.livelattice.core.model.enums.DashboardWidgetType;
import io.livelattice.core.repository.DashboardRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WidgetRepository;
import io.livelattice.core.service.query.QueryEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WidgetService {

    private final WidgetRepository widgetRepository;
    private final DashboardRepository dashboardRepository;
    private final UserRepository userRepository;
    private final DataSourceService dataSourceService;
    private final QueryEngine queryEngine;
    private final PermissionService permissionService;

    public WidgetService(WidgetRepository widgetRepository,
                         DashboardRepository dashboardRepository,
                         UserRepository userRepository,
                         DataSourceService dataSourceService,
                         QueryEngine queryEngine,
                         PermissionService permissionService) {
        this.widgetRepository = widgetRepository;
        this.dashboardRepository = dashboardRepository;
        this.userRepository = userRepository;
        this.dataSourceService = dataSourceService;
        this.queryEngine = queryEngine;
        this.permissionService = permissionService;
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

    private Dashboard findActiveDashboard(String id) {
        return dashboardRepository.findByIdAndDeletedAtIsNull(parseUuid(id, "dashboardId"))
            .orElseThrow(() -> new NotFoundException("Dashboard not found: " + id));
    }

    private Widget findWidget(String dashboardId, String widgetId) {
        Dashboard dashboard = findActiveDashboard(dashboardId);
        Widget widget = widgetRepository.findById(parseUuid(widgetId, "widgetId"))
            .orElseThrow(() -> new NotFoundException("Widget not found: " + widgetId));
        if (!widget.getDashboardId().equals(dashboard.getId())) {
            throw new ForbiddenException("Widget does not belong to dashboard");
        }
        return widget;
    }

    private Map<String, Object> defaultOptions() {
        return new HashMap<>();
    }

    private Map<String, Object> defaultPosition() {
        return Map.of("x", 0, "y", 0, "w", 4, "h", 4);
    }

    public WidgetResponse create(String dashboardId, CreateWidgetRequest request, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "widget:create");

        DashboardWidgetType type = DashboardWidgetType.valueOf(request.type());
        UUID dataSourceId = null;
        if (type != DashboardWidgetType.MARKDOWN) {
            if (request.dataSourceId() == null || request.dataSourceId().isBlank()) {
                throw new BadRequestException("Data source is required for widget type " + type);
            }
            dataSourceId = parseUuid(request.dataSourceId(), "dataSourceId");
            DataSource dataSource = dataSourceService.findActiveEntity(request.dataSourceId());
            if (!dataSource.getWorkspaceId().equals(dashboard.getWorkspaceId())) {
                throw new ForbiddenException("Data source does not belong to workspace");
            }
        }

        Map<String, Object> options = request.options() != null ? request.options() : defaultOptions();
        Map<String, Object> position = request.position() != null ? request.position() : defaultPosition();

        Widget widget = new Widget(
            dashboard.getId(),
            type,
            request.title(),
            dataSourceId,
            request.query(),
            options,
            position
        );
        widget = widgetRepository.save(widget);
        return WidgetResponse.from(widget);
    }

    @Transactional(readOnly = true)
    public List<WidgetResponse> listByDashboard(String dashboardId, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "widget:read");
        return widgetRepository.findByDashboardId(dashboard.getId()).stream()
            .map(WidgetResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public WidgetResponse getById(String dashboardId, String widgetId, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Widget widget = findWidget(dashboardId, widgetId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "widget:read");
        return WidgetResponse.from(widget);
    }

    public WidgetResponse update(String dashboardId, String widgetId, UpdateWidgetRequest request, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Widget widget = findWidget(dashboardId, widgetId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "widget:edit");

        DashboardWidgetType nextType = request.type() != null ? DashboardWidgetType.valueOf(request.type()) : widget.getType();
        UUID nextDataSourceId = widget.getDataSourceId();
        if (request.title() != null) {
            widget.setTitle(request.title());
        }
        if (request.dataSourceId() != null) {
            if (request.dataSourceId().isBlank()) {
                nextDataSourceId = null;
            } else {
                DataSource dataSource = dataSourceService.findActiveEntity(request.dataSourceId());
                if (!dataSource.getWorkspaceId().equals(dashboard.getWorkspaceId())) {
                    throw new ForbiddenException("Data source does not belong to workspace");
                }
                nextDataSourceId = dataSource.getId();
            }
        }
        if (nextType != DashboardWidgetType.MARKDOWN && nextDataSourceId == null) {
            throw new BadRequestException("Data source is required for widget type " + nextType);
        }
        if (nextType == DashboardWidgetType.MARKDOWN) {
            nextDataSourceId = null;
        }
        widget.setType(nextType);
        widget.setDataSourceId(nextDataSourceId);
        if (request.query() != null) {
            widget.setQuery(request.query());
        }
        if (request.options() != null) {
            widget.setOptions(request.options());
        }
        if (request.position() != null) {
            widget.setPosition(request.position());
        }
        widget.setUpdatedAt(Instant.now());
        widget = widgetRepository.save(widget);

        queryEngine.invalidate(widget.getId().toString());
        return WidgetResponse.from(widget);
    }

    public void delete(String dashboardId, String widgetId, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Widget widget = findWidget(dashboardId, widgetId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "widget:delete");
        widgetRepository.delete(widget);
        queryEngine.invalidate(widget.getId().toString());
    }

    @Transactional(readOnly = true)
    public DashboardDataResponse loadDashboardData(String dashboardId, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "dashboard:read");

        List<Widget> widgets = widgetRepository.findByDashboardId(dashboard.getId());
        List<CompletableFuture<DashboardDataResponse.WidgetData>> futures = new ArrayList<>();

        for (Widget widget : widgets) {
            if (widget.getType() == DashboardWidgetType.MARKDOWN) {
                Map<String, Object> data = new HashMap<>();
                data.put("columns", List.of());
                data.put("rows", List.of());
                data.put("meta", Map.of("totalRows", 0, "executedAt", Instant.now().toString()));
                futures.add(CompletableFuture.completedFuture(
                    new DashboardDataResponse.WidgetData(widget.getId().toString(), data, null)));
                continue;
            }
            futures.add(queryEngine.executeWidget(
                widget.getId().toString(),
                widget.getQuery(),
                resolveDataSourceConfig(widget),
                dashboard.getTimeRange(),
                dashboard.getWorkspaceId().toString()
            ).thenApply(data -> new DashboardDataResponse.WidgetData(widget.getId().toString(), data, null)));
        }

        List<DashboardDataResponse.WidgetData> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        return new DashboardDataResponse(dashboardId, results);
    }

    @Transactional(readOnly = true)
    public DashboardDataResponse loadWidgetData(String dashboardId, String widgetId, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Widget widget = findWidget(dashboardId, widgetId);
        Dashboard dashboard = findActiveDashboard(dashboardId);
        permissionService.requirePermission(dashboard.getWorkspaceId().toString(), internalUserId.toString(), "widget:read");

        if (widget.getType() == DashboardWidgetType.MARKDOWN) {
            Map<String, Object> data = new HashMap<>();
            data.put("columns", List.of());
            data.put("rows", List.of());
            data.put("meta", Map.of("totalRows", 0, "executedAt", Instant.now().toString()));
            return new DashboardDataResponse(dashboardId, List.of(new DashboardDataResponse.WidgetData(widgetId, data, null)));
        }

        Map<String, Object> data = queryEngine.executeWidget(
            widget.getId().toString(),
            widget.getQuery(),
            resolveDataSourceConfig(widget),
            dashboard.getTimeRange(),
            dashboard.getWorkspaceId().toString()
        ).join();
        return new DashboardDataResponse(dashboardId, List.of(new DashboardDataResponse.WidgetData(widgetId, data, null)));
    }

    private Map<String, Object> resolveDataSourceConfig(Widget widget) {
        try {
            return dataSourceService.resolveDecryptedConfig(widget.getDataSourceId().toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve data source config: " + e.getMessage());
        }
    }
}
