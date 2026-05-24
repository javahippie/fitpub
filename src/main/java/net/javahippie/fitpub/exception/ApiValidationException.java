package net.javahippie.fitpub.exception;

/**
 * Exception for API request validation that depends on runtime configuration.
 */
public class ApiValidationException extends RuntimeException {

    public ApiValidationException(String message) {
        super(message);
    }
}
