package io.livelattice.core.repository;

import io.livelattice.core.model.entity.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {
    @Query(value = "SELECT * FROM data_sources WHERE workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<DataSource> findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
        @Param("workspaceId") UUID workspaceId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    Optional<DataSource> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
