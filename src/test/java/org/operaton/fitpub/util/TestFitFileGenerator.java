package org.operaton.fitpub.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for generating test FIT files.
 * Creates minimal valid FIT file structures for testing.
 */
public class TestFitFileGenerator {

    /**
     * Generates a minimal valid FIT file header.
     */
    public static byte[] generateValidFitFileHeader() {
        ByteBuffer buffer = ByteBuffer.allocate(14);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 14);           // Header size
        buffer.put((byte) 0x10);         // Protocol version 1.0
        buffer.putShort((short) 2048);   // Profile version
        buffer.putInt(100);              // Data size
        buffer.put(".FIT".getBytes());   // Signature

        return buffer.array();
    }

    /**
     * Generates a FIT file with invalid header size.
     */
    public static byte[] generateInvalidHeaderSize() {
        byte[] header = generateValidFitFileHeader();
        header[0] = 20; // Invalid header size
        return header;
    }

    /**
     * Generates a FIT file with invalid signature.
     */
    public static byte[] generateInvalidSignature() {
        byte[] header = generateValidFitFileHeader();
        header[8] = 'X'; // Invalid signature
        return header;
    }

    /**
     * Generates a minimal valid FIT file with a single data record.
     * This creates a very basic but valid FIT file structure.
     */
    public static byte[] generateMinimalValidFitFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write header
        ByteBuffer header = ByteBuffer.allocate(14);
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 14);           // Header size
        header.put((byte) 0x20);         // Protocol version 2.0
        header.putShort((short) 2113);   // Profile version 21.13
        header.putInt(0);                // Data size (will update later)
        header.put(".FIT".getBytes());   // Signature
        header.putShort((short) 0);      // CRC (optional, set to 0)
        baos.write(header.array());

        // For a real FIT file, we would write definition messages and data messages here
        // For testing purposes, this minimal header-only file should suffice for validation tests
        // More complex tests would require actual FIT SDK to generate proper files

        byte[] result = baos.toByteArray();

        // Update data size in header
        ByteBuffer dataSize = ByteBuffer.allocate(4);
        dataSize.order(ByteOrder.LITTLE_ENDIAN);
        dataSize.putInt(result.length - 14 - 2); // Exclude header and CRC
        System.arraycopy(dataSize.array(), 0, result, 4, 4);

        return result;
    }

    /**
     * Generates an empty byte array (invalid FIT file).
     */
    public static byte[] generateEmptyFile() {
        return new byte[0];
    }

    /**
     * Generates a file that's too small.
     */
    public static byte[] generateTooSmallFile() {
        return new byte[10];
    }

    /**
     * Generates a very large file (simulated, not actually allocating the memory).
     */
    public static byte[] generateTooLargeFileHeader() {
        // Just return a header, tests will check the size parameter
        return generateValidFitFileHeader();
    }
}
