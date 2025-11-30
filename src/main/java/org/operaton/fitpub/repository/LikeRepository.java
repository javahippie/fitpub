package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Like entity operations.
 */
@Repository
public interface LikeRepository extends JpaRepository<Like, UUID> {

    /**
     * Find all likes for an activity.
     *
     * @param activityId the activity ID
     * @return list of likes
     */
    List<Like> findByActivityIdOrderByCreatedAtDesc(UUID activityId);

    /**
     * Count likes for an activity.
     *
     * @param activityId the activity ID
     * @return number of likes
     */
    long countByActivityId(UUID activityId);

    /**
     * Find a like by activity and local user.
     *
     * @param activityId the activity ID
     * @param userId the user ID
     * @return the like if exists
     */
    Optional<Like> findByActivityIdAndUserId(UUID activityId, UUID userId);

    /**
     * Find a like by activity and remote actor.
     *
     * @param activityId the activity ID
     * @param remoteActorUri the remote actor URI
     * @return the like if exists
     */
    Optional<Like> findByActivityIdAndRemoteActorUri(UUID activityId, String remoteActorUri);

    /**
     * Find a like by ActivityPub ID.
     *
     * @param activityPubId the ActivityPub Like activity ID
     * @return the like if exists
     */
    Optional<Like> findByActivityPubId(String activityPubId);

    /**
     * Check if a local user has liked an activity.
     *
     * @param activityId the activity ID
     * @param userId the user ID
     * @return true if liked
     */
    boolean existsByActivityIdAndUserId(UUID activityId, UUID userId);

    /**
     * Check if a remote actor has liked an activity.
     *
     * @param activityId the activity ID
     * @param remoteActorUri the remote actor URI
     * @return true if liked
     */
    boolean existsByActivityIdAndRemoteActorUri(UUID activityId, String remoteActorUri);

    /**
     * Delete a like by activity and user.
     *
     * @param activityId the activity ID
     * @param userId the user ID
     */
    void deleteByActivityIdAndUserId(UUID activityId, UUID userId);

    /**
     * Delete a like by activity and remote actor.
     *
     * @param activityId the activity ID
     * @param remoteActorUri the remote actor URI
     */
    void deleteByActivityIdAndRemoteActorUri(UUID activityId, String remoteActorUri);
}
