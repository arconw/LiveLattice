package io.livelattice.backgroundjobs.repository;

import io.livelattice.backgroundjobs.model.JobExecution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    Optional<JobExecution> findTopByJobDefinitionIdOrderByCreatedAtDesc(UUID jobDefinitionId);
}
