package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.RemoteActorRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.security.HttpSignatureValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for ActivityPub federation operations.
 * Handles outbound activities and remote actor management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FederationService {

    private final RemoteActorRepository remoteActorRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final HttpSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Fetch and cache a remote actor's information.
     *
     * @param actorUri the actor's URI
     * @return the cached remote actor
     */
    @Transactional
    public RemoteActor fetchRemoteActor(String actorUri) {
        log.info("Fetching remote actor: {}", actorUri);

        // Check if we have a cached version
        RemoteActor cached = remoteActorRepository.findByActorUri(actorUri).orElse(null);
        if (cached != null && cached.getLastFetchedAt() != null &&
            cached.getLastFetchedAt().isAfter(Instant.now().minusSeconds(3600))) {
            log.debug("Using cached actor info for: {}", actorUri);
            return cached;
        }

        try {
            // Fetch actor information
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/activity+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                actorUri,
                HttpMethod.GET,
                entity,
                Map.class
            );

            Map<String, Object> actorData = response.getBody();
            if (actorData == null) {
                throw new RuntimeException("Empty actor response from: " + actorUri);
            }

            // Parse actor data
            String username = extractUsername(actorUri, actorData);
            String domain = URI.create(actorUri).getHost();
            String inboxUrl = (String) actorData.get("inbox");
            String outboxUrl = (String) actorData.get("outbox");
            String sharedInboxUrl = extractSharedInbox(actorData);
            String publicKey = extractPublicKey(actorData);
            String publicKeyId = extractPublicKeyId(actorData);

            // Update or create remote actor
            RemoteActor actor;
            if (cached != null) {
                actor = cached;
            } else {
                actor = new RemoteActor();
                actor.setActorUri(actorUri);
            }

            actor.setUsername(username);
            actor.setDomain(domain);
            actor.setInboxUrl(inboxUrl);
            actor.setOutboxUrl(outboxUrl);
            actor.setSharedInboxUrl(sharedInboxUrl);
            actor.setPublicKey(publicKey);
            actor.setPublicKeyId(publicKeyId);
            actor.setDisplayName((String) actorData.get("name"));
            actor.setAvatarUrl(extractAvatarUrl(actorData));
            actor.setSummary((String) actorData.get("summary"));
            actor.setLastFetchedAt(Instant.now());

            return remoteActorRepository.save(actor);

        } catch (Exception e) {
            log.error("Failed to fetch remote actor: {}", actorUri, e);
            throw new RuntimeException("Failed to fetch remote actor: " + actorUri, e);
        }
    }

    /**
     * Send an Accept activity in response to a Follow.
     *
     * @param follow the follow relationship
     * @param localUser the local user being followed
     */
    @Transactional
    public void sendAcceptActivity(Follow follow, User localUser) {
        try {
            // Get the remote actor who sent the follow request
            String remoteActorUri = follow.getRemoteActorUri();
            if (remoteActorUri == null) {
                log.error("Cannot send Accept: Follow has no remote actor URI");
                return;
            }

            RemoteActor remoteActor = fetchRemoteActor(remoteActorUri);

            String acceptId = baseUrl + "/activities/" + UUID.randomUUID();
            String actorUri = baseUrl + "/users/" + localUser.getUsername();

            Map<String, Object> acceptActivity = new HashMap<>();
            acceptActivity.put("@context", "https://www.w3.org/ns/activitystreams");
            acceptActivity.put("type", "Accept");
            acceptActivity.put("id", acceptId);
            acceptActivity.put("actor", actorUri);
            acceptActivity.put("object", follow.getActivityId());

            sendActivity(remoteActor.getInboxUrl(), acceptActivity, localUser);
            log.info("Sent Accept activity to: {}", remoteActor.getActorUri());

        } catch (Exception e) {
            log.error("Failed to send Accept activity for follow: {}", follow.getId(), e);
        }
    }

    /**
     * Send an activity to a remote inbox.
     *
     * @param inboxUrl the remote inbox URL
     * @param activity the activity to send
     * @param sender the local user sending the activity
     */
    public void sendActivity(String inboxUrl, Map<String, Object> activity, User sender) {
        try {
            String activityJson = objectMapper.writeValueAsString(activity);

            // Generate HTTP signature with all required headers
            // This calculates what the signature SHOULD be, including the host from the URL
            HttpSignatureValidator.SignatureHeaders signatureHeaders = signatureValidator.signRequest(
                HttpMethod.POST.name(),
                inboxUrl,
                activityJson,
                sender.getPrivateKey(),
                baseUrl + "/users/" + sender.getUsername() + "#main-key"
            );

            log.debug("=== HTTP Signature Debug ===");
            log.debug("Inbox URL: {}", inboxUrl);
            log.debug("Expected Host: {}", signatureHeaders.host);
            log.debug("Date: {}", signatureHeaders.date);
            log.debug("Digest: {}", signatureHeaders.digest);
            log.debug("Signature: {}", signatureHeaders.signature);
            log.debug("KeyId: {}", baseUrl + "/users/" + sender.getUsername() + "#main-key");
            log.debug("Activity JSON length: {}", activityJson.length());
            log.debug("===========================");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/activity+json");
            headers.set("Accept", "application/activity+json");

            // CRITICAL: Set the Host header to exactly match what was used in the signature
            // We MUST set this explicitly, otherwise RestTemplate might set it differently
            // (e.g., with port number) and the signature validation will fail
            headers.set("Host", signatureHeaders.host);

            // Set the Date and Digest headers that were used in the signature
            headers.set("Date", signatureHeaders.date);
            headers.set("Digest", signatureHeaders.digest);

            // Set the Signature header
            headers.set("Signature", signatureHeaders.signature);

            HttpEntity<String> entity = new HttpEntity<>(activityJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(inboxUrl, entity, String.class);
            log.info("Sent activity to: {} - Status: {}", inboxUrl, response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to send activity to: {}", inboxUrl, e);
            throw new RuntimeException("Failed to send activity", e);
        }
    }

    /**
     * Get all follower inbox URLs for a local user.
     *
     * @param userId the local user's ID
     * @return list of inbox URLs
     */
    @Transactional(readOnly = true)
    public List<String> getFollowerInboxes(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String actorUri = baseUrl + "/users/" + user.getUsername();
        List<Follow> followers = followRepository.findAcceptedFollowersByActorUri(actorUri);

        return followers.stream()
            .map(follow -> {
                try {
                    RemoteActor actor = remoteActorRepository.findByActorUri(follow.getRemoteActorUri())
                        .orElseGet(() -> fetchRemoteActor(follow.getRemoteActorUri()));
                    return actor.getSharedInboxUrl() != null ? actor.getSharedInboxUrl() : actor.getInboxUrl();
                } catch (Exception e) {
                    log.error("Failed to get inbox for follower: {}", follow.getRemoteActorUri(), e);
                    return null;
                }
            })
            .filter(inbox -> inbox != null)
            .distinct()
            .toList();
    }

    /**
     * Send a Create activity for a new post/object.
     *
     * @param objectId the ID of the created object
     * @param object the object being created (activity, note, etc.)
     * @param sender the local user creating the object
     * @param toPublic whether to send to public (CC followers)
     */
    @Transactional
    public void sendCreateActivity(String objectId, Map<String, Object> object, User sender, boolean toPublic) {
        try {
            String createId = baseUrl + "/activities/create/" + UUID.randomUUID();
            String actorUri = baseUrl + "/users/" + sender.getUsername();

            Map<String, Object> createActivity = new HashMap<>();
            createActivity.put("@context", "https://www.w3.org/ns/activitystreams");
            createActivity.put("type", "Create");
            createActivity.put("id", createId);
            createActivity.put("actor", actorUri);
            createActivity.put("published", Instant.now().toString());
            createActivity.put("object", object);

            if (toPublic) {
                createActivity.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
                createActivity.put("cc", List.of(actorUri + "/followers"));
            } else {
                createActivity.put("to", List.of(actorUri + "/followers"));
            }

            // Send to all follower inboxes
            List<String> inboxes = getFollowerInboxes(sender.getId());
            for (String inbox : inboxes) {
                sendActivity(inbox, createActivity, sender);
            }

            log.info("Sent Create activity for object: {} to {} inboxes", objectId, inboxes.size());

        } catch (Exception e) {
            log.error("Failed to send Create activity for object: {}", objectId, e);
        }
    }

    /**
     * Send a Like activity.
     *
     * @param objectUri the URI of the object being liked
     * @param sender the local user liking the object
     */
    @Transactional
    public void sendLikeActivity(String objectUri, User sender) {
        try {
            String likeId = baseUrl + "/activities/like/" + UUID.randomUUID();
            String actorUri = baseUrl + "/users/" + sender.getUsername();

            Map<String, Object> likeActivity = new HashMap<>();
            likeActivity.put("@context", "https://www.w3.org/ns/activitystreams");
            likeActivity.put("type", "Like");
            likeActivity.put("id", likeId);
            likeActivity.put("actor", actorUri);
            likeActivity.put("object", objectUri);
            likeActivity.put("published", Instant.now().toString());

            // Send to all follower inboxes
            List<String> inboxes = getFollowerInboxes(sender.getId());
            for (String inbox : inboxes) {
                sendActivity(inbox, likeActivity, sender);
            }

            log.info("Sent Like activity for object: {} to {} inboxes", objectUri, inboxes.size());

        } catch (Exception e) {
            log.error("Failed to send Like activity for object: {}", objectUri, e);
        }
    }

    /**
     * Send an Undo activity (for unlike, unfollow, etc.).
     *
     * @param originalActivityId the ID of the activity being undone
     * @param originalActivity the original activity being undone
     * @param sender the local user undoing the activity
     */
    @Transactional
    public void sendUndoActivity(String originalActivityId, Map<String, Object> originalActivity, User sender) {
        try {
            String undoId = baseUrl + "/activities/undo/" + UUID.randomUUID();
            String actorUri = baseUrl + "/users/" + sender.getUsername();

            Map<String, Object> undoActivity = new HashMap<>();
            undoActivity.put("@context", "https://www.w3.org/ns/activitystreams");
            undoActivity.put("type", "Undo");
            undoActivity.put("id", undoId);
            undoActivity.put("actor", actorUri);
            undoActivity.put("object", originalActivity);
            undoActivity.put("published", Instant.now().toString());

            // Send to all follower inboxes
            List<String> inboxes = getFollowerInboxes(sender.getId());
            for (String inbox : inboxes) {
                sendActivity(inbox, undoActivity, sender);
            }

            log.info("Sent Undo activity for: {} to {} inboxes", originalActivityId, inboxes.size());

        } catch (Exception e) {
            log.error("Failed to send Undo activity for: {}", originalActivityId, e);
        }
    }

    // Helper methods

    private String extractUsername(String actorUri, Map<String, Object> actorData) {
        String preferredUsername = (String) actorData.get("preferredUsername");
        if (preferredUsername != null) {
            return preferredUsername;
        }
        // Fallback: extract from URI
        return actorUri.substring(actorUri.lastIndexOf("/") + 1);
    }

    private String extractSharedInbox(Map<String, Object> actorData) {
        Object endpoints = actorData.get("endpoints");
        if (endpoints instanceof Map) {
            return (String) ((Map<?, ?>) endpoints).get("sharedInbox");
        }
        return null;
    }

    private String extractPublicKey(Map<String, Object> actorData) {
        Object publicKey = actorData.get("publicKey");
        if (publicKey instanceof Map) {
            return (String) ((Map<?, ?>) publicKey).get("publicKeyPem");
        }
        throw new RuntimeException("No public key found in actor data");
    }

    private String extractPublicKeyId(Map<String, Object> actorData) {
        Object publicKey = actorData.get("publicKey");
        if (publicKey instanceof Map) {
            return (String) ((Map<?, ?>) publicKey).get("id");
        }
        return null;
    }

    private String extractAvatarUrl(Map<String, Object> actorData) {
        Object icon = actorData.get("icon");
        if (icon instanceof Map) {
            return (String) ((Map<?, ?>) icon).get("url");
        }
        return null;
    }
}
