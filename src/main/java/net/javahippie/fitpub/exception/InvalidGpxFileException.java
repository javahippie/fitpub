package net.javahippie.fitpub.exception;

/**
 * Exception thrown when a GPX file fails validation.
 * This includes malformed XML, missing required elements, or invalid GPX structure.
 */
public class InvalidGpxFileException extends GpxFileProcessingException {

    public InvalidGpxFileException(String message) {
        super(message);
    }

    public InvalidGpxFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
