package net.javahippie.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a remote ActivityPub actor (user from another server).
 * Cached information about remote actors for federation.
 */
@Entity
@Table(name = "remote_actors", indexes = {
    @Index(name = "idx_actor_uri", columnList = "actor_uri", unique = true),
    @Index(name = "idx_domain", columnList = "domain")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteActor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The full ActivityPub actor URI.
     * Example: https://mastodon.social/users/alice
     */
    @Column(name = "actor_uri", nullable = false, unique = true, length = 512)
    private String actorUri;

    /**
     * The username part of the actor.
     * Example: alice
     */
    @Column(nullable = false, length = 255)
    private String username;

    /**
     * The domain of the remote server.
     * Example: mastodon.social
     */
    @Column(nullable = false, length = 255)
    private String domain;

    /**
     * The actor's inbox URL for sending activities.
     */
    @Column(name = "inbox_url", nullable = false, length = 512)
    private String inboxUrl;

    /**
     * The actor's outbox URL for fetching activities.
     */
    @Column(name = "outbox_url", length = 512)
    private String outboxUrl;

    /**
     * The actor's shared inbox URL (if available).
     * More efficient for server-to-server communication.
     */
    @Column(name = "shared_inbox_url", length = 512)
    private String sharedInboxUrl;

    /**
     * The actor's public key in PEM format.
     * Used for verifying HTTP signatures.
     */
    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
    private String publicKey;

    /**
     * The actor's public key ID.
     * Example: https://mastodon.social/users/alice#main-key
     */
    @Column(name = "public_key_id", length = 512)
    private String publicKeyId;

    /**
     * The actor's display name.
     */
    @Column(name = "display_name", length = 255)
    private String displayName;

    /**
     * The actor's avatar URL.
     */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /**
     * The actor's bio/summary.
     */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /**
     * When the actor information was last fetched/updated.
     */
    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Extract username and domain from actor URI.
     * Example: https://mastodon.social/users/alice -> alice@mastodon.social
     */
    public String getHandle() {
        return username + "@" + domain;
    }
}
