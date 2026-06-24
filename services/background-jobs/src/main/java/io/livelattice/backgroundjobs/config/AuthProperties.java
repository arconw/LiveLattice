package io.livelattice.backgroundjobs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.auth")
public class AuthProperties {

    private String internalSecret = "livelattice_internal_dev_secret";

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }
}
