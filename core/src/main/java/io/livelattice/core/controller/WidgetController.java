package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CreateWidgetRequest;
import io.livelattice.core.model.dto.DashboardDataResponse;
import io.livelattice.core.model.dto.UpdateWidgetRequest;
import io.livelattice.core.model.dto.WidgetResponse;
import io.livelattice.core.service.WidgetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboards/{dashboardId}/widgets")
public class WidgetController {

    private final WidgetService widgetService;

    public WidgetController(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @GetMapping
    public ResponseEntity<List<WidgetResponse>> list(
            @PathVariable String dashboardId,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(widgetService.listByDashboard(dashboardId, userId));
    }

    @PostMapping
    public ResponseEntity<WidgetResponse> create(
            @PathVariable String dashboardId,
            @Valid @RequestBody CreateWidgetRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(widgetService.create(dashboardId, request, userId));
    }

    @GetMapping("/{widgetId}")
    public ResponseEntity<WidgetResponse> get(
            @PathVariable String dashboardId,
            @PathVariable String widgetId,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(widgetService.getById(dashboardId, widgetId, userId));
    }

    @PatchMapping("/{widgetId}")
    public ResponseEntity<WidgetResponse> update(
            @PathVariable String dashboardId,
            @PathVariable String widgetId,
            @Valid @RequestBody UpdateWidgetRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(widgetService.update(dashboardId, widgetId, request, userId));
    }

    @DeleteMapping("/{widgetId}")
    public ResponseEntity<Void> delete(
            @PathVariable String dashboardId,
            @PathVariable String widgetId,
            @RequestHeader("x-user-id") String userId) {
        widgetService.delete(dashboardId, widgetId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{widgetId}/data")
    public ResponseEntity<DashboardDataResponse> widgetData(
            @PathVariable String dashboardId,
            @PathVariable String widgetId,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(widgetService.loadWidgetData(dashboardId, widgetId, userId));
    }
}
