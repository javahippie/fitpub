package net.javahippie.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a comment on an activity.
 * Supports both local and federated comments (from remote ActivityPub actors).
 */
@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comments_activity_id", columnList = "activity_id"),
    @Index(name = "idx_comments_user_id", columnList = "user_id"),
    @Index(name = "idx_comments_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The activity being commented on.
     */
    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    /**
     * The local user who commented (null if remote).
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * The remote actor URI who commented (null if local).
     * Format: https://mastodon.social/users/username
     */
    @Column(name = "remote_actor_uri", length = 500)
    private String remoteActorUri;

    /**
     * Display name of the commenter (cached for performance).
     */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /**
     * Avatar URL of the commenter (cached for performance).
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * The comment content.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * The ActivityPub Note/Create activity ID (for federation).
     * Format: https://mastodon.social/users/username/statuses/123
     */
    @Column(name = "activity_pub_id", length = 500)
    private String activityPubId;

    /**
     * Whether the comment has been deleted (soft delete for federation tracking).
     */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Check if this is a local comment.
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
