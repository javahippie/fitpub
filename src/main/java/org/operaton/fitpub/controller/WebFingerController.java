package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.activitypub.WebFingerResponse;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * WebFinger controller for user discovery.
 * Implements RFC 7033 WebFinger protocol.
 *
 * Example: /.well-known/webfinger?resource=acct:username@domain.com
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WebFingerController {

    private final UserRepository userRepository;

    @Value("${fitpub.domain}")
    private String domain;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * WebFinger endpoint for user discovery.
     *
     * @param resource the resource identifier (acct:username@domain)
     * @return WebFinger response
     */
    @GetMapping(value = "/.well-known/webfinger", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebFingerResponse> webfinger(@RequestParam("resource") String resource) {
        log.debug("WebFinger request for resource: {}", resource);

        // Parse resource identifier
        String username = parseUsername(resource);
        if (username == null) {
            log.warn("Invalid WebFinger resource format: {}", resource);
            return ResponseEntity.badRequest().build();
        }

        // Look up user
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("User not found for WebFinger request: {}", username);
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        String actorUrl = user.getActorUri(baseUrl);

        WebFingerResponse response = WebFingerResponse.forUser(username, domain, actorUrl);
        return ResponseEntity.ok(response);
    }

    /**
     * Parses the username from an acct: URI.
     *
     * @param resource the resource URI (acct:username@domain)
     * @return the username or null if invalid
     */
    private String parseUsername(String resource) {
        if (!resource.startsWith("acct:")) {
            return null;
        }

        // Remove "acct:" prefix
        String acct = resource.substring(5);

        // Split on @
        String[] parts = acct.split("@");
        if (parts.length != 2) {
            return null;
        }

        String username = parts[0];
        String requestedDomain = parts[1];

        // Verify domain matches
        if (!requestedDomain.equalsIgnoreCase(domain)) {
            log.warn("WebFinger request for different domain: {} (ours: {})", requestedDomain, domain);
            return null;
        }

        return username;
    }
}
