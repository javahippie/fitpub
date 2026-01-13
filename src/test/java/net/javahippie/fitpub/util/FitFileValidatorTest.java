package net.javahippie.fitpub.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import net.javahippie.fitpub.exception.InvalidFitFileException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FitFileValidator.
 */
class FitFileValidatorTest {

    private FitFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FitFileValidator();
    }

    @Test
    @DisplayName("Should validate a valid FIT file header")
    void testValidateValidHeader() {
        byte[] validHeader = TestFitFileGenerator.generateValidFitFileHeader();

        assertDoesNotThrow(() -> validator.validate(validHeader));
    }

    @Test
    @DisplayName("Should throw exception for empty file")
    void testValidateEmptyFile() {
        byte[] emptyFile = TestFitFileGenerator.generateEmptyFile();

        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate(emptyFile)
        );

        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Should throw exception for null file")
    void testValidateNullFile() {
        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate((byte[]) null)
        );

        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Should throw exception for file that's too small")
    void testValidateTooSmallFile() {
        byte[] tooSmall = TestFitFileGenerator.generateTooSmallFile();

        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate(tooSmall)
        );

        assertTrue(exception.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("Should throw exception for file that's too large")
    void testValidateTooLargeFile() throws IOException {
        long tooLarge = 60L * 1024 * 1024; // 60 MB
        byte[] validHeader = TestFitFileGenerator.generateValidFitFileHeader();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(validHeader);

        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate(inputStream, tooLarge)
        );

        assertTrue(exception.getMessage().contains("too large"));
    }

    @Test
    @DisplayName("Should throw exception for invalid header size")
    void testValidateInvalidHeaderSize() {
        byte[] invalidHeader = TestFitFileGenerator.generateInvalidHeaderSize();

        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate(invalidHeader)
        );

        assertTrue(exception.getMessage().contains("Invalid FIT header size"));
    }

    @Test
    @DisplayName("Should throw exception for invalid signature")
    void testValidateInvalidSignature() {
        byte[] invalidSignature = TestFitFileGenerator.generateInvalidSignature();

        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate(invalidSignature)
        );

        assertTrue(exception.getMessage().contains("Invalid FIT file signature"));
    }

    @Test
    @DisplayName("Should validate file from input stream")
    void testValidateFromInputStream() throws IOException {
        byte[] validHeader = TestFitFileGenerator.generateValidFitFileHeader();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(validHeader);

        assertDoesNotThrow(() -> validator.validate(inputStream, validHeader.length));
    }

    @Test
    @DisplayName("Should throw exception for input stream with insufficient data")
    void testValidateInsufficientDataFromStream() throws IOException {
        byte[] tooSmall = new byte[10];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(tooSmall);

        InvalidFitFileException exception = assertThrows(
            InvalidFitFileException.class,
            () -> validator.validate(inputStream, tooSmall.length)
        );

        assertTrue(exception.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("Should validate .fit file extension")
    void testHasValidExtension() {
        assertTrue(validator.hasValidExtension("activity.fit"));
        assertTrue(validator.hasValidExtension("ACTIVITY.FIT"));
        assertTrue(validator.hasValidExtension("path/to/file.fit"));
        assertTrue(validator.hasValidExtension("file.FIT"));
    }

    @Test
    @DisplayName("Should reject invalid file extensions")
    void testHasInvalidExtension() {
        assertFalse(validator.hasValidExtension("activity.gpx"));
        assertFalse(validator.hasValidExtension("activity.txt"));
        assertFalse(validator.hasValidExtension("activity"));
        assertFalse(validator.hasValidExtension(null));
        assertFalse(validator.hasValidExtension(""));
    }
}
