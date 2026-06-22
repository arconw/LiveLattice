package io.livelattice.core.controller;

import io.livelattice.core.model.dto.DashboardDataResponse;
import io.livelattice.core.service.WidgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboards/{id}/data")
public class DashboardDataController {

    private final WidgetService widgetService;

    public DashboardDataController(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @GetMapping
    public ResponseEntity<DashboardDataResponse> data(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(widgetService.loadDashboardData(id, userId));
    }
}
