package io.livelattice.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.auth")
public class AuthProperties {

    private String internalSecret = "livelattice_internal_dev_secret";
    private long apiKeyCacheTtlSeconds = 300;

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public long getApiKeyCacheTtlSeconds() {
        return apiKeyCacheTtlSeconds;
    }

    public void setApiKeyCacheTtlSeconds(long apiKeyCacheTtlSeconds) {
        this.apiKeyCacheTtlSeconds = apiKeyCacheTtlSeconds;
    }
}
