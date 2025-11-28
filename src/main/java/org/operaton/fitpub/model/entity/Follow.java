package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a follow relationship between users (local or remote).
 * Supports both local-to-local and local-to-remote follow relationships.
 */
@Entity
@Table(name = "follows", indexes = {
    @Index(name = "idx_follower_id", columnList = "follower_id"),
    @Index(name = "idx_following_actor_uri", columnList = "following_actor_uri")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The local user who is following.
     */
    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    /**
     * The ActivityPub actor URI being followed (local or remote).
     * Example: https://mastodon.social/users/alice
     */
    @Column(name = "following_actor_uri", nullable = false, length = 512)
    private String followingActorUri;

    /**
     * Status of the follow relationship.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FollowStatus status = FollowStatus.PENDING;

    /**
     * The ActivityPub Follow activity ID.
     * Used to reference the original Follow activity.
     */
    @Column(name = "activity_id", length = 512)
    private String activityId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Status of a follow relationship.
     */
    public enum FollowStatus {
        /** Follow request sent, awaiting acceptance */
        PENDING,
        /** Follow request accepted, relationship active */
        ACCEPTED,
        /** Follow request rejected */
        REJECTED
    }
}
