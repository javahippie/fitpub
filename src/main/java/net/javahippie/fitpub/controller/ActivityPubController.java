package net.javahippie.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.activitypub.Actor;
import net.javahippie.fitpub.model.activitypub.OrderedCollection;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.FollowRepository;
import net.javahippie.fitpub.repository.ActivityRepository;
import net.javahippie.fitpub.repository.UserRepository;
import net.javahippie.fitpub.service.ActivityImageService;
import net.javahippie.fitpub.service.InboxProcessor;
import net.javahippie.fitpub.util.ActivityFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;

/**
 * ActivityPub protocol controller.
 * Implements ActivityPub server-to-server (S2S) protocol endpoints.
 *
 * Spec: https://www.w3.org/TR/activitypub/
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ActivityPubController {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityImageService activityImageService;
    private final InboxProcessor inboxProcessor;
    private final FollowRepository followRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    private static final String ACTIVITY_JSON = "application/activity+json";
    private static final String LD_JSON = "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";

    /**
     * Actor profile endpoint.
     * Returns the ActivityPub Actor object for a user.
     *
     * @param username the username
     * @return Actor object in JSON-LD format
     */
    @GetMapping(
        value = "/users/{username}",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<Actor> getActor(@PathVariable String username) {
        log.debug("ActivityPub actor request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("User not found for ActivityPub request: {}", username);
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        Actor actor = Actor.fromUser(user, baseUrl);

        return ResponseEntity.ok(actor);
    }

    /**
     * Inbox endpoint for receiving ActivityPub activities.
     * POST /users/{username}/inbox
     *
     * @param username the username
     * @param activity the incoming activity
     * @return accepted response
     */
    @PostMapping(
        value = "/users/{username}/inbox",
        consumes = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<Void> inbox(
        @PathVariable String username,
        @RequestBody Map<String, Object> activity,
        @RequestHeader(value = "Signature", required = false) String signature
    ) {
        log.info("Received ActivityPub activity for user {}: {}", username, activity.get("type"));

        // TODO: Validate HTTP signature (signature validation can be added later)

        // Process activity asynchronously to avoid blocking the sender
        try {
            inboxProcessor.processActivity(username, activity);
        } catch (Exception e) {
            log.error("Error processing inbox activity", e);
        }

        // Always return 202 Accepted per ActivityPub spec
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Outbox endpoint for user's activities.
     * GET /users/{username}/outbox
     *
     * @param username the username
     * @return OrderedCollection of activities
     */
    @GetMapping(
        value = "/users/{username}/outbox",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<OrderedCollection> outbox(@PathVariable String username) {
        log.debug("Outbox request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        String outboxUrl = baseUrl + "/users/" + username + "/outbox";

        // Count public activities for this user
        // Mastodon and other ActivityPub servers primarily use the totalItems count
        long activityCount = activityRepository.countByUserIdAndVisibility(
            user.getId(),
            Activity.Visibility.PUBLIC
        );

        OrderedCollection collection = OrderedCollection.builder()
            .context("https://www.w3.org/ns/activitystreams")
            .type("OrderedCollection")
            .id(outboxUrl)
            .totalItems((int) activityCount)
            .build();

        return ResponseEntity.ok(collection);
    }

    /**
     * Followers collection endpoint.
     * GET /users/{username}/followers
     *
     * @param username the username
     * @return OrderedCollection of followers
     */
    @GetMapping(
        value = "/users/{username}/followers",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<OrderedCollection> followers(@PathVariable String username) {
        log.debug("Followers request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        String followersUrl = baseUrl + "/users/" + username + "/followers";
        String actorUri = user.getActorUri(baseUrl);

        // Get actual follower count from database
        long followerCount = followRepository.countAcceptedFollowersByActorUri(actorUri);

        OrderedCollection collection = OrderedCollection.builder()
            .context("https://www.w3.org/ns/activitystreams")
            .type("OrderedCollection")
            .id(followersUrl)
            .totalItems((int) followerCount)
            .build();

        return ResponseEntity.ok(collection);
    }

    /**
     * Following collection endpoint.
     * GET /users/{username}/following
     *
     * @param username the username
     * @return OrderedCollection of following
     */
    @GetMapping(
        value = "/users/{username}/following",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<OrderedCollection> following(@PathVariable String username) {
        log.debug("Following request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        String followingUrl = baseUrl + "/users/" + username + "/following";

        // Get actual following count from database
        long followingCount = followRepository.countAcceptedFollowingByUserId(user.getId());

        OrderedCollection collection = OrderedCollection.builder()
            .context("https://www.w3.org/ns/activitystreams")
            .type("OrderedCollection")
            .id(followingUrl)
            .totalItems((int) followingCount)
            .build();

        return ResponseEntity.ok(collection);
    }

    /**
     * Activity object endpoint.
     * Returns a single activity as an ActivityPub Note object.
     * This is needed for quote posts and other federation features.
     *
     * GET /activities/{id}
     *
     * @param id the activity ID
     * @return Note object in ActivityPub format
     */
    @GetMapping(
        value = "/activities/{id}",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<Map<String, Object>> getActivity(@PathVariable UUID id) {
        log.debug("ActivityPub activity request for ID: {}", id);

        Optional<Activity> activityOpt = activityRepository.findById(id);
        if (activityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Activity activity = activityOpt.get();

        // Only return public activities via ActivityPub
        if (activity.getVisibility() != Activity.Visibility.PUBLIC) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Get the user
        Optional<User> userOpt = userRepository.findById(activity.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        String actorUri = baseUrl + "/users/" + user.getUsername();
        String activityUri = baseUrl + "/activities/" + activity.getId();

        // Build the Note object (same format as used in federation)
        Map<String, Object> noteObject = new HashMap<>();
        noteObject.put("@context", "https://www.w3.org/ns/activitystreams");
        noteObject.put("id", activityUri);
        noteObject.put("type", "Note");
        noteObject.put("attributedTo", actorUri);
        noteObject.put("published", activity.getCreatedAt().toString());
        noteObject.put("content", formatActivityContent(activity));
        noteObject.put("url", activityUri);

        // Audience
        noteObject.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
        noteObject.put("cc", List.of(actorUri + "/followers"));

        // Add conversation/context for threading
        noteObject.put("conversation", activityUri);

        // Add activity image if available
        // Check if image exists, otherwise generate it
        File imageFile = activityImageService.getActivityImageFile(activity.getId());
        if (!imageFile.exists()) {
            // Generate image if it doesn't exist
            activityImageService.generateActivityImage(activity);
        }

        // Only add attachment if image was successfully generated/exists
        if (imageFile.exists()) {
            String imageUrl = baseUrl + "/api/activities/" + activity.getId() + "/image";
            Map<String, Object> imageAttachment = new HashMap<>();
            imageAttachment.put("type", "Image");
            imageAttachment.put("mediaType", "image/png");
            imageAttachment.put("url", imageUrl);
            imageAttachment.put("name", "Activity map showing " + activity.getActivityType() + " route");
            noteObject.put("attachment", List.of(imageAttachment));
        }

        return ResponseEntity.ok(noteObject);
    }

    /**
     * Format activity content for ActivityPub.
     * Uses plain text with Unicode symbols for maximum compatibility.
     */
    private String formatActivityContent(Activity activity) {
        StringBuilder content = new StringBuilder();

        // Title (if present)
        if (activity.getTitle() != null && !activity.getTitle().isEmpty()) {
            content.append(activity.getTitle()).append("\n\n");
        }

        // Description (if present)
        if (activity.getDescription() != null && !activity.getDescription().isEmpty()) {
            content.append(activity.getDescription()).append("\n\n");
        }

        // Activity type with emoji
        String activityEmoji = getActivityEmoji(activity.getActivityType());
        String formattedType = ActivityFormatter.formatActivityType(activity.getActivityType());
        content.append(activityEmoji).append(" ").append(formattedType);

        // Metrics
        if (activity.getTotalDistance() != null) {
            content.append("\n📏 ")
                .append(String.format("%.2f km", activity.getTotalDistance().doubleValue() / 1000.0));
        }

        if (activity.getTotalDurationSeconds() != null) {
            long hours = activity.getTotalDurationSeconds() / 3600;
            long minutes = (activity.getTotalDurationSeconds() % 3600) / 60;
            long seconds = activity.getTotalDurationSeconds() % 60;
            content.append("\n⏱️ ");
            if (hours > 0) {
                content.append(hours).append("h ");
            }
            content.append(minutes).append("m ").append(seconds).append("s");
        }

        if (activity.getElevationGain() != null) {
            content.append("\n⛰️ ")
                .append(String.format("%.0f m", activity.getElevationGain().doubleValue()));
        }

        return content.toString();
    }

    /**
     * Get emoji for activity type.
     */
    private String getActivityEmoji(Activity.ActivityType activityType) {
        return switch (activityType) {
            case RUN -> "🏃";
            case RIDE -> "🚴";
            case HIKE -> "🥾";
            case WALK -> "🚶";
            case SWIM -> "🏊";
            default -> "💪";
        };
    }
}
