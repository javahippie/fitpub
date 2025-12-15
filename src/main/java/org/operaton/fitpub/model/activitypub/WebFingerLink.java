package org.operaton.fitpub.model.activitypub;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a link in a WebFinger response (RFC 7033).
 *
 * Example:
 * {
 *   "rel": "self",
 *   "type": "application/activity+json",
 *   "href": "https://fitpub.example/users/username"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebFingerLink {

    /**
     * The relationship type (e.g., "self", "http://webfinger.net/rel/profile-page").
     */
    private String rel;

    /**
     * The media type of the linked resource (e.g., "application/activity+json").
     */
    private String type;

    /**
     * The URL of the linked resource.
     */
    private String href;

    /**
     * Optional template for the link (used for some link types).
     */
    private String template;

    /**
     * Optional titles for the link in different languages.
     */
    private java.util.Map<String, String> titles;

    /**
     * Optional properties for the link.
     */
    private java.util.Map<String, String> properties;
}
