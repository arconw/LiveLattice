package io.livelattice.auditlog.repository;

import io.livelattice.auditlog.model.ExportJobEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJobEntity, String> {

    Optional<ExportJobEntity> findFirstByStatusOrderByCreatedAtAsc(String status);
}
