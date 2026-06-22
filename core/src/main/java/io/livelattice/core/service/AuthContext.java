package io.livelattice.core.service;

public final class AuthContext {

    private static final ThreadLocal<ApiKeyValidation> API_KEY = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setApiKey(ApiKeyValidation validation) {
        API_KEY.set(validation);
    }

    public static ApiKeyValidation apiKey() {
        return API_KEY.get();
    }

    public static void clear() {
        API_KEY.remove();
    }
}
