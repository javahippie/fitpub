package net.javahippie.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.exception.InvalidFitFileException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Validates FIT files before processing.
 * Checks file size, header, and basic integrity.
 */
@Component
@Slf4j
public class FitFileValidator {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MIN_FILE_SIZE = 14; // Minimum FIT file header size
    private static final byte[] FIT_HEADER_SIGNATURE = {'.', 'F', 'I', 'T'};
    private static final int HEADER_SIZE_OFFSET = 0;
    private static final int PROTOCOL_VERSION_OFFSET = 1;
    private static final int SIGNATURE_OFFSET = 8;

    /**
     * Validates a FIT file from byte array.
     *
     * @param fileData the FIT file data
     * @throws InvalidFitFileException if the file is invalid
     */
    public void validate(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            throw new InvalidFitFileException("FIT file is empty");
        }

        validateFileSize(fileData.length);
        validateFitHeader(fileData);
    }

    /**
     * Validates a FIT file from input stream.
     *
     * @param inputStream the input stream
     * @param contentLength the content length
     * @throws InvalidFitFileException if the file is invalid
     */
    public void validate(InputStream inputStream, long contentLength) throws IOException {
        validateFileSize(contentLength);

        byte[] header = new byte[14];
        int bytesRead = inputStream.read(header);

        if (bytesRead < MIN_FILE_SIZE) {
            throw new InvalidFitFileException("FIT file is too small. Minimum size is " + MIN_FILE_SIZE + " bytes");
        }

        validateFitHeader(header);
    }

    /**
     * Validates the file size.
     *
     * @param size the file size in bytes
     * @throws InvalidFitFileException if the size is invalid
     */
    private void validateFileSize(long size) {
        if (size < MIN_FILE_SIZE) {
            throw new InvalidFitFileException(
                String.format("FIT file is too small. Size: %d bytes, minimum: %d bytes", size, MIN_FILE_SIZE)
            );
        }

        if (size > MAX_FILE_SIZE) {
            throw new InvalidFitFileException(
                String.format("FIT file is too large. Size: %d bytes, maximum: %d bytes", size, MAX_FILE_SIZE)
            );
        }
    }

    /**
     * Validates the FIT file header.
     *
     * @param data the file data (at least first 14 bytes)
     * @throws InvalidFitFileException if the header is invalid
     */
    private void validateFitHeader(byte[] data) {
        if (data.length < MIN_FILE_SIZE) {
            throw new InvalidFitFileException("Insufficient data to validate FIT header");
        }

        // Check header size
        int headerSize = data[HEADER_SIZE_OFFSET] & 0xFF;
        if (headerSize != 12 && headerSize != 14) {
            throw new InvalidFitFileException(
                String.format("Invalid FIT header size: %d. Expected 12 or 14", headerSize)
            );
        }

        // Check protocol version
        int protocolVersion = data[PROTOCOL_VERSION_OFFSET] & 0xFF;
        int majorVersion = protocolVersion >> 4;
        if (majorVersion == 0 || majorVersion > 20) {
            log.warn("Unusual FIT protocol version: {}.{}", majorVersion, protocolVersion & 0x0F);
        }

        // Check signature
        boolean signatureValid = true;
        for (int i = 0; i < FIT_HEADER_SIGNATURE.length; i++) {
            if (data[SIGNATURE_OFFSET + i] != FIT_HEADER_SIGNATURE[i]) {
                signatureValid = false;
                break;
            }
        }

        if (!signatureValid) {
            throw new InvalidFitFileException(
                "Invalid FIT file signature. Expected '.FIT' at offset " + SIGNATURE_OFFSET
            );
        }

        log.debug("FIT file header validated successfully. Header size: {}, Protocol version: {}.{}",
            headerSize, majorVersion, protocolVersion & 0x0F);
    }

    /**
     * Checks if a file appears to be a valid FIT file based on extension.
     *
     * @param filename the filename
     * @return true if the filename has a .fit extension
     */
    public boolean hasValidExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        return filename.toLowerCase().endsWith(".fit");
    }
}
