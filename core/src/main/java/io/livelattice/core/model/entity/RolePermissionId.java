package io.livelattice.core.model.entity;

import java.io.Serializable;
import java.util.Objects;

public class RolePermissionId implements Serializable {
    private String role;
    private String permission;

    public RolePermissionId() {}

    public RolePermissionId(String role, String permission) {
        this.role = role;
        this.permission = permission;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionId that)) return false;
        return Objects.equals(role, that.role) && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, permission);
    }
}
