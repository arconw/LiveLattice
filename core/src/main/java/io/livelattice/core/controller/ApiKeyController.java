package io.livelattice.core.controller;

import io.livelattice.core.model.dto.ApiKeyCreatedResponse;
import io.livelattice.core.model.dto.ApiKeyMetadataResponse;
import io.livelattice.core.model.dto.CreateApiKeyRequest;
import io.livelattice.core.service.ApiKeyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> create(
            @Valid @RequestBody CreateApiKeyRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.create(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyMetadataResponse>> list(
            @RequestParam String workspaceId,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(apiKeyService.list(workspaceId, userId));
    }

    @DeleteMapping("/{workspaceId}/{keyId}")
    public ResponseEntity<Void> revoke(
            @PathVariable String workspaceId,
            @PathVariable String keyId,
            @RequestHeader("x-user-id") String userId) {
        apiKeyService.revoke(workspaceId, keyId, userId);
        return ResponseEntity.noContent().build();
    }
}
