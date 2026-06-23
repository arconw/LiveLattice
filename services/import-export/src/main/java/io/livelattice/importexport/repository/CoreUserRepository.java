package io.livelattice.importexport.repository;

import io.livelattice.importexport.model.CoreUserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreUserRepository extends JpaRepository<CoreUserEntity, UUID> {
    Optional<CoreUserEntity> findByExternalSubject(String externalSubject);
}
