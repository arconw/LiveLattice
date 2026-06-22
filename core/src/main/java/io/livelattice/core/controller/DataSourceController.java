package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CreateDataSourceRequest;
import io.livelattice.core.model.dto.DataSourceResponse;
import io.livelattice.core.model.dto.UpdateDataSourceRequest;
import io.livelattice.core.service.DataSourceService;
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
@RequestMapping("/data-sources")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public ResponseEntity<List<DataSourceResponse>> list(
            @RequestParam String workspaceId,
            @RequestHeader("x-user-id") String userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(dataSourceService.listByWorkspace(workspaceId, userId, limit, offset));
    }

    @PostMapping
    public ResponseEntity<DataSourceResponse> create(
            @Valid @RequestBody CreateDataSourceRequest request,
            @RequestHeader("x-user-id") String userId) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(dataSourceService.create(request, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSourceResponse> get(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(dataSourceService.getById(id, userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DataSourceResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateDataSourceRequest request,
            @RequestHeader("x-user-id") String userId) throws Exception {
        return ResponseEntity.ok(dataSourceService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        dataSourceService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Boolean> test(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(dataSourceService.testConnection(id, userId));
    }
}
