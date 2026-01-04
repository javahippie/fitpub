package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Follow entity operations.
 */
@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    /**
     * Find a follow relationship by follower and following actor URI.
     *
     * @param followerId the follower's user ID
     * @param followingActorUri the actor URI being followed
     * @return the follow relationship if it exists
     */
    Optional<Follow> findByFollowerIdAndFollowingActorUri(UUID followerId, String followingActorUri);

    /**
     * Find all follow relationships for a follower.
     *
     * @param followerId the follower's user ID
     * @return list of follow relationships
     */
    List<Follow> findByFollowerId(UUID followerId);

    /**
     * Find all accepted followers of a user by their actor URI.
     *
     * @param actorUri the actor URI being followed
     * @return list of accepted follow relationships
     */
    @Query("SELECT f FROM Follow f WHERE f.followingActorUri = :actorUri AND f.status = 'ACCEPTED'")
    List<Follow> findAcceptedFollowersByActorUri(@Param("actorUri") String actorUri);

    /**
     * Count accepted followers for an actor URI.
     *
     * @param actorUri the actor URI
     * @return count of accepted followers
     */
    @Query("SELECT COUNT(f) FROM Follow f WHERE f.followingActorUri = :actorUri AND f.status = 'ACCEPTED'")
    long countAcceptedFollowersByActorUri(@Param("actorUri") String actorUri);

    /**
     * Find all accepted following relationships for a user.
     *
     * @param followerId the follower's user ID
     * @return list of accepted follow relationships
     */
    @Query("SELECT f FROM Follow f WHERE f.followerId = :followerId AND f.status = 'ACCEPTED'")
    List<Follow> findAcceptedFollowingByUserId(@Param("followerId") UUID followerId);

    /**
     * Count accepted following relationships for a user.
     *
     * @param followerId the follower's user ID
     * @return count of accepted following relationships
     */
    @Query("SELECT COUNT(f) FROM Follow f WHERE f.followerId = :followerId AND f.status = 'ACCEPTED'")
    long countAcceptedFollowingByUserId(@Param("followerId") UUID followerId);

    /**
     * Find a follow by its Activity ID.
     *
     * @param activityId the ActivityPub Follow activity ID
     * @return the follow relationship if it exists
     */
    Optional<Follow> findByActivityId(String activityId);

    /**
     * Find a follow relationship by remote actor URI and following actor URI.
     * Used to check if a remote user is following a local user.
     *
     * @param remoteActorUri the remote actor's URI (follower)
     * @param followingActorUri the actor URI being followed
     * @return the follow relationship if it exists
     */
    Optional<Follow> findByRemoteActorUriAndFollowingActorUri(String remoteActorUri, String followingActorUri);

    /**
     * Delete all follow records where the given actor URI is being followed.
     * This is used when a user account is deleted to clean up records where
     * the user was being followed (since following_actor_uri is a string, not FK).
     *
     * @param followingActorUri the actor URI being followed
     * @return number of deleted records
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Follow f WHERE f.followingActorUri = :followingActorUri")
    int deleteByFollowingActorUri(@Param("followingActorUri") String followingActorUri);

    /**
     * Delete all follow records for a remote actor (when remote account is deleted).
     * This cleans up follows where the remote actor was the follower.
     *
     * @param remoteActorUri the remote actor's URI
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Follow f WHERE f.remoteActorUri = :remoteActorUri")
    void deleteByRemoteActorUri(@Param("remoteActorUri") String remoteActorUri);
}
