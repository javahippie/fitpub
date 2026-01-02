package org.operaton.fitpub.exception;

/**
 * Base exception for GPX file processing errors.
 * Thrown when an error occurs during GPX file parsing or processing.
 */
public class GpxFileProcessingException extends RuntimeException {

    public GpxFileProcessingException(String message) {
        super(message);
    }

    public GpxFileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
