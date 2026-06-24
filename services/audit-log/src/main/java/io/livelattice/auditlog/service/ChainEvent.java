package io.livelattice.auditlog.service;

record ChainEvent(
    String id,
    String action,
    String targetId,
    String changes,
    String previousHash,
    String hash,
    boolean hot
) {
    ChainEvent(String id, String action, String targetId, String changes, String previousHash, String hash) {
        this(id, action, targetId, changes, previousHash, hash, false);
    }
}
