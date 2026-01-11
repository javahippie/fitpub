package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.AuthResponse;
import org.operaton.fitpub.model.dto.LoginRequest;
import org.operaton.fitpub.model.dto.RegisterRequest;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for user management operations including registration and authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final FollowRepository followRepository;
    private final FederationService federationService;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Register a new user account with RSA key pair for ActivityPub.
     *
     * @param request Registration details
     * @return Authentication response with JWT token
     * @throws IllegalArgumentException if username or email already exists
     */
    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        log.info("Registering new user: {}", request.getUsername());

        // Check if username already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }

        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        // Generate RSA key pair for ActivityPub signatures
        KeyPair keyPair = generateRsaKeyPair();
        String publicKey = encodePublicKey(keyPair.getPublic().getEncoded());
        String privateKey = encodePrivateKey(keyPair.getPrivate().getEncoded());

        // Create user entity
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername())
                .bio(request.getBio())
                .publicKey(publicKey)
                .privateKey(privateKey)
                .enabled(true)
                .locked(false)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());

        // Generate JWT token
        String token = jwtTokenProvider.createToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build();
    }

    /**
     * Authenticate user and generate JWT token.
     *
     * @param request Login credentials
     * @return Authentication response with JWT token
     * @throws BadCredentialsException if credentials are invalid
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for: {}", request.getUsernameOrEmail());

        // Find user by username or email
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username/email or password"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username/email or password");
        }

        // Check if account is enabled
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        // Check if account is locked
        if (user.isLocked()) {
            throw new BadCredentialsException("Account is locked");
        }

        log.info("User logged in successfully: {}", user.getUsername());

        // Generate JWT token
        String token = jwtTokenProvider.createToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build();
    }

    /**
     * Generate RSA key pair for ActivityPub HTTP signatures.
     *
     * @return Generated key pair
     */
    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * Encode public key to PEM format.
     *
     * @param keyBytes Raw key bytes
     * @return PEM-formatted public key
     */
    private String encodePublicKey(byte[] keyBytes) {
        String base64 = Base64.getEncoder().encodeToString(keyBytes);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    /**
     * Encode private key to PEM format.
     *
     * @param keyBytes Raw key bytes
     * @return PEM-formatted private key
     */
    private String encodePrivateKey(byte[] keyBytes) {
        String base64 = Base64.getEncoder().encodeToString(keyBytes);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    /**
     * Delete a user account permanently.
     * Requires password verification for security.
     * Sends ActivityPub Delete activity to notify followers (best effort).
     * Database cascades handle most deletions automatically.
     *
     * @param userId User ID to delete
     * @param password Password for verification
     * @throws IllegalArgumentException if user not found
     * @throws BadCredentialsException if password is invalid
     */
    @Transactional
    public void deleteUserAccount(UUID userId, String password) {
        log.info("Attempting to delete user account: {}", userId);

        // 1. Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. Verify password
        log.debug("Verifying password for account deletion - user: {}, password provided: {}, hash exists: {}",
                 user.getUsername(), password != null && !password.isEmpty(), user.getPasswordHash() != null);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Invalid password provided for account deletion: {} (password matches: false)", user.getUsername());
            throw new BadCredentialsException("Invalid password");
        }

        log.info("Password verified successfully for account deletion: {}", user.getUsername());

        // 3. Send Delete activity to followers (best effort)
        try {
            federationService.sendActorDeleteActivity(user);
            log.info("Sent Delete activity to followers for: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to send Delete activity for {}, continuing with deletion", user.getUsername(), e);
        }

        // 4. Manual cleanup: Delete follows where user is being followed
        // (Database cascades don't cover this since followingActorUri is a string, not FK)
        String actorUri = baseUrl + "/users/" + user.getUsername();
        int deletedFollows = followRepository.deleteByFollowingActorUri(actorUri);
        log.info("Deleted {} follow records where user was being followed: {}", deletedFollows, user.getUsername());

        // 5. Delete user (triggers all ON DELETE CASCADE for related entities)
        userRepository.delete(user);

        log.info("User account deleted successfully: {}", user.getUsername());
    }
}
