package io.livelattice.core.repository;

import io.livelattice.core.model.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByExternalSubject(String externalSubject);

    boolean existsByExternalSubject(String externalSubject);
}
