package io.livelattice.auditlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.auth")
public class AuthProperties {

    private String internalSecret = "livelattice_internal_dev_secret";
    private String storageAccessKey = "livelattice";
    private String storageSecretKey = "livelattice_dev_password";

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public String getStorageAccessKey() {
        return storageAccessKey;
    }

    public void setStorageAccessKey(String storageAccessKey) {
        this.storageAccessKey = storageAccessKey;
    }

    public String getStorageSecretKey() {
        return storageSecretKey;
    }

    public void setStorageSecretKey(String storageSecretKey) {
        this.storageSecretKey = storageSecretKey;
    }
}
