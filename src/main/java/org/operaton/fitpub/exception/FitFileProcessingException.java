package org.operaton.fitpub.exception;

/**
 * Exception thrown when FIT file processing fails.
 */
public class FitFileProcessingException extends RuntimeException {

    public FitFileProcessingException(String message) {
        super(message);
    }

    public FitFileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
