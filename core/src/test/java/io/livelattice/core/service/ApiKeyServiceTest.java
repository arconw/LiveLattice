package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.livelattice.core.config.AuthProperties;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.UnauthorizedException;
import io.livelattice.core.model.dto.ApiKeyCreatedResponse;
import io.livelattice.core.model.dto.CreateApiKeyRequest;
import io.livelattice.core.model.entity.ApiKey;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.repository.ApiKeyRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EventPublisher eventPublisher;

    private final UUID workspaceId = UUID.randomUUID();
    private final User user = new User("subject-1", "owner@example.com", "Owner");
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        apiKeyService = new ApiKeyService(
            apiKeyRepository,
            workspaceRepository,
            userRepository,
            userService,
            permissionService,
            redisTemplate,
            properties,
            eventPublisher,
            new BCryptPasswordEncoder(4),
            new SecureRandom()
        );
    }

    @Test
    void create_shouldReturnPlaintextTokenOnceAndPersistHashOnly() {
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId)).thenReturn(true);
        when(userService.requireBySubject("subject-1")).thenReturn(user);
        doNothing().when(permissionService).requirePermission(workspaceId.toString(), user.getId().toString(), "api_key:create");
        when(apiKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyCreatedResponse response = apiKeyService.create(
            new CreateApiKeyRequest(workspaceId.toString(), "build key", List.of("workspace:read"), null),
            "subject-1"
        );

        assertTrue(response.token().startsWith("ll."));
        assertTrue(response.token().length() <= 72);
        assertEquals(List.of("workspace:read"), response.permissions());
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void validate_shouldAcceptActiveTokenAndUpdateLastUsedAt() {
        UUID keyId = UUID.randomUUID();
        String token = tokenFor(keyId, "secret");
        ApiKey apiKey = new ApiKey(workspaceId, user.getId(), "build key", new BCryptPasswordEncoder(4).encode(token), List.of("workspace:read"), null);
        apiKey.setId(keyId);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(apiKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyValidation validation = apiKeyService.validate(token);

        assertEquals(keyId.toString(), validation.apiKeyId());
        assertEquals(workspaceId.toString(), validation.workspaceId());
        assertEquals("subject-1", validation.userSubject());
        assertFalse(validation.permissions().isEmpty());
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void validate_shouldRejectWrongSecret() {
        UUID keyId = UUID.randomUUID();
        String token = tokenFor(keyId, "secret");
        ApiKey apiKey = new ApiKey(workspaceId, user.getId(), "build key", new BCryptPasswordEncoder(4).encode(token), List.of("workspace:read"), null);
        apiKey.setId(keyId);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));

        assertThrows(UnauthorizedException.class, () -> apiKeyService.validate(tokenFor(keyId, "other")));
    }

    @Test
    void validate_shouldNotTrustKeyIdOnlyCacheForWrongSecret() {
        UUID keyId = UUID.randomUUID();
        String token = tokenFor(keyId, "secret");
        ApiKey apiKey = new ApiKey(workspaceId, user.getId(), "build key", new BCryptPasswordEncoder(4).encode(token), List.of("workspace:read"), null);
        apiKey.setId(keyId);
        lenient().when(valueOperations.get("auth:api-key:" + keyId)).thenReturn(keyId + "|" + workspaceId + "|subject-1|owner@example.com|Owner|workspace:read");
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));

        assertThrows(UnauthorizedException.class, () -> apiKeyService.validate(tokenFor(keyId, "other")));
    }

    @Test
    void revoke_shouldMarkKeyRevoked() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = new ApiKey(workspaceId, user.getId(), "build key", "hash", List.of("workspace:read"), null);
        apiKey.setId(keyId);
        when(userService.requireBySubject("subject-1")).thenReturn(user);
        doNothing().when(permissionService).requirePermission(workspaceId.toString(), user.getId().toString(), "api_key:revoke");
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));

        apiKeyService.revoke(workspaceId.toString(), keyId.toString(), "subject-1");

        assertEquals("REVOKED", apiKey.getStatus());
        assertNotEquals(null, apiKey.getRevokedAt());
        verify(apiKeyRepository).save(apiKey);
    }

    private String tokenFor(UUID keyId, String secret) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(keyId.getMostSignificantBits());
        buffer.putLong(keyId.getLeastSignificantBits());
        return "ll." + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array()) + "." + secret;
    }

    @Test
    void validate_shouldRejectExpiredToken() {
        UUID keyId = UUID.randomUUID();
        String token = tokenFor(keyId, "secret");
        ApiKey apiKey = new ApiKey(workspaceId, user.getId(), "build key", new BCryptPasswordEncoder(4).encode(token), List.of("workspace:read"), Instant.now().minusSeconds(1));
        apiKey.setId(keyId);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));

        assertThrows(UnauthorizedException.class, () -> apiKeyService.validate(token));
    }
}
