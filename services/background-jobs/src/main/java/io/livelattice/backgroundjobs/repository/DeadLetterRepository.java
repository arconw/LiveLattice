package io.livelattice.backgroundjobs.repository;

import io.livelattice.backgroundjobs.model.DeadLetter;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {

    @Query("SELECT COUNT(d) FROM DeadLetter d")
    long countDeadLetters();

    @Query(value = """
        SELECT dl.* FROM background_job_dead_letters dl
        JOIN background_job_definitions jd ON jd.id = dl.job_definition_id
        WHERE jd.owner_subject = :ownerSubject
        ORDER BY dl.created_at DESC
        """, countQuery = """
        SELECT COUNT(*) FROM background_job_dead_letters dl
        JOIN background_job_definitions jd ON jd.id = dl.job_definition_id
        WHERE jd.owner_subject = :ownerSubject
        """, nativeQuery = true)
    Page<DeadLetter> findByOwnerSubject(@Param("ownerSubject") String ownerSubject, Pageable pageable);

    Page<DeadLetter> findByJobDefinitionId(UUID jobDefinitionId, Pageable pageable);

    boolean existsByJobDefinitionId(UUID jobDefinitionId);
}
