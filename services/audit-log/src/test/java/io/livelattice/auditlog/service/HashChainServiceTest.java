package io.livelattice.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.model.AuditEventEntity;
import org.junit.jupiter.api.Test;

class HashChainServiceTest {

    private final HashChainService service = new HashChainService(new ObjectMapper());

    @Test
    void computesGenesisHash() {
        AuditEventEntity event = event("event-1", "canvas.create", "canvas", "t-1", "{}", AuditEventEntity.genesisHash());
        String hash = service.computeHash(event);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void sameInputsProduceSameHash() {
        AuditEventEntity event = event("event-2", "canvas.update", "canvas", "t-2", "{\"version\":1}", AuditEventEntity.genesisHash());
        assertEquals(service.computeHash(event), service.computeHash(event));
    }

    @Test
    void differentPreviousHashProducesDifferentHash() {
        AuditEventEntity first = event("event-3", "canvas.create", "canvas", "t-3", "{}", AuditEventEntity.genesisHash());
        String firstHash = service.computeHash(first);
        AuditEventEntity second = event("event-4", "canvas.create", "canvas", "t-4", "{}", firstHash);
        String secondHash = service.computeHash(second);
        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void recomputedHashMatchesStoredHash() {
        AuditEventEntity event = event("event-5", "workspace.create", "workspace", "ws-1", "{}", AuditEventEntity.genesisHash());
        String hash = service.computeHash(event);
        String recomputed = service.recomputeHash(event.getId(), event.getAction(), event.getTargetId(), event.getChanges(), event.getPreviousHash());
        assertEquals(hash, recomputed);
    }

    private AuditEventEntity event(String id, String action, String targetType, String targetId, String changes, String previousHash) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(id);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setChanges(changes);
        event.setMetadata("{}");
        event.setWorkspaceId("ws-1");
        event.setActorId("actor-1");
        event.setPreviousHash(previousHash);
        return event;
    }
}
