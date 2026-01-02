package org.operaton.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.exception.InvalidGpxFileException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

/**
 * Validates GPX files before processing.
 * Checks file size, XML well-formedness, and GPX structure.
 */
@Component
@Slf4j
public class GpxFileValidator {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MIN_FILE_SIZE = 100; // Minimum XML file size

    /**
     * Validates a GPX file from byte array.
     *
     * @param fileData the GPX file data
     * @throws InvalidGpxFileException if the file is invalid
     */
    public void validate(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            throw new InvalidGpxFileException("GPX file is empty");
        }

        validateFileSize(fileData.length);
        validateGpxStructure(fileData);
    }

    /**
     * Validates the file size.
     *
     * @param size the file size in bytes
     * @throws InvalidGpxFileException if the size is invalid
     */
    private void validateFileSize(long size) {
        if (size < MIN_FILE_SIZE) {
            throw new InvalidGpxFileException(
                String.format("GPX file is too small. Size: %d bytes, minimum: %d bytes", size, MIN_FILE_SIZE)
            );
        }

        if (size > MAX_FILE_SIZE) {
            throw new InvalidGpxFileException(
                String.format("GPX file is too large. Size: %d bytes, maximum: %d bytes", size, MAX_FILE_SIZE)
            );
        }
    }

    /**
     * Validates GPX XML structure.
     *
     * @param fileData the GPX file data
     * @throws InvalidGpxFileException if the structure is invalid
     */
    private void validateGpxStructure(byte[] fileData) {
        try {
            // Parse XML to check well-formedness
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(fileData));

            // Check root element is <gpx>
            Element root = doc.getDocumentElement();
            if (!"gpx".equals(root.getLocalName()) && !"gpx".equals(root.getNodeName())) {
                throw new InvalidGpxFileException("Root element must be <gpx>, found: <" + root.getNodeName() + ">");
            }

            // Check for at least one <trk> or <rte> element
            NodeList tracks = doc.getElementsByTagName("trk");
            NodeList routes = doc.getElementsByTagName("rte");

            // Also check for namespace-qualified names
            if (tracks.getLength() == 0) {
                tracks = doc.getElementsByTagNameNS("*", "trk");
            }
            if (routes.getLength() == 0) {
                routes = doc.getElementsByTagNameNS("*", "rte");
            }

            if (tracks.getLength() == 0 && routes.getLength() == 0) {
                throw new InvalidGpxFileException("GPX file must contain at least one <trk> or <rte> element");
            }

            log.debug("GPX validation successful. Tracks: {}, Routes: {}", tracks.getLength(), routes.getLength());
        } catch (InvalidGpxFileException e) {
            throw e; // Re-throw our custom exceptions
        } catch (Exception e) {
            throw new InvalidGpxFileException("Invalid GPX XML structure: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a file appears to be a valid GPX file based on extension.
     *
     * @param filename the filename
     * @return true if the filename has a .gpx extension
     */
    public boolean hasValidExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        return filename.toLowerCase().endsWith(".gpx");
    }
}
