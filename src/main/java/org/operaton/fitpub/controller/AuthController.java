package org.operaton.fitpub.controller;

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

    /**
     * Register a new user account.
     *
     * @param request Registration details
     * @return Authentication response with JWT token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // Check if registration is enabled
        if (!registrationEnabled) {
            log.warn("Registration attempt blocked - registration is disabled");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
        }

        log.info("Registration request received for username: {}", request.getUsername());

        try {
            AuthResponse response = userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
        return ResponseEntity.ok(new RegistrationStatusResponse(registrationEnabled));
    }

    /**
     * Authenticate user and generate JWT token.
     *
     * @param request Login credentials
     * @return Authentication response with JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for: {}", request.getUsernameOrEmail());

        try {
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            log.warn("Login failed: {}", e.getMessage());
            throw e;
        }
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
    record RegistrationStatusResponse(boolean enabled) {}
}
