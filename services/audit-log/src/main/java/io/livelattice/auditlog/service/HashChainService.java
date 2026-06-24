package io.livelattice.auditlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.model.AuditEventEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class HashChainService {

    private final ObjectMapper objectMapper;

    public HashChainService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String computeHash(AuditEventEntity event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(event.getId().getBytes(StandardCharsets.UTF_8));
            digest.update(event.getAction().getBytes(StandardCharsets.UTF_8));
            digest.update(event.getTargetId().getBytes(StandardCharsets.UTF_8));
            digest.update(normalize(event.getChanges()).getBytes(StandardCharsets.UTF_8));
            digest.update(event.getPreviousHash().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public String recomputeHash(String id, String action, String targetId, String changesJson, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(id.getBytes(StandardCharsets.UTF_8));
            digest.update(action.getBytes(StandardCharsets.UTF_8));
            digest.update(targetId.getBytes(StandardCharsets.UTF_8));
            digest.update(normalize(changesJson).getBytes(StandardCharsets.UTF_8));
            digest.update(previousHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String normalize(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(json));
        } catch (JsonProcessingException ex) {
            return json;
        }
    }
}
