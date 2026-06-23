package io.livelattice.search.exception;

public class SearchBackendException extends RuntimeException {
    public SearchBackendException(String message) {
        super(message);
    }

    public SearchBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
