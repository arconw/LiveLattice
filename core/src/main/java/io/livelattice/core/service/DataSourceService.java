package io.livelattice.core.service;

import io.livelattice.core.exception.BadRequestException;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.dto.CreateDataSourceRequest;
import io.livelattice.core.model.dto.DataSourceResponse;
import io.livelattice.core.model.dto.UpdateDataSourceRequest;
import io.livelattice.core.model.entity.DataSource;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.enums.DataSourceType;
import io.livelattice.core.repository.DataSourceRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.service.query.ClickHouseDataSourceFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DataSourceService {

    private final DataSourceRepository dataSourceRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ConfigEncryptionService encryptionService;
    private final ClickHouseDataSourceFactory clickHouseDataSourceFactory;

    public DataSourceService(DataSourceRepository dataSourceRepository,
                             UserRepository userRepository,
                             PermissionService permissionService,
                             ConfigEncryptionService encryptionService,
                             ClickHouseDataSourceFactory clickHouseDataSourceFactory) {
        this.dataSourceRepository = dataSourceRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.encryptionService = encryptionService;
        this.clickHouseDataSourceFactory = clickHouseDataSourceFactory;
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

    private Map<String, Object> encryptConfig(Map<String, Object> config) throws Exception {
        Map<String, Object> encrypted = new HashMap<>(config);
        for (String key : List.of("password", "token", "secret", "apiKey", "api_key")) {
            if (encrypted.containsKey(key) && encrypted.get(key) instanceof String s) {
                encrypted.put(key, encryptionService.encrypt(s));
            }
        }
        return encrypted;
    }

    private Map<String, Object> decryptConfig(Map<String, Object> config) throws Exception {
        Map<String, Object> decrypted = new HashMap<>(config);
        for (String key : List.of("password", "token", "secret", "apiKey", "api_key")) {
            if (decrypted.containsKey(key) && decrypted.get(key) instanceof String s) {
                decrypted.put(key, encryptionService.decrypt(s));
            }
        }
        return decrypted;
    }

    public DataSourceResponse create(CreateDataSourceRequest request, String userId) throws Exception {
        UUID internalUserId = resolveUserId(userId);
        UUID workspaceUuid = parseUuid(request.workspaceId(), "workspaceId");
        permissionService.requirePermission(request.workspaceId(), internalUserId.toString(), "data_source:create");

        Map<String, Object> encryptedConfig = encryptConfig(request.config());
        DataSource dataSource = new DataSource(
            workspaceUuid,
            request.name(),
            DataSourceType.valueOf(request.type()),
            encryptedConfig,
            internalUserId
        );
        dataSource = dataSourceRepository.save(dataSource);
        return DataSourceResponse.from(dataSource);
    }

    @Transactional(readOnly = true)
    public List<DataSourceResponse> listByWorkspace(String workspaceId, String userId, int limit, int offset) {
        UUID internalUserId = resolveUserId(userId);
        UUID workspaceUuid = parseUuid(workspaceId, "workspaceId");
        permissionService.requirePermission(workspaceId, internalUserId.toString(), "data_source:read");
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return dataSourceRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(workspaceUuid, safeLimit, safeOffset).stream()
            .map(DataSourceResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public DataSourceResponse getById(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        DataSource dataSource = findActive(id);
        permissionService.requirePermission(dataSource.getWorkspaceId().toString(), internalUserId.toString(), "data_source:read");
        return DataSourceResponse.from(dataSource);
    }

    public DataSourceResponse update(String id, UpdateDataSourceRequest request, String userId) throws Exception {
        UUID internalUserId = resolveUserId(userId);
        DataSource dataSource = findActive(id);
        permissionService.requirePermission(dataSource.getWorkspaceId().toString(), internalUserId.toString(), "data_source:edit");

        if (request.name() != null) {
            dataSource.setName(request.name());
        }
        if (request.config() != null) {
            dataSource.setConfig(encryptConfig(request.config()));
        }
        dataSource.setUpdatedBy(internalUserId);
        dataSource.setUpdatedAt(Instant.now());
        dataSource = dataSourceRepository.save(dataSource);
        return DataSourceResponse.from(dataSource);
    }

    public void delete(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        DataSource dataSource = findActive(id);
        permissionService.requirePermission(dataSource.getWorkspaceId().toString(), internalUserId.toString(), "data_source:delete");
        dataSource.setDeletedAt(Instant.now());
        dataSource.setUpdatedBy(internalUserId);
        dataSource.setUpdatedAt(Instant.now());
        dataSourceRepository.save(dataSource);
    }

    public boolean testConnection(String id, String userId) {
        DataSource dataSource = findActive(id);
        UUID internalUserId = resolveUserId(userId);
        permissionService.requirePermission(dataSource.getWorkspaceId().toString(), internalUserId.toString(), "data_source:read");
        try {
            Map<String, Object> config = decryptConfig(dataSource.getConfig());
            config.put("type", dataSource.getType().name());
            if (dataSource.getType() == DataSourceType.CLICKHOUSE) {
                clickHouseDataSourceFactory.create(config).getConnection().close();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private DataSource findActive(String id) {
        return dataSourceRepository.findByIdAndDeletedAtIsNull(parseUuid(id, "dataSourceId"))
            .orElseThrow(() -> new NotFoundException("Data source not found: " + id));
    }

    DataSource findActiveEntity(String id) {
        return findActive(id);
    }

    Map<String, Object> resolveDecryptedConfig(String id) throws Exception {
        DataSource dataSource = findActive(id);
        Map<String, Object> config = decryptConfig(dataSource.getConfig());
        config.put("type", dataSource.getType().name());
        return config;
    }
}
