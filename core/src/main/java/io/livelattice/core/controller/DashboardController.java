package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CreateDashboardRequest;
import io.livelattice.core.model.dto.DashboardResponse;
import io.livelattice.core.model.dto.UpdateDashboardRequest;
import io.livelattice.core.model.dto.WidgetResponse;
import io.livelattice.core.service.DashboardService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<List<DashboardResponse>> list(
            @RequestParam String workspaceId,
            @RequestHeader("x-user-id") String userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(dashboardService.listByWorkspace(workspaceId, userId, limit, offset));
    }

    @PostMapping
    public ResponseEntity<DashboardResponse> create(
            @Valid @RequestBody CreateDashboardRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboardService.create(request, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DashboardResponse> get(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(dashboardService.getById(id, userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DashboardResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateDashboardRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(dashboardService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        dashboardService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<DashboardResponse> duplicate(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboardService.duplicate(id, userId));
    }

    @GetMapping("/{id}/widgets")
    public ResponseEntity<List<WidgetResponse>> listWidgets(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(dashboardService.listWidgets(id, userId));
    }
}
