package net.javahippie.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a user notification.
 * Notifications are created for social interactions like likes, comments, follows, etc.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id", columnList = "user_id"),
    @Index(name = "idx_notifications_read_status", columnList = "user_id, is_read"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * The user who receives this notification.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Type of notification.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    /**
     * The user who triggered this notification (actor).
     * Can be null for system notifications.
     */
    @Column(name = "actor_uri")
    private String actorUri;

    /**
     * Display name of the actor (cached for performance).
     */
    @Column(name = "actor_display_name")
    private String actorDisplayName;

    /**
     * Username of the actor (cached for performance).
     */
    @Column(name = "actor_username")
    private String actorUsername;

    /**
     * Avatar URL of the actor (cached for performance).
     */
    @Column(name = "actor_avatar_url")
    private String actorAvatarUrl;

    /**
     * Related activity ID (for likes, comments on activities).
     */
    @Column(name = "activity_id")
    private UUID activityId;

    /**
     * Activity title (cached for performance).
     */
    @Column(name = "activity_title")
    private String activityTitle;

    /**
     * Related comment ID (if notification is about a comment).
     */
    @Column(name = "comment_id")
    private UUID commentId;

    /**
     * Comment text preview (cached for performance).
     */
    @Column(name = "comment_text", length = 200)
    private String commentText;

    /**
     * Whether the notification has been read.
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /**
     * Timestamp when notification was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when notification was read.
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * Types of notifications that can be sent.
     */
    public enum NotificationType {
        /**
         * Someone liked your activity.
         */
        ACTIVITY_LIKED,

        /**
         * Someone commented on your activity.
         */
        ACTIVITY_COMMENTED,

        /**
         * Someone followed you.
         */
        USER_FOLLOWED,

        /**
         * Someone accepted your follow request.
         */
        FOLLOW_ACCEPTED,

        /**
         * Someone shared/announced your activity.
         */
        ACTIVITY_SHARED,

        /**
         * Someone mentioned you in a comment.
         */
        MENTIONED_IN_COMMENT
    }

    /**
     * Mark this notification as read.
     */
    public void markAsRead() {
        this.read = true;
        this.readAt = LocalDateTime.now();
    }
}
