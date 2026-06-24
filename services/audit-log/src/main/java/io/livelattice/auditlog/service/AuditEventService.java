package io.livelattice.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.dto.AuditEventResponse;
import io.livelattice.auditlog.dto.PagedAuditEventsResponse;
import io.livelattice.auditlog.dto.VerifyResponse;
import io.livelattice.auditlog.model.AuditEventEntity;
import io.livelattice.auditlog.repository.AuditEventRepository;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {

    private static final int DEFAULT_VERIFY_BATCH_SIZE = 1000;

    private final AuditEventRepository repository;
    private final HashChainService hashChainService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditArchiveReader archiveReader;

    public AuditEventService(AuditEventRepository repository,
                             HashChainService hashChainService,
                             ObjectMapper objectMapper,
                             JdbcTemplate jdbcTemplate,
                             AuditArchiveReader archiveReader) {
        this.repository = repository;
        this.hashChainService = hashChainService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.archiveReader = archiveReader;
    }

    @Transactional
    public AuditEventEntity ingest(AuditEventEntity event) {
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        ensurePartition(event.getOccurredAt());
        String previousHash = currentChainTailHash();
        event.setPreviousHash(previousHash);
        event.setHash(hashChainService.computeHash(event));
        return repository.save(event);
    }

    private void ensurePartition(Instant occurredAt) {
        Date monthDate = Date.valueOf(occurredAt.atZone(ZoneOffset.UTC).toLocalDate());
        jdbcTemplate.queryForObject("SELECT ensure_audit_partition(?)", String.class, monthDate);
    }

    @Transactional(readOnly = true)
    public PagedAuditEventsResponse query(AuditQuery query, Pageable pageable) {
        Specification<AuditEventEntity> spec = buildSpecification(query);
        Page<AuditEventEntity> page = repository.findAll(spec, pageable);
        List<AuditEventResponse> events = page.getContent().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return new PagedAuditEventsResponse(
            events,
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber() + 1,
            page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public Optional<AuditEventResponse> findById(String id) {
        return repository.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VerifyResponse verify(int batchSize) {
        List<ChainEvent> all = loadChainEvents(batchSize);
        ChainVerification verification = verifyLinkedChain(all);
        return new VerifyResponse(
            verification.valid(),
            verification.firstInvalidId(),
            verification.firstInvalidHash(),
            verification.checkedCount()
        );
    }

    private String currentChainTailHash() {
        if (!archiveReader.hasArchivedEvents()) {
            return repository.findTopByOrderByIngestedAtDescIdDesc()
                .map(AuditEventEntity::getHash)
                .orElse(AuditEventEntity.genesisHash());
        }
        ChainVerification verification = verifyLinkedChain(loadChainEvents(DEFAULT_VERIFY_BATCH_SIZE));
        if (!verification.valid()) {
            throw new IllegalStateException("Cannot append to invalid audit chain at " + verification.firstInvalidId());
        }
        return verification.tailHash();
    }

    private List<ChainEvent> loadChainEvents(int batchSize) {
        int safeBatchSize = Math.max(1, batchSize);
        List<ChainEvent> all = new ArrayList<>();
        archiveReader.readArchivedEvents(safeBatchSize, all::addAll);
        appendHotChainEvents(safeBatchSize, all);
        return all;
    }

    private void appendHotChainEvents(int batchSize, List<ChainEvent> all) {
        int pageNumber = 0;
        Page<AuditEventEntity> page;
        do {
            page = repository.findAllByOrderByIngestedAtAscIdAsc(PageRequest.of(pageNumber, batchSize));
            page.getContent().stream()
                .map(this::toChainEvent)
                .forEach(all::add);
            pageNumber++;
        } while (page.hasNext());
    }

    private ChainVerification verifyLinkedChain(List<ChainEvent> events) {
        Map<String, List<ChainEvent>> byPreviousHash = new HashMap<>();
        Set<String> hotIds = new HashSet<>();
        for (ChainEvent event : events) {
            byPreviousHash.computeIfAbsent(event.previousHash(), ignored -> new ArrayList<>()).add(event);
            if (event.hot()) {
                hotIds.add(event.id());
            }
        }

        ChainPath path = bestPath(AuditEventEntity.genesisHash(), byPreviousHash, new HashSet<>());
        if (!path.valid()) {
            return new ChainVerification(false, path.firstInvalidId(), path.firstInvalidHash(), path.checkedCount(), path.tailHash());
        }
        if (!path.hotIds().containsAll(hotIds)) {
            Optional<ChainEvent> orphan = events.stream()
                .filter(candidate -> candidate.hot() && !path.hotIds().contains(candidate.id()))
                .findFirst();
            if (orphan.isPresent()) {
                return new ChainVerification(false, orphan.get().id(), orphan.get().hash(), path.checkedCount() + 1, path.tailHash());
            }
        }
        return new ChainVerification(true, null, null, path.checkedCount(), path.tailHash());
    }

    private ChainPath bestPath(String previousHash, Map<String, List<ChainEvent>> byPreviousHash, Set<String> visitedHashes) {
        List<ChainEvent> children = byPreviousHash.getOrDefault(previousHash, List.of());
        if (children.isEmpty()) {
            return new ChainPath(true, null, null, 0, previousHash, Collections.emptySet());
        }

        ChainPath best = null;
        ChainPath firstInvalid = null;
        for (ChainEvent child : children) {
            ChainPath candidate = pathFromChild(child, previousHash, byPreviousHash, visitedHashes);
            if (candidate.valid()) {
                if (best == null || isBetter(candidate, best)) {
                    best = candidate;
                }
            } else if (firstInvalid == null) {
                firstInvalid = candidate;
            }
        }
        return best == null ? firstInvalid : best;
    }

    private ChainPath pathFromChild(ChainEvent event, String previousHash, Map<String, List<ChainEvent>> byPreviousHash, Set<String> visitedHashes) {
        if (visitedHashes.contains(event.hash())) {
            return new ChainPath(false, event.id(), event.hash(), 1, previousHash, Collections.emptySet());
        }

        String expected = hashChainService.recomputeHash(
            event.id(),
            event.action(),
            event.targetId(),
            event.changes(),
            previousHash
        );
        if (!expected.equals(event.hash())) {
            return new ChainPath(false, event.id(), event.hash(), 1, previousHash, Collections.emptySet());
        }

        Set<String> nextVisitedHashes = new HashSet<>(visitedHashes);
        nextVisitedHashes.add(event.hash());
        ChainPath tail = bestPath(event.hash(), byPreviousHash, nextVisitedHashes);
        if (!tail.valid()) {
            return new ChainPath(false, tail.firstInvalidId(), tail.firstInvalidHash(), tail.checkedCount() + 1, tail.tailHash(), tail.hotIds());
        }

        Set<String> pathHotIds = new HashSet<>(tail.hotIds());
        if (event.hot()) {
            pathHotIds.add(event.id());
        }
        return new ChainPath(true, null, null, tail.checkedCount() + 1, tail.tailHash(), pathHotIds);
    }

    private boolean isBetter(ChainPath candidate, ChainPath current) {
        if (candidate.hotIds().size() != current.hotIds().size()) {
            return candidate.hotIds().size() > current.hotIds().size();
        }
        return candidate.checkedCount() > current.checkedCount();
    }

    private Specification<AuditEventEntity> buildSpecification(AuditQuery query) {
        List<Specification<AuditEventEntity>> specs = new ArrayList<>();
        if (query.workspaceId() != null && !query.workspaceId().isBlank()) {
            specs.add((root, cb, q) -> q.equal(root.get("workspaceId"), query.workspaceId()));
        }
        if (query.action() != null && !query.action().isBlank()) {
            specs.add((root, cb, q) -> q.equal(root.get("action"), query.action()));
        }
        if (query.actorId() != null && !query.actorId().isBlank()) {
            specs.add((root, cb, q) -> q.equal(root.get("actorId"), query.actorId()));
        }
        if (query.targetType() != null && !query.targetType().isBlank()) {
            specs.add((root, cb, q) -> q.equal(root.get("targetType"), query.targetType()));
        }
        if (query.targetId() != null && !query.targetId().isBlank()) {
            specs.add((root, cb, q) -> q.equal(root.get("targetId"), query.targetId()));
        }
        if (query.from() != null) {
            specs.add((root, cb, q) -> q.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
        }
        if (query.to() != null) {
            specs.add((root, cb, q) -> q.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
        }
        Specification<AuditEventEntity> result = Specification.where((root, cb, q) -> q.conjunction());
        for (Specification<AuditEventEntity> spec : specs) {
            result = result.and(spec);
        }
        return result;
    }

    private AuditEventResponse toResponse(AuditEventEntity event) {
        Object changes = parseJson(event.getChanges());
        Object metadata = parseJson(event.getMetadata());
        return new AuditEventResponse(
            event.getId(),
            event.getWorkspaceId(),
            event.getActorId(),
            event.getAction(),
            event.getTargetType(),
            event.getTargetId(),
            changes,
            metadata,
            event.getPreviousHash(),
            event.getHash(),
            event.getOccurredAt()
        );
    }

    private ChainEvent toChainEvent(AuditEventEntity event) {
        return new ChainEvent(
            event.getId(),
            event.getAction(),
            event.getTargetId(),
            event.getChanges(),
            event.getPreviousHash(),
            event.getHash(),
            true
        );
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    public record AuditQuery(
        String workspaceId,
        String action,
        String actorId,
        String targetType,
        String targetId,
        Instant from,
        Instant to
    ) {}

    private record ChainVerification(
        boolean valid,
        String firstInvalidId,
        String firstInvalidHash,
        long checkedCount,
        String tailHash
    ) {}

    private record ChainPath(
        boolean valid,
        String firstInvalidId,
        String firstInvalidHash,
        long checkedCount,
        String tailHash,
        Set<String> hotIds
    ) {}
}
