package io.livelattice.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.auth")
public class AuthProperties {

    private String internalSecret = "livelattice_internal_dev_secret";
    private String reindexAdminRole = "admin";

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public String getReindexAdminRole() {
        return reindexAdminRole;
    }

    public void setReindexAdminRole(String reindexAdminRole) {
        this.reindexAdminRole = reindexAdminRole;
    }
}
