package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;

import java.time.Instant;

/**
 * DTO representing an actor (local user or remote actor) in follower/following lists.
 * Provides a unified representation regardless of whether the actor is local or remote.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorDTO {

    /**
     * The ActivityPub actor URI.
     * Example: https://fitpub.example/users/alice or https://mastodon.social/users/bob
     */
    private String actorUri;

    /**
     * The username.
     * Example: alice
     */
    private String username;

    /**
     * The domain of the actor's server.
     * Example: fitpub.example or mastodon.social
     */
    private String domain;

    /**
     * The full handle in format username@domain.
     * Example: alice@fitpub.example or bob@mastodon.social
     */
    private String handle;

    /**
     * The display name.
     */
    private String displayName;

    /**
     * The actor's avatar URL.
     */
    private String avatarUrl;

    /**
     * The actor's bio/summary.
     */
    private String bio;

    /**
     * Whether this is a local actor (true) or remote actor (false).
     */
    private boolean local;

    /**
     * When the follow relationship was created.
     */
    private Instant followedAt;

    /**
     * Create ActorDTO from a local User entity.
     *
     * @param user the local user
     * @param baseUrl the base URL of this server
     * @param followedAt when the follow relationship was created
     * @return ActorDTO representing the local user
     */
    public static ActorDTO fromLocalUser(User user, String baseUrl, Instant followedAt) {
        String domain = extractDomainFromUrl(baseUrl);
        return ActorDTO.builder()
            .actorUri(user.getActorUri(baseUrl))
            .username(user.getUsername())
            .domain(domain)
            .handle(user.getUsername() + "@" + domain)
            .displayName(user.getDisplayName())
            .avatarUrl(user.getAvatarUrl())
            .bio(user.getBio())
            .local(true)
            .followedAt(followedAt)
            .build();
    }

    /**
     * Create ActorDTO from a RemoteActor entity.
     *
     * @param remoteActor the remote actor
     * @param followedAt when the follow relationship was created
     * @return ActorDTO representing the remote actor
     */
    public static ActorDTO fromRemoteActor(RemoteActor remoteActor, Instant followedAt) {
        return ActorDTO.builder()
            .actorUri(remoteActor.getActorUri())
            .username(remoteActor.getUsername())
            .domain(remoteActor.getDomain())
            .handle(remoteActor.getHandle())
            .displayName(remoteActor.getDisplayName())
            .avatarUrl(remoteActor.getAvatarUrl())
            .bio(remoteActor.getSummary())
            .local(false)
            .followedAt(followedAt)
            .build();
    }

    /**
     * Extract domain from a base URL.
     * Example: http://localhost:8080 -> localhost:8080
     * Example: https://fitpub.example -> fitpub.example
     */
    private static String extractDomainFromUrl(String url) {
        try {
            return url.replaceFirst("^https?://", "");
        } catch (Exception e) {
            return url;
        }
    }
}
