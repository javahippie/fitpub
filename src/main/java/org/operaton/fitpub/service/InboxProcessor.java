package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.Comment;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.Like;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.CommentRepository;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.LikeRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Processes incoming ActivityPub activities in the inbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboxProcessor {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FederationService federationService;
    private final ActivityRepository activityRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Process an incoming activity.
     *
     * @param username the local username
     * @param activity the activity to process
     */
    @Transactional
    public void processActivity(String username, Map<String, Object> activity) {
        String type = (String) activity.get("type");
        log.info("Processing {} activity for user {}", type, username);

        switch (type) {
            case "Follow":
                processFollow(username, activity);
                break;
            case "Undo":
                processUndo(username, activity);
                break;
            case "Accept":
                processAccept(username, activity);
                break;
            case "Create":
                processCreate(username, activity);
                break;
            case "Like":
                processLike(username, activity);
                break;
            default:
                log.warn("Unhandled activity type: {}", type);
        }
    }

    /**
     * Process a Follow activity.
     * Remote user wants to follow local user.
     */
    private void processFollow(String username, Map<String, Object> activity) {
        try {
            String activityId = (String) activity.get("id");
            String actor = (String) activity.get("actor");
            String object = (String) activity.get("object");

            // Verify the follow is for the correct local user
            User localUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            String expectedObjectUri = baseUrl + "/users/" + username;
            if (!object.equals(expectedObjectUri)) {
                log.warn("Follow object mismatch. Expected: {}, Got: {}", expectedObjectUri, object);
                return;
            }

            // Fetch remote actor information
            RemoteActor remoteActor = federationService.fetchRemoteActor(actor);

            // Check if follow already exists
            Follow existing = followRepository.findByActivityId(activityId).orElse(null);
            if (existing != null) {
                log.debug("Follow already processed: {}", activityId);
                return;
            }

            // Create follow relationship (as the object of the follow, from remote actor's perspective)
            // Here we store that the remote actor is following our local user
            // Note: We're storing it from the perspective of "who is following whom"
            Follow follow = Follow.builder()
                .followerId(null) // Remote actor, so no local user ID
                .remoteActorUri(actor) // The remote actor who is following
                .followingActorUri(expectedObjectUri) // The local user being followed
                .status(Follow.FollowStatus.ACCEPTED) // Auto-accept for now
                .activityId(activityId)
                .build();

            followRepository.save(follow);

            // Send Accept activity
            federationService.sendAcceptActivity(follow, localUser);

            log.info("Processed Follow from {} for user {}", actor, username);

        } catch (Exception e) {
            log.error("Error processing Follow activity", e);
        }
    }

    /**
     * Process an Undo activity (e.g., unfollow, unlike).
     */
    private void processUndo(String username, Map<String, Object> activity) {
        try {
            String actor = (String) activity.get("actor");
            Object object = activity.get("object");
            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> undoObject = (Map<String, Object>) object;
                String type = (String) undoObject.get("type");

                if ("Follow".equals(type)) {
                    String activityId = (String) undoObject.get("id");
                    Follow follow = followRepository.findByActivityId(activityId).orElse(null);
                    if (follow != null) {
                        followRepository.delete(follow);
                        log.info("Processed Undo Follow: {}", activityId);
                    }
                } else if ("Like".equals(type)) {
                    String objectUri = (String) undoObject.get("object");
                    UUID activityId = extractActivityIdFromUri(objectUri);
                    if (activityId != null) {
                        likeRepository.deleteByActivityIdAndRemoteActorUri(activityId, actor);
                        log.info("Processed Undo Like from {} for activity {}", actor, activityId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Undo activity", e);
        }
    }

    /**
     * Process an Accept activity (e.g., follow request accepted).
     */
    private void processAccept(String username, Map<String, Object> activity) {
        try {
            Object object = activity.get("object");
            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> acceptObject = (Map<String, Object>) object;
                String activityId = (String) acceptObject.get("id");

                Follow follow = followRepository.findByActivityId(activityId).orElse(null);
                if (follow != null && follow.getStatus() == Follow.FollowStatus.PENDING) {
                    follow.setStatus(Follow.FollowStatus.ACCEPTED);
                    followRepository.save(follow);
                    log.info("Follow request accepted: {}", activityId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Accept activity", e);
        }
    }

    /**
     * Process a Create activity (e.g., new post, comment).
     */
    private void processCreate(String username, Map<String, Object> activity) {
        try {
            String actor = (String) activity.get("actor");
            Object object = activity.get("object");

            if (!(object instanceof Map)) {
                log.warn("Create activity object is not a Map");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> noteObject = (Map<String, Object>) object;
            String type = (String) noteObject.get("type");

            if (!"Note".equals(type)) {
                log.debug("Received Create activity with non-Note object type: {}", type);
                return;
            }

            String inReplyTo = (String) noteObject.get("inReplyTo");
            if (inReplyTo == null) {
                log.debug("Create/Note is not a reply, ignoring");
                return;
            }

            // Extract activity ID from inReplyTo URI
            UUID activityId = extractActivityIdFromUri(inReplyTo);
            if (activityId == null) {
                log.warn("Could not extract activity ID from inReplyTo: {}", inReplyTo);
                return;
            }

            // Check if activity exists
            Activity localActivity = activityRepository.findById(activityId).orElse(null);
            if (localActivity == null) {
                log.warn("Activity not found: {}", activityId);
                return;
            }

            // Fetch remote actor information
            RemoteActor remoteActor = federationService.fetchRemoteActor(actor);

            // Get comment content
            String content = (String) noteObject.get("content");
            if (content == null || content.trim().isEmpty()) {
                log.warn("Create/Note has no content");
                return;
            }

            // Check if comment already exists by activityPubId
            String commentId = (String) noteObject.get("id");
            if (commentRepository.findByActivityPubId(commentId).isPresent()) {
                log.debug("Comment already exists with activityPubId: {}", commentId);
                return;
            }

            // Create comment
            Comment comment = Comment.builder()
                .activityId(activityId)
                .userId(null) // Remote actor, not a local user
                .remoteActorUri(actor)
                .displayName(remoteActor.getDisplayName() != null ? remoteActor.getDisplayName() : remoteActor.getUsername())
                .avatarUrl(remoteActor.getAvatarUrl())
                .content(stripHtml(content))
                .activityPubId(commentId)
                .build();

            commentRepository.save(comment);
            log.info("Processed Create/Note (comment) from {} for activity {}", actor, activityId);

        } catch (Exception e) {
            log.error("Error processing Create activity", e);
        }
    }

    /**
     * Process a Like activity.
     */
    private void processLike(String username, Map<String, Object> activity) {
        try {
            String actor = (String) activity.get("actor");
            String objectUri = (String) activity.get("object");

            log.debug("Received Like from {} for object {}", actor, objectUri);

            // Extract activity ID from the object URI
            // Expected format: https://fitpub.example/activities/{uuid}
            UUID activityId = extractActivityIdFromUri(objectUri);
            if (activityId == null) {
                log.warn("Could not extract activity ID from object URI: {}", objectUri);
                return;
            }

            // Check if the activity exists
            Activity localActivity = activityRepository.findById(activityId).orElse(null);
            if (localActivity == null) {
                log.warn("Activity not found: {}", activityId);
                return;
            }

            // Fetch remote actor information
            RemoteActor remoteActor = federationService.fetchRemoteActor(actor);

            // Check if like already exists
            if (likeRepository.existsByActivityIdAndRemoteActorUri(activityId, actor)) {
                log.debug("Like already exists from {} for activity {}", actor, activityId);
                return;
            }

            // Create the like
            Like like = Like.builder()
                .activityId(activityId)
                .userId(null) // Remote actor, not a local user
                .remoteActorUri(actor)
                .displayName(remoteActor.getDisplayName() != null ? remoteActor.getDisplayName() : remoteActor.getUsername())
                .avatarUrl(remoteActor.getAvatarUrl())
                .build();

            likeRepository.save(like);
            log.info("Processed Like from {} for activity {}", actor, activityId);

        } catch (Exception e) {
            log.error("Error processing Like activity", e);
        }
    }

    /**
     * Extract activity UUID from URI.
     * Expects format: https://fitpub.example/activities/{uuid}
     */
    private UUID extractActivityIdFromUri(String uri) {
        try {
            if (uri == null || !uri.startsWith(baseUrl + "/activities/")) {
                return null;
            }
            String uuidStr = uri.substring((baseUrl + "/activities/").length());
            return UUID.fromString(uuidStr);
        } catch (Exception e) {
            log.warn("Failed to extract activity ID from URI: {}", uri, e);
            return null;
        }
    }

    /**
     * Strip HTML tags from content.
     * Mastodon sends HTML formatted content, we want plain text.
     */
    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        // Replace common HTML tags with appropriate text
        String text = html
            .replaceAll("<br\\s*/?>", "\n")
            .replaceAll("<p>", "")
            .replaceAll("</p>", "\n")
            .replaceAll("<[^>]+>", ""); // Remove all other HTML tags

        // Decode HTML entities
        text = text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&");

        return text.trim();
    }
}
