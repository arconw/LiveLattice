package io.livelattice.core.service;

import io.livelattice.core.config.AuthProperties;
import io.livelattice.core.event.ApiKeyCreated;
import io.livelattice.core.event.ApiKeyRevoked;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.exception.UnauthorizedException;
import io.livelattice.core.model.dto.ApiKeyCreatedResponse;
import io.livelattice.core.model.dto.ApiKeyMetadataResponse;
import io.livelattice.core.model.dto.CreateApiKeyRequest;
import io.livelattice.core.model.entity.ApiKey;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.repository.ApiKeyRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApiKeyService {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final ApiKeyRepository apiKeyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PermissionService permissionService;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final EventPublisher eventPublisher;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    @Autowired
    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         WorkspaceRepository workspaceRepository,
                         UserRepository userRepository,
                         UserService userService,
                         PermissionService permissionService,
                         StringRedisTemplate redisTemplate,
                         AuthProperties authProperties,
                         EventPublisher eventPublisher) {
        this(apiKeyRepository, workspaceRepository, userRepository, userService, permissionService, redisTemplate, authProperties, eventPublisher, new BCryptPasswordEncoder(12), new SecureRandom());
    }

    ApiKeyService(ApiKeyRepository apiKeyRepository,
                  WorkspaceRepository workspaceRepository,
                  UserRepository userRepository,
                  UserService userService,
                  PermissionService permissionService,
                  StringRedisTemplate redisTemplate,
                  AuthProperties authProperties,
                  EventPublisher eventPublisher,
                  BCryptPasswordEncoder passwordEncoder,
                  SecureRandom secureRandom) {
        this.apiKeyRepository = apiKeyRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.permissionService = permissionService;
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = secureRandom;
    }

    public ApiKeyCreatedResponse create(CreateApiKeyRequest request, String creatorSubject) {
        UUID workspaceId = UUID.fromString(request.workspaceId());
        if (!workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId)) {
            throw new NotFoundException("Workspace not found: " + request.workspaceId());
        }
        User creator = userService.requireBySubject(creatorSubject);
        permissionService.requirePermission(request.workspaceId(), creator.getId().toString(), "api_key:create");
        ApiKey apiKey = new ApiKey(workspaceId, creator.getId(), request.name(), "pending", request.permissions(), request.expiresAt());
        String token = tokenFor(apiKey.getId());
        apiKey.setTokenHash(passwordEncoder.encode(token));
        apiKey = apiKeyRepository.save(apiKey);

        eventPublisher.publish(new ApiKeyCreated(apiKey.getId(), workspaceId, creator.getId()));

        return ApiKeyCreatedResponse.from(apiKey, token);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyMetadataResponse> list(String workspaceId, String requesterSubject) {
        User requester = userService.requireBySubject(requesterSubject);
        permissionService.requirePermission(workspaceId, requester.getId().toString(), "api_key:read");
        return apiKeyRepository.findByWorkspaceIdOrderByCreatedAtDesc(UUID.fromString(workspaceId)).stream()
            .map(ApiKeyMetadataResponse::from)
            .toList();
    }

    public void revoke(String workspaceId, String keyId, String requesterSubject) {
        User requester = userService.requireBySubject(requesterSubject);
        permissionService.requirePermission(workspaceId, requester.getId().toString(), "api_key:revoke");
        ApiKey apiKey = apiKeyRepository.findById(UUID.fromString(keyId))
            .orElseThrow(() -> new NotFoundException("API key not found: " + keyId));
        if (!apiKey.getWorkspaceId().equals(UUID.fromString(workspaceId))) {
            throw new NotFoundException("API key not found: " + keyId);
        }
        apiKey.setStatus(REVOKED);
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
        evict(keyId);

        eventPublisher.publish(new ApiKeyRevoked(apiKey.getId(), UUID.fromString(workspaceId), requester.getId()));
    }

    public ApiKeyValidation validate(String token) {
        String keyId = keyIdFromToken(token);
        ApiKey apiKey = apiKeyRepository.findById(UUID.fromString(keyId))
            .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        if (!ACTIVE.equals(apiKey.getStatus()) || isExpired(apiKey)) {
            throw new UnauthorizedException("Invalid API key");
        }
        ApiKeyValidation cached = cached(keyId, token);
        if (cached != null) {
            return cached;
        }
        if (!passwordEncoder.matches(token, apiKey.getTokenHash())) {
            throw new UnauthorizedException("Invalid API key");
        }
        User creator = userRepository.findById(apiKey.getCreatorId())
            .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        apiKey.setLastUsedAt(Instant.now());
        apiKeyRepository.save(apiKey);
        ApiKeyValidation validation = new ApiKeyValidation(
            apiKey.getId().toString(),
            apiKey.getWorkspaceId().toString(),
            creator.getExternalSubject(),
            creator.getEmail(),
            creator.getDisplayName(),
            apiKey.permissionList()
        );
        cache(validation, token);
        return validation;
    }

    private String tokenFor(UUID keyId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "ll." + shortKeyId(keyId) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String keyIdFromToken(String token) {
        if (token == null || !token.startsWith("ll.")) {
            throw new UnauthorizedException("Invalid API key");
        }
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid API key");
        }
        return longKeyId(parts[1]).toString();
    }

    private String shortKeyId(UUID keyId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(keyId.getMostSignificantBits());
        buffer.putLong(keyId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private UUID longKeyId(String value) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length != 16) {
            throw new UnauthorizedException("Invalid API key");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private boolean isExpired(ApiKey apiKey) {
        return apiKey.getExpiresAt() != null && !apiKey.getExpiresAt().isAfter(Instant.now());
    }

    private ApiKeyValidation cached(String keyId, String token) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey(keyId, token));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split("\\|", 6);
            if (parts.length != 6) {
                return null;
            }
            return new ApiKeyValidation(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5].isBlank() ? List.of() : List.of(parts[5].split(",")));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void cache(ApiKeyValidation validation, String token) {
        try {
            String raw = String.join("|",
                validation.apiKeyId(),
                validation.workspaceId(),
                validation.userSubject(),
                validation.userEmail(),
                validation.userDisplayName(),
                String.join(",", validation.permissions())
            );
            redisTemplate.opsForValue().set(cacheKey(validation.apiKeyId(), token), raw, Duration.ofSeconds(authProperties.getApiKeyCacheTtlSeconds()));
        } catch (RuntimeException ex) {}
    }

    private void evict(String keyId) {
        try {
            redisTemplate.delete(cacheKey(keyId));
        } catch (RuntimeException ex) {}
    }

    private String cacheKey(String keyId) {
        return "auth:api-key:" + keyId;
    }

    private String cacheKey(String keyId, String token) {
        return cacheKey(keyId) + ":" + tokenFingerprint(token);
    }

    private String tokenFingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
