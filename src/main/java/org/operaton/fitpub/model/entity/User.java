package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity representing a local user account.
 * Each user has an ActivityPub Actor profile for federation.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * RSA public key for ActivityPub HTTP Signatures.
     * Used by remote servers to verify signed requests from this user.
     */
    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
    private String publicKey;

    /**
     * RSA private key for signing ActivityPub requests.
     * Encrypted at rest (handled by application layer).
     */
    @Column(name = "private_key", columnDefinition = "TEXT", nullable = false)
    private String privateKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean locked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Gets the ActivityPub actor URI for this user.
     * Format: https://domain/users/{username}
     */
    public String getActorUri(String baseUrl) {
        return String.format("%s/users/%s", baseUrl, username);
    }

    /**
     * Gets the WebFinger account identifier.
     * Format: acct:username@domain
     */
    public String getWebFingerAccount(String domain) {
        return String.format("acct:%s@%s", username, domain);
    }
}
