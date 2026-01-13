package net.javahippie.fitpub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WebFinger client for discovering ActivityPub actors on remote instances.
 * Implements RFC 7033 WebFinger protocol with SSRF protection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebFingerClient {

    private final ObjectMapper objectMapper;

    @Value("${fitpub.domain}")
    private String localDomain;

    @Value("${fitpub.activitypub.allow-private-ips:false}")
    private boolean allowPrivateIps;

    @Value("${fitpub.activitypub.federation-protocol:https}")
    private String federationProtocol;

    private static final int TIMEOUT_SECONDS = 5;
    private static final String WEBFINGER_PATH = "/.well-known/webfinger";
    private static final String ACTIVITYPUB_CONTENT_TYPE = "application/activity+json";

    /**
     * Discovers an ActivityPub actor URI from a handle.
     *
     * @param handle the handle in format @username@domain or username@domain
     * @return the actor URI (e.g., https://domain.com/users/username)
     * @throws IllegalArgumentException if handle is invalid or domain is not allowed
     * @throws IOException if WebFinger request fails
     */
    public String discoverActor(String handle) throws IOException {
        log.debug("Discovering actor for handle: {}", handle);

        // Parse and validate handle
        ParsedHandle parsed = parseHandle(handle);
        String username = parsed.username;
        String domain = parsed.domain;

        // SSRF protection: validate domain
        validateDomain(domain);

        // Fetch WebFinger resource
        Map<String, Object> webFingerResponse = fetchWebFingerResource(domain, username);

        // Extract actor URI from links
        String actorUri = extractActorUri(webFingerResponse);
        if (actorUri == null) {
            throw new IOException("No ActivityPub actor link found in WebFinger response");
        }

        log.info("Discovered actor URI: {} for handle: {}", actorUri, handle);
        return actorUri;
    }

    /**
     * Parses a handle into username and domain components.
     *
     * @param handle the handle (e.g., @username@domain or username@domain)
     * @return parsed handle components
     * @throws IllegalArgumentException if handle format is invalid
     */
    private ParsedHandle parseHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new IllegalArgumentException("Handle cannot be null or empty");
        }

        // Remove leading @ if present
        String normalized = handle.startsWith("@") ? handle.substring(1) : handle;

        // Split on @
        String[] parts = normalized.split("@");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid handle format. Expected: @username@domain or username@domain");
        }

        String username = parts[0].trim();
        String domain = parts[1].trim();

        if (username.isEmpty() || domain.isEmpty()) {
            throw new IllegalArgumentException("Username and domain cannot be empty");
        }

        // Validate username format (alphanumeric, underscore, hyphen)
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid username format. Allowed characters: a-z, A-Z, 0-9, _, -");
        }

        // Validate domain format (basic check - allow domains and IP addresses)
        // Domain: must have at least one dot and end with 2+ letters, optional port
        // IP: must be 4 numbers separated by dots, optional port
        boolean isValidDomain = domain.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(:[0-9]+)?$");
        boolean isValidIP = domain.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:[0-9]+)?$");

        if (!isValidDomain && !isValidIP) {
            throw new IllegalArgumentException("Invalid domain format. Domain must be a valid hostname or IP address, optionally with port");
        }

        return new ParsedHandle(username, domain);
    }

    /**
     * Fetches WebFinger resource from a remote domain.
     *
     * @param domain the domain to query
     * @param username the username to look up
     * @return WebFinger response as a map
     * @throws IOException if request fails
     */
    private Map<String, Object> fetchWebFingerResource(String domain, String username) throws IOException {
        // Construct WebFinger URL
        String resource = "acct:" + username + "@" + domain;
        String webFingerUrl = federationProtocol + "://" + domain + WEBFINGER_PATH + "?resource=" + resource;

        log.debug("Fetching WebFinger resource: {}", webFingerUrl);

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webFingerUrl))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/jrd+json, application/json")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("WebFinger request failed with status: " + response.statusCode());
            }

            // Parse JSON response
            @SuppressWarnings("unchecked")
            Map<String, Object> webFingerData = objectMapper.readValue(response.body(), Map.class);

            return webFingerData;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("WebFinger request interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to fetch WebFinger resource: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that a domain is not a private or loopback address (SSRF protection).
     *
     * @param domain the domain to validate
     * @throws IllegalArgumentException if domain resolves to a private IP
     */
    private void validateDomain(String domain) {
        // Don't allow requests to local domain (should use local API instead)
        if (domain.equalsIgnoreCase(localDomain)) {
            throw new IllegalArgumentException("Cannot discover local users via WebFinger. Use local API instead.");
        }

        // If private IPs are allowed (local testing mode), skip SSRF protection
        if (allowPrivateIps) {
            log.debug("Private IPs allowed - skipping SSRF validation for domain: {}", domain);
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(domain);

            // Block loopback addresses (127.0.0.0/8, ::1)
            if (address.isLoopbackAddress()) {
                throw new IllegalArgumentException("Loopback addresses are not allowed: " + domain);
            }

            // Block site-local addresses (private IPs: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
            if (address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("Private IP addresses are not allowed: " + domain);
            }

            // Block link-local addresses (169.254.0.0/16, fe80::/10)
            if (address.isLinkLocalAddress()) {
                throw new IllegalArgumentException("Link-local addresses are not allowed: " + domain);
            }

            // Block multicast addresses
            if (address.isMulticastAddress()) {
                throw new IllegalArgumentException("Multicast addresses are not allowed: " + domain);
            }

            log.debug("Domain validation passed for: {} (resolved to {})", domain, address.getHostAddress());

        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve domain: " + domain, e);
        }
    }

    /**
     * Extracts the ActivityPub actor URI from a WebFinger response.
     *
     * @param webFingerResponse the WebFinger response
     * @return the actor URI, or null if not found
     */
    private String extractActorUri(Map<String, Object> webFingerResponse) {
        Object linksObj = webFingerResponse.get("links");
        if (!(linksObj instanceof List)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) linksObj;

        // Look for link with rel="self" and type="application/activity+json"
        for (Map<String, Object> link : links) {
            String rel = (String) link.get("rel");
            String type = (String) link.get("type");
            String href = (String) link.get("href");

            if ("self".equals(rel) &&
                (ACTIVITYPUB_CONTENT_TYPE.equals(type) ||
                 "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"".equals(type))) {
                return href;
            }
        }

        return null;
    }

    /**
     * Internal class to hold parsed handle components.
     */
    private static class ParsedHandle {
        final String username;
        final String domain;

        ParsedHandle(String username, String domain) {
            this.username = username;
            this.domain = domain;
        }
    }
}
