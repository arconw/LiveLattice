package io.livelattice.importexport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.dto.DashboardExportData;
import io.livelattice.importexport.exception.NotFoundException;
import io.livelattice.importexport.exception.ValidationException;
import io.livelattice.importexport.model.CanvasEntity;
import io.livelattice.importexport.model.CoreCanvasEntity;
import io.livelattice.importexport.model.CoreDashboardEntity;
import io.livelattice.importexport.model.CoreWidgetEntity;
import io.livelattice.importexport.repository.CanvasRepository;
import io.livelattice.importexport.repository.CoreCanvasRepository;
import io.livelattice.importexport.repository.CoreDashboardRepository;
import io.livelattice.importexport.repository.CoreWidgetRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CanvasLookupService {

    private final ObjectMapper objectMapper;
    private final CanvasRepository legacyCanvasRepository;
    private final CoreCanvasRepository coreCanvasRepository;
    private final CoreDashboardRepository coreDashboardRepository;
    private final CoreWidgetRepository coreWidgetRepository;
    private final ImportExportAuthorizationService authorizationService;

    public CanvasLookupService(ObjectMapper objectMapper,
                               CanvasRepository legacyCanvasRepository,
                               CoreCanvasRepository coreCanvasRepository,
                               CoreDashboardRepository coreDashboardRepository,
                               CoreWidgetRepository coreWidgetRepository,
                               ImportExportAuthorizationService authorizationService) {
        this.objectMapper = objectMapper;
        this.legacyCanvasRepository = legacyCanvasRepository;
        this.coreCanvasRepository = coreCanvasRepository;
        this.coreDashboardRepository = coreDashboardRepository;
        this.coreWidgetRepository = coreWidgetRepository;
        this.authorizationService = authorizationService;
    }

    public void requireWorkspacePermission(UUID workspaceId, String userSubject, String permission) {
        authorizationService.requirePermission(workspaceId, userSubject, permission);
    }

    public Map<String, Object> load(UUID canvasId, String userSubject) {
        return coreCanvasRepository.findByIdAndDeletedAtIsNull(canvasId)
            .map(entity -> {
                authorizationService.requirePermission(entity.getWorkspaceId(), userSubject, "canvas:read");
                return toCanvasMap(entity);
            })
            .orElseGet(() -> loadLegacy(canvasId, userSubject));
    }

    public UUID save(Map<String, Object> canvas, UUID workspaceId, String userSubject) {
        UUID canvasId = canvasId(canvas);
        UUID userId = authorizationService.resolveUserId(userSubject);
        Instant now = Instant.now();
        var active = coreCanvasRepository.findByIdAndDeletedAtIsNull(canvasId);
        if (active.isEmpty() && coreCanvasRepository.existsById(canvasId)) {
            throw new ValidationException("Canvas id is unavailable: " + canvasId);
        }
        boolean existing = active.isPresent();
        CoreCanvasEntity entity = active.orElseGet(CoreCanvasEntity::new);
        if (existing) {
            if (!entity.getWorkspaceId().equals(workspaceId)) {
                throw new ValidationException("Canvas does not belong to workspace: " + canvasId);
            }
            authorizationService.requirePermission(entity.getWorkspaceId(), userId, "canvas:edit");
        } else {
            authorizationService.requirePermission(workspaceId, userId, "canvas:create");
        }
        entity.setId(canvasId);
        entity.setWorkspaceId(workspaceId);
        entity.setTitle(title(canvas));
        entity.setContent(coreContent(canvas));
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(null);
        if (!existing) {
            entity.setVersion(number(canvas.get("version"), 1L));
            entity.setLockVersion(0);
            entity.setOperationCountSinceSnapshot(0);
            entity.setCreatedBy(userId);
            entity.setCreatedAt(now);
        } else {
            entity.setVersion(entity.getVersion() + 1);
            entity.setOperationCountSinceSnapshot(entity.getOperationCountSinceSnapshot() + 1);
        }
        coreCanvasRepository.save(entity);
        canvas.put("id", canvasId.toString());
        canvas.put("workspaceId", workspaceId.toString());
        canvas.put("title", entity.getTitle());
        canvas.put("version", entity.getVersion());
        return canvasId;
    }

    public DashboardExportData loadDashboard(UUID dashboardId, String userSubject) {
        CoreDashboardEntity dashboard = coreDashboardRepository.findByIdAndDeletedAtIsNull(dashboardId)
            .orElseThrow(() -> new NotFoundException("Dashboard not found: " + dashboardId));
        authorizationService.requirePermission(dashboard.getWorkspaceId(), userSubject, "dashboard:read");
        List<CoreWidgetEntity> widgets = coreWidgetRepository.findByDashboardIdOrderByCreatedAtAscIdAsc(dashboardId);
        List<Map<String, Object>> rows = widgets.stream()
            .map(widget -> dashboardRow(dashboard, widget))
            .toList();
        return new DashboardExportData(dashboard.getId().toString(), dashboard.getTitle(), rows);
    }

    private Map<String, Object> loadLegacy(UUID canvasId, String userSubject) {
        CanvasEntity entity = legacyCanvasRepository.findById(canvasId)
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));
        authorizationService.requirePermission(entity.getWorkspaceId(), userSubject, "canvas:read");
        try {
            Map<String, Object> canvas = objectMapper.readValue(entity.getContentJson(), new TypeReference<Map<String, Object>>() {});
            canvas.put("id", entity.getId().toString());
            canvas.put("workspaceId", entity.getWorkspaceId().toString());
            canvas.put("title", entity.getTitle());
            return canvas;
        } catch (Exception e) {
            throw new NotFoundException("Failed to load canvas: " + canvasId);
        }
    }

    private Map<String, Object> toCanvasMap(CoreCanvasEntity entity) {
        Map<String, Object> canvas = new LinkedHashMap<>();
        if (entity.getContent() != null) {
            canvas.putAll(entity.getContent());
        }
        canvas.put("id", entity.getId().toString());
        canvas.put("workspaceId", entity.getWorkspaceId().toString());
        canvas.put("title", entity.getTitle());
        canvas.put("version", entity.getVersion());
        return canvas;
    }

    private UUID canvasId(Map<String, Object> canvas) {
        Object raw = canvas.get("id");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return UUID.randomUUID();
        }
        return UUID.fromString(String.valueOf(raw));
    }

    private String title(Map<String, Object> canvas) {
        Object title = canvas.get("title");
        if (title == null || String.valueOf(title).isBlank()) {
            return "Imported Canvas";
        }
        return String.valueOf(title);
    }

    private Map<String, Object> coreContent(Map<String, Object> canvas) {
        Map<String, Object> content = new LinkedHashMap<>(canvas);
        content.remove("id");
        content.remove("workspaceId");
        content.remove("title");
        content.remove("version");
        return content;
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private Map<String, Object> dashboardRow(CoreDashboardEntity dashboard, CoreWidgetEntity widget) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dashboard_id", dashboard.getId().toString());
        row.put("dashboard_title", dashboard.getTitle());
        row.put("workspace_id", dashboard.getWorkspaceId().toString());
        row.put("widget_id", widget.getId().toString());
        row.put("widget_type", widget.getType());
        row.put("widget_title", widget.getTitle());
        row.put("data_source_id", widget.getDataSourceId() != null ? widget.getDataSourceId().toString() : "");
        row.put("query", json(widget.getQuery()));
        row.put("options", json(widget.getOptions()));
        row.put("position", json(widget.getPosition()));
        return row;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize dashboard field", e);
        }
    }
}
