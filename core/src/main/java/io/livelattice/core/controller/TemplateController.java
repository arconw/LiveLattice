package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CreateTemplateRequest;
import io.livelattice.core.model.dto.TemplateResponse;
import io.livelattice.core.service.TemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String category,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(templateService.list(workspaceId, category, userId));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody CreateTemplateRequest request,
            @RequestParam(required = false) String workspaceId,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(request, workspaceId, userId));
    }
}
