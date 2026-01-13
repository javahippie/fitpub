package net.javahippie.fitpub.exception;

/**
 * Exception thrown when a FIT file is invalid or corrupted.
 */
public class InvalidFitFileException extends FitFileProcessingException {

    public InvalidFitFileException(String message) {
        super(message);
    }

    public InvalidFitFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
