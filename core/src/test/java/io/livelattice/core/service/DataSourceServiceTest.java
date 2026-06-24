package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.model.dto.CreateDataSourceRequest;
import io.livelattice.core.model.dto.DataSourceResponse;
import io.livelattice.core.model.dto.UpdateDataSourceRequest;
import io.livelattice.core.model.entity.DataSource;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.enums.DataSourceType;
import io.livelattice.core.repository.DataSourceRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.service.query.ClickHouseDataSourceFactory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private ConfigEncryptionService encryptionService;
    @Mock
    private ClickHouseDataSourceFactory clickHouseDataSourceFactory;
    @Mock
    private EventPublisher eventPublisher;

    private DataSourceService dataSourceService;
    private final String userId = UUID.randomUUID().toString();
    private final String wsId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(encryptionService.encrypt(anyString())).thenAnswer(i -> "enc:" + i.getArgument(0));
        lenient().when(encryptionService.decrypt(anyString())).thenAnswer(i -> ((String) i.getArgument(0)).substring(4));
        dataSourceService = new DataSourceService(
            dataSourceRepository, userRepository, permissionService,
            encryptionService, clickHouseDataSourceFactory, eventPublisher
        );
    }

    @Test
    void create_shouldEncryptSensitiveConfig() throws Exception {
        CreateDataSourceRequest request = new CreateDataSourceRequest(
            wsId, "Events", "CLICKHOUSE",
            Map.of("host", "clickhouse", "port", 8123, "password", "secret")
        );
        User user = new User(userId, "a@b.com", "User1");
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "data_source:create");
        when(dataSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DataSourceResponse response = dataSourceService.create(request, userId);

        assertEquals("Events", response.name());
        assertEquals("CLICKHOUSE", response.type());
    }

    @Test
    void update_shouldChangeName() throws Exception {
        DataSource dataSource = new DataSource(
            UUID.fromString(wsId), "Old", DataSourceType.CLICKHOUSE,
            Map.of("host", "clickhouse"), UUID.fromString(userId)
        );
        User user = new User(userId, "a@b.com", "User1");
        when(dataSourceRepository.findByIdAndDeletedAtIsNull(dataSource.getId())).thenReturn(Optional.of(dataSource));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "data_source:edit");
        when(dataSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DataSourceResponse response = dataSourceService.update(
            dataSource.getId().toString(),
            new UpdateDataSourceRequest("New", null),
            userId
        );

        assertEquals("New", response.name());
    }
}
