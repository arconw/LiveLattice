package io.livelattice.auditlog.repository;

import io.livelattice.auditlog.model.AuditEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String>, JpaSpecificationExecutor<AuditEventEntity> {

    Optional<AuditEventEntity> findTopByOrderByIngestedAtDescIdDesc();

    List<AuditEventEntity> findAllByOrderByIngestedAtAscIdAsc();

    Page<AuditEventEntity> findAllByOrderByIngestedAtAscIdAsc(Pageable pageable);
}
