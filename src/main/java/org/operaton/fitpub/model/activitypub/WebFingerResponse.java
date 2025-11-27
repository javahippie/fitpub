package org.operaton.fitpub.model.activitypub;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WebFinger response format (RFC 7033).
 * Used for user discovery in ActivityPub.
 *
 * Spec: https://datatracker.ietf.org/doc/html/rfc7033
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebFingerResponse {

    private String subject;
    private List<String> aliases;
    private List<Link> links;

    /**
     * Creates a WebFinger response for a user.
     */
    public static WebFingerResponse forUser(String username, String domain, String actorUrl) {
        String acctUri = String.format("acct:%s@%s", username, domain);

        return WebFingerResponse.builder()
            .subject(acctUri)
            .aliases(List.of(actorUrl))
            .links(List.of(
                Link.builder()
                    .rel("self")
                    .type("application/activity+json")
                    .href(actorUrl)
                    .build(),
                Link.builder()
                    .rel("http://webfinger.net/rel/profile-page")
                    .type("text/html")
                    .href(actorUrl)
                    .build()
            ))
            .build();
    }

    /**
     * WebFinger link object.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Link {
        private String rel;
        private String type;
        private String href;
    }
}
