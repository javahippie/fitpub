package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WebFingerClient.
 */
@ExtendWith(MockitoExtension.class)
class WebFingerClientTest {

    private WebFingerClient webFingerClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webFingerClient = new WebFingerClient(objectMapper);
        ReflectionTestUtils.setField(webFingerClient, "localDomain", "fitpub.test");
    }

    // ==================== Handle Parsing Tests ====================

    @Test
    void parseHandle_withAtPrefix_shouldParseCorrectly() throws Exception {
        // This test uses reflection to access the private parseHandle method
        String handle = "@alice@example.com";

        // We can't directly test private methods, but we can test through discoverActor
        // which will validate the handle parsing logic
        // For now, we'll test the validation through discoverActor's exceptions

        // Testing valid format doesn't throw during parsing phase
        assertThatThrownBy(() -> webFingerClient.discoverActor(handle))
            .isInstanceOf(IOException.class) // Will fail at network call, but parsing succeeded
            .hasMessageContaining("Failed to fetch WebFinger resource");
    }

    @Test
    void parseHandle_withoutAtPrefix_shouldParseCorrectly() throws Exception {
        String handle = "alice@example.com";

        assertThatThrownBy(() -> webFingerClient.discoverActor(handle))
            .isInstanceOf(IOException.class) // Will fail at network call, but parsing succeeded
            .hasMessageContaining("Failed to fetch WebFinger resource");
    }

    @Test
    void parseHandle_withNullHandle_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Handle cannot be null or empty");
    }

    @Test
    void parseHandle_withEmptyHandle_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Handle cannot be null or empty");
    }

    @Test
    void parseHandle_withBlankHandle_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Handle cannot be null or empty");
    }

    @Test
    void parseHandle_withoutAtSymbol_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("aliceexample.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid handle format");
    }

    @Test
    void parseHandle_withMultipleAtSymbols_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("@alice@example@com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid handle format");
    }

    @Test
    void parseHandle_withEmptyUsername_shouldThrowException() {
        // "@example.com" becomes "example.com" after removing @, then split gives only 1 part
        assertThatThrownBy(() -> webFingerClient.discoverActor("@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid handle format");
    }

    @Test
    void parseHandle_withEmptyDomain_shouldThrowException() {
        // "alice@" splits into ["alice"] (trailing empty string is discarded)
        // So this fails the parts.length != 2 check
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid handle format");
    }

    @Test
    void parseHandle_withInvalidUsernameCharacters_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice!@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid username format");
    }

    @Test
    void parseHandle_withInvalidDomainFormat_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid domain format");
    }

    @Test
    void parseHandle_withValidUsernameCharacters_shouldNotThrowParsingException() {
        // Valid characters: a-z, A-Z, 0-9, _, -
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice_bob-123@example.com"))
            .isInstanceOf(IOException.class) // Fails at network, not parsing
            .hasMessageContaining("Failed to fetch WebFinger resource");
    }

    // ==================== SSRF Protection Tests ====================

    @Test
    void validateDomain_withLoopbackAddress_shouldThrowException() {
        // "localhost" doesn't have a dot, so it fails domain format validation
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@localhost"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid domain format");
    }

    @Test
    void validateDomain_with127_0_0_1_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@127.0.0.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Loopback addresses are not allowed");
    }

    @Test
    void validateDomain_withPrivateIP_10_0_0_1_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@10.0.0.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Private IP addresses are not allowed");
    }

    @Test
    void validateDomain_withPrivateIP_192_168_1_1_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@192.168.1.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Private IP addresses are not allowed");
    }

    @Test
    void validateDomain_withPrivateIP_172_16_0_1_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@172.16.0.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Private IP addresses are not allowed");
    }

    @Test
    void validateDomain_withLinkLocalAddress_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@169.254.0.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Link-local addresses are not allowed");
    }

    @Test
    void validateDomain_withLocalDomain_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@fitpub.test"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot discover local users via WebFinger");
    }

    @Test
    void validateDomain_withLocalDomainCaseInsensitive_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@FITPUB.TEST"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot discover local users via WebFinger");
    }

    @Test
    void validateDomain_withNonexistentDomain_shouldThrowException() {
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@this-domain-does-not-exist-12345.invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unable to resolve domain");
    }

    // ==================== Integration-like Tests ====================
    // Note: These tests will attempt real network calls and will fail with IOException
    // In a real scenario, we'd use WireMock or similar to mock HTTP responses

    @Test
    void discoverActor_withValidHandle_butNoNetwork_shouldThrowIOException() {
        // This test validates that valid handles pass validation
        // Use a domain that definitely won't have WebFinger endpoint
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@example.com"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Failed to fetch WebFinger resource");
    }

    @Test
    void discoverActor_withPublicIP_shouldPassSSRFValidation() {
        // Public IP (Google DNS) should pass SSRF validation but fail at WebFinger layer
        assertThatThrownBy(() -> webFingerClient.discoverActor("alice@8.8.8.8"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Failed to fetch WebFinger resource");
    }
}
