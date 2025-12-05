package org.operaton.fitpub.model.activitypub;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ActivityPub Actor object.
 * Represents a user's ActivityPub profile.
 *
 * Spec: https://www.w3.org/TR/activitypub/#actors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Actor {

    @JsonProperty("@context")
    private Object context;

    private String type;
    private String id;
    private String preferredUsername;
    private String name;
    private String summary;
    private String inbox;
    private String outbox;
    private String followers;
    private String following;
    private PublicKey publicKey;
    private Image icon;
    private String url;

    /**
     * Creates an Actor from a User entity.
     */
    public static Actor fromUser(org.operaton.fitpub.model.entity.User user, String baseUrl) {
        String actorUri = user.getActorUri(baseUrl);

        return Actor.builder()
            .context(List.of(
                "https://www.w3.org/ns/activitystreams",
                "https://w3id.org/security/v1"
            ))
            .type("Person")
            .id(actorUri)
            .preferredUsername(user.getUsername())
            .name(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
            .summary(user.getBio())
            .inbox(actorUri + "/inbox")
            .outbox(actorUri + "/outbox")
            .followers(actorUri + "/followers")
            .following(actorUri + "/following")
            .publicKey(PublicKey.builder()
                .id(actorUri + "#main-key")
                .owner(actorUri)
                .publicKeyPem(user.getPublicKey())
                .build())
            .icon(user.getAvatarUrl() != null ? Image.builder()
                .type("Image")
                .mediaType("image/jpeg")
                .url(user.getAvatarUrl())
                .build() : null)
            .url(baseUrl + "/users/" + user.getUsername())
            .build();
    }

    /**
     * Public key object for HTTP signature verification.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PublicKey {
        private String id;
        private String owner;
        private String publicKeyPem;
    }

    /**
     * Image object for avatars.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Image {
        private String type;
        private String mediaType;
        private String url;
    }
}
