package io.livelattice.core.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
public class RolePermission {

    @Id
    private String role;

    @Id
    private String permission;

    public RolePermission() {}

    public RolePermission(String role, String permission) {
        this.role = role;
        this.permission = permission;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
