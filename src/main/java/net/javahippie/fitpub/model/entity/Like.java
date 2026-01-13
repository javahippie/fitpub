package net.javahippie.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a like on an activity.
 * Supports both local and federated likes (from remote ActivityPub actors).
 */
@Entity
@Table(name = "likes", indexes = {
    @Index(name = "idx_likes_activity_id", columnList = "activity_id"),
    @Index(name = "idx_likes_user_id", columnList = "user_id"),
    @Index(name = "idx_likes_activity_user", columnList = "activity_id,user_id", unique = true),
    @Index(name = "idx_likes_activity_actor", columnList = "activity_id,remote_actor_uri", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The activity being liked.
     */
    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    /**
     * The local user who liked the activity (null if remote).
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * The remote actor URI who liked the activity (null if local).
     * Format: https://mastodon.social/users/username
     */
    @Column(name = "remote_actor_uri", length = 500)
    private String remoteActorUri;

    /**
     * Display name of the liker (cached for performance).
     */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /**
     * Avatar URL of the liker (cached for performance).
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * The ActivityPub Like activity ID (for federation).
     * Format: https://mastodon.social/users/username/statuses/123/activity
     */
    @Column(name = "activity_pub_id", length = 500)
    private String activityPubId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if this is a local like.
     */
    public boolean isLocal() {
        return userId != null;
    }

    /**
     * Get the actor identifier (local user ID or remote actor URI).
     */
    public String getActorIdentifier() {
        return isLocal() ? userId.toString() : remoteActorUri;
    }
}
