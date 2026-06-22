package io.livelattice.core.repository;

import io.livelattice.core.model.entity.RolePermission;
import io.livelattice.core.model.entity.RolePermissionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByRole(String role);

    boolean existsByRoleAndPermission(String role, String permission);
}
