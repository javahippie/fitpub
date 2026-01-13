package net.javahippie.fitpub.model.activitypub;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a WebFinger resource response (RFC 7033).
 *
 * Example:
 * {
 *   "subject": "acct:username@domain.com",
 *   "aliases": ["https://domain.com/users/username"],
 *   "links": [
 *     {
 *       "rel": "self",
 *       "type": "application/activity+json",
 *       "href": "https://domain.com/users/username"
 *     },
 *     {
 *       "rel": "http://webfinger.net/rel/profile-page",
 *       "type": "text/html",
 *       "href": "https://domain.com/@username"
 *     }
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebFingerResource {

    /**
     * The subject identifier (usually in acct: URI format).
     * Example: "acct:username@domain.com"
     */
    private String subject;

    /**
     * Alternative URIs for the same resource.
     */
    private List<String> aliases;

    /**
     * Links to related resources.
     */
    private List<WebFingerLink> links;

    /**
     * Optional properties (key-value pairs).
     */
    private Map<String, String> properties;
}
