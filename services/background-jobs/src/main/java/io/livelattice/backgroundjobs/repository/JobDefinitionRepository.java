package io.livelattice.backgroundjobs.repository;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobDefinitionRepository extends JpaRepository<JobDefinition, UUID> {

    @Query(value = """
        SELECT * FROM background_job_definitions
        WHERE (:type IS NULL OR job_type = :type)
        AND (:status IS NULL OR status = :status)
        AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
        AND (:ownerSubject IS NULL OR owner_subject = :ownerSubject)
        ORDER BY created_at DESC
        """, countQuery = """
        SELECT COUNT(*) FROM background_job_definitions
        WHERE (:type IS NULL OR job_type = :type)
        AND (:status IS NULL OR status = :status)
        AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
        AND (:ownerSubject IS NULL OR owner_subject = :ownerSubject)
        """, nativeQuery = true)
    Page<JobDefinition> findScoped(@Param("type") String type,
                                   @Param("status") String status,
                                   @Param("workspaceId") UUID workspaceId,
                                   @Param("ownerSubject") String ownerSubject,
                                   Pageable pageable);

    Page<JobDefinition> findByJobTypeAndStatus(String jobType, JobStatus status, Pageable pageable);

    Page<JobDefinition> findByStatus(JobStatus status, Pageable pageable);

    Page<JobDefinition> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    @Query(value = """
        SELECT * FROM background_job_definitions
        WHERE status = 'QUEUED' AND job_type = :type
        AND (scheduled_at IS NULL OR scheduled_at <= :now)
        ORDER BY priority DESC, created_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT 1
        """, nativeQuery = true)
    Optional<JobDefinition> findNextQueuedByType(@Param("type") String type, @Param("now") Instant now);

    @Modifying
    @Query(value = """
        UPDATE background_job_definitions
        SET status = 'RUNNING', worker_id = :workerId, updated_at = :now
        WHERE id = :id AND (status = 'QUEUED' OR status = 'SCHEDULED' OR status = 'RETRYING')
        """, nativeQuery = true)
    int atomicClaimById(@Param("id") UUID id, @Param("workerId") String workerId, @Param("now") Instant now);

    @Query(value = """
        SELECT * FROM background_job_definitions
        WHERE status = :status AND worker_id = :workerId
        """, nativeQuery = true)
    List<JobDefinition> findRunningJobs(@Param("status") String status, @Param("workerId") String workerId);

    @Query(value = """
        SELECT * FROM background_job_definitions
        WHERE status = 'RETRYING' AND next_retry_at <= :now
        ORDER BY priority DESC, next_retry_at ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<JobDefinition> findFirstRetryable(@Param("now") Instant now);

    @Query(value = """
        SELECT * FROM background_job_definitions
        WHERE id = :id AND status = 'RETRYING' AND next_retry_at <= :now
        FOR UPDATE SKIP LOCKED
        LIMIT 1
        """, nativeQuery = true)
    Optional<JobDefinition> claimRetryableById(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE JobDefinition j SET j.status = :status, j.updatedAt = :now WHERE j.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") JobStatus status, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE JobDefinition j SET j.status = :status, j.retryCount = :retryCount, j.nextRetryAt = :nextRetryAt, j.updatedAt = :now WHERE j.id = :id")
    int updateRetryState(@Param("id") UUID id, @Param("status") JobStatus status, @Param("retryCount") int retryCount,
                         @Param("nextRetryAt") Instant nextRetryAt, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE JobDefinition j SET j.status = :status WHERE j.status = :currentStatus")
    int bulkTransitionStatus(@Param("currentStatus") JobStatus currentStatus, @Param("status") JobStatus status);
}
