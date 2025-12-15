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

import java.time.Instant;
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
    private final NotificationService notificationService;
    private final org.operaton.fitpub.repository.RemoteActivityRepository remoteActivityRepository;
    private final org.operaton.fitpub.repository.RemoteActorRepository remoteActorRepository;

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

            // Create notification for followed user
            notificationService.createUserFollowedNotification(localUser, actor);

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
            String activityId = null;

            // Handle both embedded object (Map) and reference (String)
            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> acceptObject = (Map<String, Object>) object;
                activityId = (String) acceptObject.get("id");
            } else if (object instanceof String) {
                activityId = (String) object;
            }

            if (activityId != null) {
                Follow follow = followRepository.findByActivityId(activityId).orElse(null);
                if (follow != null && follow.getStatus() == Follow.FollowStatus.PENDING) {
                    // Update follow status to ACCEPTED
                    follow.setStatus(Follow.FollowStatus.ACCEPTED);
                    followRepository.save(follow);
                    log.info("Follow request accepted: {}", activityId);

                    // Create notification for the follower
                    // The follower is the local user who initiated the follow request
                    UUID followerId = follow.getFollowerId();
                    if (followerId != null) {
                        User follower = userRepository.findById(followerId).orElse(null);
                        if (follower != null) {
                            String remoteActorUri = follow.getFollowingActorUri();
                            notificationService.createFollowAcceptedNotification(
                                follower.getId(),
                                remoteActorUri,
                                activityId
                            );
                            log.info("Created follow accepted notification for user {}", follower.getUsername());
                        }
                    }
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
                // Standalone Note activity - could be a remote workout/activity
                processRemoteActivity(username, actor, noteObject);
            } else {
                // Note with inReplyTo - this is a comment
                processComment(username, actor, noteObject, inReplyTo);
            }
        } catch (Exception e) {
            log.error("Error processing Create activity", e);
        }
    }

    /**
     * Process a comment (Note with inReplyTo).
     */
    private void processComment(String username, String actor, Map<String, Object> noteObject, String inReplyTo) {
        try {
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

            // Create notification for activity owner
            notificationService.createActivityCommentedNotification(localActivity, comment, actor);

        } catch (Exception e) {
            log.error("Error processing comment", e);
        }
    }

    /**
     * Process a remote activity (standalone Note representing a workout/fitness activity).
     */
    private void processRemoteActivity(String username, String actor, Map<String, Object> noteObject) {
        try {
            String activityUri = (String) noteObject.get("id");
            if (activityUri == null) {
                log.warn("Remote activity has no id");
                return;
            }

            // Check if activity already exists (deduplication)
            if (remoteActivityRepository.existsByActivityUri(activityUri)) {
                log.debug("Remote activity already exists: {}", activityUri);
                return;
            }

            // Fetch and cache remote actor
            RemoteActor remoteActor = federationService.fetchRemoteActor(actor);

            // Check if local user follows this remote actor
            User localUser = userRepository.findByUsername(username).orElse(null);
            if (localUser == null) {
                log.warn("Local user not found: {}", username);
                return;
            }

            boolean isFollowing = followRepository.findByFollowerIdAndFollowingActorUri(
                localUser.getId(), actor
            ).map(follow -> follow.getStatus() == Follow.FollowStatus.ACCEPTED).orElse(false);

            if (!isFollowing) {
                log.debug("Local user {} is not following {}, ignoring activity", username, actor);
                return;
            }

            // Extract workout metadata
            Map<String, Object> workoutData = extractWorkoutData(noteObject);
            Map<String, String> attachments = extractAttachments(noteObject);
            org.operaton.fitpub.model.entity.RemoteActivity.Visibility visibility = determineVisibility(noteObject);

            // Parse published timestamp
            String publishedStr = (String) noteObject.get("published");
            Instant publishedAt = publishedStr != null ? Instant.parse(publishedStr) : Instant.now();

            // Build RemoteActivity entity
            org.operaton.fitpub.model.entity.RemoteActivity remoteActivity = org.operaton.fitpub.model.entity.RemoteActivity.builder()
                .activityUri(activityUri)
                .remoteActorUri(actor)
                .activityType((String) workoutData.get("activityType"))
                .title((String) noteObject.getOrDefault("name", noteObject.getOrDefault("summary", "Untitled Activity")))
                .description(stripHtml((String) noteObject.get("content")))
                .publishedAt(publishedAt)
                .totalDistance(parseLong(workoutData.get("distance")))
                .totalDurationSeconds(parseDurationSeconds((String) workoutData.get("duration")))
                .elevationGain(parseInteger(workoutData.get("elevationGain")))
                .averagePaceSeconds(parseDurationSeconds((String) workoutData.get("averagePace")))
                .averageHeartRate(parseInteger(workoutData.get("averageHeartRate")))
                .maxSpeed(parseDouble(workoutData.get("maxSpeed")))
                .averageSpeed(parseDouble(workoutData.get("averageSpeed")))
                .calories(parseInteger(workoutData.get("calories")))
                .mapImageUrl(attachments.get("mapImage"))
                .trackGeojsonUrl(attachments.get("trackGeojson"))
                .visibility(visibility)
                .activityPubObject(serializeToJson(noteObject))
                .build();

            remoteActivityRepository.save(remoteActivity);
            log.info("Stored remote activity from {}: {} ({})", remoteActor.getUsername(), remoteActivity.getTitle(), activityUri);

        } catch (Exception e) {
            log.error("Error processing remote activity", e);
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

            // Create notification for activity owner
            notificationService.createActivityLikedNotification(localActivity, actor);

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

    // ==================== Remote Activity Helper Methods ====================

    /**
     * Extract workout/fitness data from a Note object.
     * Looks for a "workoutData" extension field containing structured fitness metrics.
     */
    private Map<String, Object> extractWorkoutData(Map<String, Object> noteObject) {
        Map<String, Object> workoutData = new java.util.HashMap<>();

        // Check for custom workoutData extension (FitPub-specific)
        Object workoutDataObj = noteObject.get("workoutData");
        if (workoutDataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) workoutDataObj;
            workoutData.putAll(data);
        }

        // Fallback: Try to extract from summary or content
        String summary = (String) noteObject.get("summary");
        if (summary != null) {
            // Parse summary like "10.2 km • 48:23 • 4:44/km pace"
            workoutData.putIfAbsent("activityType", guessActivityType(summary));
        }

        return workoutData;
    }

    /**
     * Extract attachment URLs (map image, GeoJSON) from a Note object.
     */
    private Map<String, String> extractAttachments(Map<String, Object> noteObject) {
        Map<String, String> attachments = new java.util.HashMap<>();

        Object attachmentObj = noteObject.get("attachment");
        if (attachmentObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> attachmentList = (java.util.List<Object>) attachmentObj;

            for (Object item : attachmentList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attach = (Map<String, Object>) item;

                    String type = (String) attach.get("type");
                    String mediaType = (String) attach.get("mediaType");
                    String url = (String) attach.get("url");
                    String name = (String) attach.get("name");

                    if (url != null) {
                        // Map image
                        if ("Image".equals(type) && (mediaType != null && mediaType.startsWith("image/"))) {
                            if (name != null && name.toLowerCase().contains("map")) {
                                attachments.put("mapImage", url);
                            }
                        }
                        // GeoJSON track
                        else if ("Document".equals(type) && "application/geo+json".equals(mediaType)) {
                            attachments.put("trackGeojson", url);
                        }
                    }
                }
            }
        }

        return attachments;
    }

    /**
     * Determine visibility from ActivityPub "to" and "cc" fields.
     */
    private org.operaton.fitpub.model.entity.RemoteActivity.Visibility determineVisibility(Map<String, Object> noteObject) {
        Object toObj = noteObject.get("to");
        Object ccObj = noteObject.get("cc");

        java.util.List<String> toList = objectToStringList(toObj);
        java.util.List<String> ccList = objectToStringList(ccObj);

        // Check if Public is in "to" or "cc"
        boolean isPublic = toList.contains("https://www.w3.org/ns/activitystreams#Public") ||
                          ccList.contains("https://www.w3.org/ns/activitystreams#Public") ||
                          toList.contains("as:Public") ||
                          ccList.contains("as:Public") ||
                          toList.contains("Public") ||
                          ccList.contains("Public");

        if (isPublic) {
            return org.operaton.fitpub.model.entity.RemoteActivity.Visibility.PUBLIC;
        }

        // If it has followers in to/cc, it's FOLLOWERS visibility
        boolean hasFollowers = toList.stream().anyMatch(s -> s.contains("/followers")) ||
                              ccList.stream().anyMatch(s -> s.contains("/followers"));

        if (hasFollowers) {
            return org.operaton.fitpub.model.entity.RemoteActivity.Visibility.FOLLOWERS;
        }

        // Default to PRIVATE
        return org.operaton.fitpub.model.entity.RemoteActivity.Visibility.PRIVATE;
    }

    /**
     * Parse ISO 8601 duration string (PT48M23S) to seconds.
     */
    private Long parseDurationSeconds(String isoDuration) {
        if (isoDuration == null || isoDuration.isBlank()) {
            return null;
        }

        try {
            // Simple ISO 8601 duration parser for PT format
            // Format: PT<hours>H<minutes>M<seconds>S
            if (!isoDuration.startsWith("PT")) {
                return null;
            }

            String duration = isoDuration.substring(2); // Remove "PT"
            long totalSeconds = 0;

            // Parse hours
            if (duration.contains("H")) {
                int hIndex = duration.indexOf("H");
                totalSeconds += Long.parseLong(duration.substring(0, hIndex)) * 3600;
                duration = duration.substring(hIndex + 1);
            }

            // Parse minutes
            if (duration.contains("M")) {
                int mIndex = duration.indexOf("M");
                totalSeconds += Long.parseLong(duration.substring(0, mIndex)) * 60;
                duration = duration.substring(mIndex + 1);
            }

            // Parse seconds
            if (duration.contains("S")) {
                int sIndex = duration.indexOf("S");
                totalSeconds += Long.parseLong(duration.substring(0, sIndex));
            }

            return totalSeconds;
        } catch (Exception e) {
            log.warn("Failed to parse ISO duration: {}", isoDuration, e);
            return null;
        }
    }

    /**
     * Serialize object to JSON string.
     */
    private String serializeToJson(Object object) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            return null;
        }
    }

    /**
     * Convert object to list of strings (handles both single string and list).
     */
    private java.util.List<String> objectToStringList(Object obj) {
        if (obj == null) {
            return java.util.Collections.emptyList();
        }
        if (obj instanceof String) {
            return java.util.Collections.singletonList((String) obj);
        }
        if (obj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) obj;
            return list.stream()
                .filter(item -> item instanceof String)
                .map(item -> (String) item)
                .collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Guess activity type from text.
     */
    private String guessActivityType(String text) {
        if (text == null) {
            return "UNKNOWN";
        }
        String lower = text.toLowerCase();
        if (lower.contains("run") || lower.contains("jog")) return "RUN";
        if (lower.contains("ride") || lower.contains("bike") || lower.contains("cycl")) return "RIDE";
        if (lower.contains("hike") || lower.contains("walk")) return "HIKE";
        if (lower.contains("swim")) return "SWIM";
        return "UNKNOWN";
    }

    /**
     * Parse Long from object.
     */
    private Long parseLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Parse Integer from object.
     */
    private Integer parseInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Parse Double from object.
     */
    private Double parseDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
