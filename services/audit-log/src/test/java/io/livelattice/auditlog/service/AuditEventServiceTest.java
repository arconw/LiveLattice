package io.livelattice.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.dto.AuditEventResponse;
import io.livelattice.auditlog.dto.PagedAuditEventsResponse;
import io.livelattice.auditlog.dto.VerifyResponse;
import io.livelattice.auditlog.model.AuditEventEntity;
import io.livelattice.auditlog.repository.AuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditEventServiceTest {

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditArchiveReader archiveReader = mock(AuditArchiveReader.class);
    private final HashChainService hashChainService = new HashChainService(new ObjectMapper());
    private final AuditEventService service = new AuditEventService(repository, hashChainService, new ObjectMapper(), jdbcTemplate, archiveReader);

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> null).when(archiveReader).readArchivedEvents(anyInt(), any());
        lenient().when(archiveReader.hasArchivedEvents()).thenReturn(false);
    }

    @Test
    void queryBuildsSpecificationFromFilters() {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId("e1");
        entity.setWorkspaceId("ws-1");
        entity.setAction("canvas.create");
        entity.setActorId("a1");
        entity.setTargetType("canvas");
        entity.setTargetId("t1");
        entity.setChanges("{}");
        entity.setMetadata("{}");
        entity.setPreviousHash(AuditEventEntity.genesisHash());
        entity.setHash("hash");
        entity.setOccurredAt(Instant.now());
        Page<AuditEventEntity> page = new PageImpl<>(List.of(entity));
        when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        PagedAuditEventsResponse response = service.query(new AuditEventService.AuditQuery("ws-1", "canvas.create", "a1", "canvas", "t1", Instant.EPOCH, Instant.now()), PageRequest.of(0, 10));
        org.junit.jupiter.api.Assertions.assertEquals(1, response.totalElements());
        AuditEventResponse first = response.events().get(0);
        org.junit.jupiter.api.Assertions.assertEquals("canvas.create", first.action());
        org.junit.jupiter.api.Assertions.assertEquals("ws-1", first.workspaceId());
    }

    @Test
    void verifyDetectsBrokenHashChain() {
        AuditEventEntity first = entity("e1", AuditEventEntity.genesisHash(), computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        AuditEventEntity second = entity("e2", "wrong-prev", "hash2");
        whenHotEvents(List.of(first, second));
        VerifyResponse response = service.verify(1000);
        assertFalse(response.valid());
        assertEquals("e2", response.firstInvalidId());
        assertNotNull(response.firstInvalidHash());
    }

    @Test
    void verifySucceedsForIntactChain() {
        AuditEventEntity first = entity("e1", AuditEventEntity.genesisHash(), computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        whenHotEvents(List.of(first));
        VerifyResponse response = service.verify(1000);
        assertTrue(response.valid());
        assertEquals(1, response.checkedCount());
    }

    @Test
    void verifySucceedsAcrossArchivedAndHotEvents() {
        ChainEvent archived = new ChainEvent("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash(),
            computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        AuditEventEntity hot = entity("e2", archived.hash(), computeHash("e2", "canvas.create", "t1", "{}", archived.hash()));
        whenArchivedEvents(List.of(archived));
        whenHotEvents(List.of(hot));
        VerifyResponse response = service.verify(1000);
        assertTrue(response.valid());
        assertEquals(2, response.checkedCount());
    }

    @Test
    void verifyIgnoresStaleArchiveBranchWhenHotChainIsComplete() {
        ChainEvent stale = new ChainEvent("stale", "workspace.create", "t1", "{}", AuditEventEntity.genesisHash(), "bad-hash");
        AuditEventEntity first = entity("e1", AuditEventEntity.genesisHash(), computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        AuditEventEntity second = entity("e2", first.getHash(), computeHash("e2", "canvas.create", "t1", "{}", first.getHash()));
        whenArchivedEvents(List.of(stale));
        whenHotEvents(List.of(first, second));
        VerifyResponse response = service.verify(1000);
        assertTrue(response.valid());
        assertEquals(2, response.checkedCount());
    }

    @Test
    void ingestContinuesFromArchivedTailWhenPartitionsWereDropped() {
        ChainEvent archived = new ChainEvent("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash(),
            computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        AuditEventEntity entity = entity("e2", "", "");
        when(archiveReader.hasArchivedEvents()).thenReturn(true);
        whenArchivedEvents(List.of(archived));
        whenHotEvents(List.of());
        when(repository.save(any(AuditEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditEventEntity saved = service.ingest(entity);
        assertEquals(archived.hash(), saved.getPreviousHash());
        assertTrue(saved.getHash().length() > 0);
    }

    @Test
    void verifyUsesRequestedBatchSizeForHotRowsAndArchives() {
        AuditEventEntity first = entity("e1", AuditEventEntity.genesisHash(), computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        AuditEventEntity second = entity("e2", first.getHash(), computeHash("e2", "canvas.create", "t1", "{}", first.getHash()));
        AuditEventEntity third = entity("e3", second.getHash(), computeHash("e3", "canvas.create", "t1", "{}", second.getHash()));
        whenHotEvents(List.of(first, second, third));

        VerifyResponse response = service.verify(2);

        assertTrue(response.valid());
        assertEquals(3, response.checkedCount());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).findAllByOrderByIngestedAtAscIdAsc(captor.capture());
        assertEquals(2, captor.getAllValues().get(0).getPageSize());
        assertEquals(0, captor.getAllValues().get(0).getPageNumber());
        assertEquals(2, captor.getAllValues().get(1).getPageSize());
        assertEquals(1, captor.getAllValues().get(1).getPageNumber());
        verify(archiveReader).readArchivedEvents(eq(2), any());
    }

    @Test
    void findByIdReturnsMappedEvent() {
        AuditEventEntity entity = entity("e1", AuditEventEntity.genesisHash(), computeHash("e1", "canvas.create", "t1", "{}", AuditEventEntity.genesisHash()));
        entity.setWorkspaceId("ws-1");
        entity.setAction("workspace.create");
        when(repository.findById("e1")).thenReturn(Optional.of(entity));
        Optional<AuditEventResponse> response = service.findById("e1");
        assertTrue(response.isPresent());
        assertEquals("workspace.create", response.get().action());
    }

    @Test
    void ingestDefaultsEventTimeAndEnsuresPartition() {
        AuditEventEntity entity = entity("e1", AuditEventEntity.genesisHash(), "");
        entity.setOccurredAt(null);
        when(repository.findTopByOrderByIngestedAtDescIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditEventEntity saved = service.ingest(entity);
        assertNotNull(saved.getOccurredAt());
        assertNotNull(saved.getHash());
        verify(jdbcTemplate).queryForObject(org.mockito.ArgumentMatchers.eq("SELECT ensure_audit_partition(?)"), org.mockito.ArgumentMatchers.eq(String.class), any(java.sql.Date.class));
    }

    private void whenArchivedEvents(List<ChainEvent> events) {
        doAnswer(invocation -> {
            Consumer<List<ChainEvent>> consumer = invocation.getArgument(1);
            if (!events.isEmpty()) {
                consumer.accept(events);
            }
            return null;
        }).when(archiveReader).readArchivedEvents(anyInt(), any());
    }

    private void whenHotEvents(List<AuditEventEntity> events) {
        when(repository.findAllByOrderByIngestedAtAscIdAsc(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            int start = Math.toIntExact(pageable.getOffset());
            int end = Math.min(start + pageable.getPageSize(), events.size());
            List<AuditEventEntity> content = start >= events.size() ? List.of() : events.subList(start, end);
            return new PageImpl<>(content, pageable, events.size());
        });
    }

    private String computeHash(String id, String action, String targetId, String changes, String previousHash) {
        return hashChainService.recomputeHash(id, action, targetId, changes, previousHash);
    }

    private AuditEventEntity entity(String id, String previousHash, String hash) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId(id);
        entity.setWorkspaceId("ws-1");
        entity.setActorId("a1");
        entity.setAction("canvas.create");
        entity.setTargetType("canvas");
        entity.setTargetId("t1");
        entity.setChanges("{}");
        entity.setMetadata("{}");
        entity.setPreviousHash(previousHash);
        entity.setHash(hash);
        entity.setOccurredAt(Instant.now());
        return entity;
    }
}
