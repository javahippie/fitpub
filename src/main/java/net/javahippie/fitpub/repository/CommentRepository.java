package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Comment entity operations.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Find all non-deleted comments for an activity, ordered by creation time.
     *
     * @param activityId the activity ID
     * @param pageable pagination parameters
     * @return page of comments
     */
    @Query("SELECT c FROM Comment c WHERE c.activityId = :activityId AND c.deleted = false ORDER BY c.createdAt ASC")
    Page<Comment> findByActivityIdAndNotDeleted(@Param("activityId") UUID activityId, Pageable pageable);

    /**
     * Count non-deleted comments for an activity.
     *
     * @param activityId the activity ID
     * @return number of comments
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.activityId = :activityId AND c.deleted = false")
    long countByActivityIdAndNotDeleted(@Param("activityId") UUID activityId);

    /**
     * Find a comment by ActivityPub ID.
     *
     * @param activityPubId the ActivityPub Note/Create activity ID
     * @return the comment if exists
     */
    Optional<Comment> findByActivityPubId(String activityPubId);

    /**
     * Find all comments by a local user.
     *
     * @param userId the user ID
     * @return list of comments
     */
    List<Comment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find all comments by a remote actor.
     * Used when processing remote actor deletion.
     *
     * @param remoteActorUri the remote actor's URI
     * @return list of comments
     */
    List<Comment> findByRemoteActorUri(String remoteActorUri);
}
