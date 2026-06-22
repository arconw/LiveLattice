package io.livelattice.core.repository;

import io.livelattice.core.model.entity.ApiKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
