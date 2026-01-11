package org.operaton.fitpub.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.AuthResponse;
import org.operaton.fitpub.model.dto.LoginRequest;
import org.operaton.fitpub.model.dto.RegisterRequest;
import org.operaton.fitpub.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 * Handles user registration and login.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @Value("${fitpub.registration.enabled:true}")
    private boolean registrationEnabled;

    @Value("${fitpub.registration.password:#{null}}")
    private String configuredRegistrationPassword;

    /**
     * Register a new user account.
     *
     * @param request Registration details
     * @param response HTTP response for setting cookies
     * @return Authentication response with JWT token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        // Check if registration is enabled
        if (!registrationEnabled) {
            log.warn("Registration attempt blocked - registration is disabled");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
        }

        // Check registration password if configured
        // Check for both null and blank (empty or whitespace-only strings)
        log.debug("Registration password check - configured: '{}', provided: '{}'",
                 configuredRegistrationPassword, request.getRegistrationPassword());

        if (configuredRegistrationPassword != null && !configuredRegistrationPassword.trim().isEmpty()) {
            String providedPassword = request.getRegistrationPassword();
            if (providedPassword == null || providedPassword.trim().isEmpty() ||
                !configuredRegistrationPassword.equals(providedPassword)) {
                log.warn("Registration attempt with invalid registration password for username: {} (expected: '{}', got: '{}')",
                         request.getUsername(), configuredRegistrationPassword, providedPassword);
                throw new IllegalArgumentException("Invalid registration password");
            }
            log.info("Registration password validated successfully for username: {}", request.getUsername());
        } else {
            log.info("No registration password configured - allowing open registration for username: {}", request.getUsername());
        }

        log.info("Registration request received for username: {}", request.getUsername());

        try {
            AuthResponse authResponse = userService.registerUser(request);

            // Set JWT as httpOnly cookie
            setJwtCookie(response, authResponse.getToken());

            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get registration status.
     *
     * @return Registration status response
     */
    @GetMapping("/registration-status")
    public ResponseEntity<RegistrationStatusResponse> getRegistrationStatus() {
        boolean passwordRequired = configuredRegistrationPassword != null && !configuredRegistrationPassword.trim().isEmpty();
        return ResponseEntity.ok(new RegistrationStatusResponse(registrationEnabled, passwordRequired));
    }

    /**
     * Authenticate user and generate JWT token.
     *
     * @param request Login credentials
     * @param response HTTP response for setting cookies
     * @return Authentication response with JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        log.info("Login request received for: {}", request.getUsernameOrEmail());

        try {
            AuthResponse authResponse = userService.login(request);

            // Set JWT as httpOnly cookie
            setJwtCookie(response, authResponse.getToken());

            return ResponseEntity.ok(authResponse);
        } catch (BadCredentialsException e) {
            log.warn("Login failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Logout user by clearing the JWT cookie.
     *
     * @param response HTTP response for clearing cookies
     * @return Success response
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        log.info("Logout request received");

        // Clear the JWT cookie
        Cookie cookie = new Cookie("JWT_TOKEN", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(0); // Delete cookie
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    /**
     * Helper method to set JWT as httpOnly cookie.
     *
     * @param response HTTP response
     * @param token JWT token
     */
    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("JWT_TOKEN", token);
        cookie.setHttpOnly(true); // Prevent JavaScript access
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24 hours (same as JWT expiration)
        response.addCookie(cookie);
        log.debug("JWT cookie set");
    }

    /**
     * Exception handler for IllegalArgumentException (e.g., duplicate username/email).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    /**
     * Exception handler for BadCredentialsException.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }

    /**
     * Error response DTO.
     */
    record ErrorResponse(String error, String message) {}

    /**
     * Registration status response DTO.
     */
    record RegistrationStatusResponse(boolean enabled, boolean passwordRequired) {}
}
