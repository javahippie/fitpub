package org.operaton.fitpub.exception;

/**
 * Exception thrown when an uploaded file format is not supported.
 * Currently supported formats: FIT, GPX.
 */
public class UnsupportedFileFormatException extends RuntimeException {

    public UnsupportedFileFormatException(String message) {
        super(message);
    }

    public UnsupportedFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
