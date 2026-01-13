package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.RemoteActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for RemoteActivity entity.
 * Provides methods for querying remote fitness activities from federated instances.
 */
@Repository
public interface RemoteActivityRepository extends JpaRepository<RemoteActivity, UUID> {

    /**
     * Finds a remote activity by its ActivityPub URI.
     * Used for deduplication and activity lookup.
     *
     * @param activityUri the ActivityPub activity URI
     * @return optional remote activity
     */
    Optional<RemoteActivity> findByActivityUri(String activityUri);

    /**
     * Checks if a remote activity exists by its ActivityPub URI.
     * Used for deduplication before storing.
     *
     * @param activityUri the ActivityPub activity URI
     * @return true if exists
     */
    boolean existsByActivityUri(String activityUri);

    /**
     * Finds remote activities by a specific remote actor.
     *
     * @param remoteActorUri the remote actor URI
     * @param pageable pagination parameters
     * @return page of remote activities
     */
    Page<RemoteActivity> findByRemoteActorUri(String remoteActorUri, Pageable pageable);

    /**
     * Finds remote activities from multiple actors with specific visibility levels.
     * Used for federated timeline - shows activities from users you follow.
     *
     * @param actorUris list of remote actor URIs
     * @param visibilities list of allowed visibility levels (PUBLIC, FOLLOWERS)
     * @param pageable pagination parameters
     * @return page of remote activities
     */
    @Query("SELECT ra FROM RemoteActivity ra WHERE ra.remoteActorUri IN :actorUris " +
           "AND ra.visibility IN :visibilities " +
           "ORDER BY ra.publishedAt DESC")
    Page<RemoteActivity> findByRemoteActorUriInAndVisibilityIn(
        @Param("actorUris") List<String> actorUris,
        @Param("visibilities") List<RemoteActivity.Visibility> visibilities,
        Pageable pageable
    );

    /**
     * Finds all public remote activities.
     * Used for public timeline.
     *
     * @param pageable pagination parameters
     * @return page of public remote activities
     */
    @Query("SELECT ra FROM RemoteActivity ra WHERE ra.visibility = 'PUBLIC' " +
           "ORDER BY ra.publishedAt DESC")
    Page<RemoteActivity> findAllPublicActivities(Pageable pageable);

    /**
     * Counts remote activities from a specific actor.
     *
     * @param remoteActorUri the remote actor URI
     * @return count of activities
     */
    long countByRemoteActorUri(String remoteActorUri);

    /**
     * Finds activities by type from specific actors.
     * Used for filtering by activity type.
     *
     * @param actorUris list of remote actor URIs
     * @param activityType the activity type (RUN, RIDE, etc.)
     * @param pageable pagination parameters
     * @return page of activities
     */
    @Query("SELECT ra FROM RemoteActivity ra WHERE ra.remoteActorUri IN :actorUris " +
           "AND ra.activityType = :activityType " +
           "ORDER BY ra.publishedAt DESC")
    Page<RemoteActivity> findByRemoteActorUriInAndActivityType(
        @Param("actorUris") List<String> actorUris,
        @Param("activityType") String activityType,
        Pageable pageable
    );

    /**
     * Deletes all remote activities from a specific actor.
     * Used when unfollowing or blocking a remote user.
     *
     * @param remoteActorUri the remote actor URI
     */
    @Modifying
    @Transactional
    void deleteByRemoteActorUri(String remoteActorUri);
}
