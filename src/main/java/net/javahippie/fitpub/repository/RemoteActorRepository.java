package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.RemoteActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RemoteActor entity operations.
 */
@Repository
public interface RemoteActorRepository extends JpaRepository<RemoteActor, UUID> {

    /**
     * Find a remote actor by their ActivityPub URI.
     *
     * @param actorUri the actor URI
     * @return the remote actor if found
     */
    Optional<RemoteActor> findByActorUri(String actorUri);

    /**
     * Check if a remote actor exists by their actor URI.
     *
     * @param actorUri the actor URI
     * @return true if the actor exists
     */
    boolean existsByActorUri(String actorUri);
}
