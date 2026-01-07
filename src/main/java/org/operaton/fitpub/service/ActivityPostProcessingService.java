package org.operaton.fitpub.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.util.ActivityFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for asynchronous post-processing of activities after upload.
 * Coordinates expensive operations (Personal Records, Weather, Heatmap, Federation)
 * in separate transactions to avoid blocking the upload response.
 *
 * Each operation runs asynchronously with REQUIRES_NEW transaction propagation
 * to ensure fault isolation - failures in one operation don't affect others.
 *
 * Operations execute in the following order:
 * - Personal Records: Runs independently (parallel)
 * - Heatmap: Runs independently (parallel)
 * - Weather → Federation: Sequential chain (weather must complete before federation)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityPostProcessingService {

    private final PersonalRecordService personalRecordService;
    private final WeatherService weatherService;
    private final HeatmapGridService heatmapGridService;
    private final FederationService federationService;
    private final ActivityImageService activityImageService;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Orchestrates async post-processing operations for an uploaded activity.
     * Called after activity is saved and immediately visible to the user.
     *
     * Personal Records and Heatmap run independently in parallel.
     * Weather and Federation run sequentially (weather must complete first for future share pic integration).
     *
     * All operations use separate transactions (REQUIRES_NEW) for fault isolation.
     * Errors are logged but don't propagate - each operation fails independently.
     *
     * @param activityId the saved activity ID
     * @param userId the user ID who uploaded the activity
     */
    @Async("taskExecutor")
    public void processActivityAsync(UUID activityId, UUID userId) {
        log.info("Starting async post-processing for activity {} by user {}", activityId, userId);

        // Run post-processing operations in background thread
        // All operations run sequentially with separate transactions (REQUIRES_NEW)
        // for fault isolation - failures in one operation don't affect others

        updatePersonalRecordsAsync(activityId);
        updateHeatmapAsync(activityId);

        // Weather must complete before federation for potential weather data in share images
        fetchWeatherAsync(activityId);
        publishToFederationAsync(activityId, userId);

        log.info("Completed async post-processing for activity {}", activityId);
    }

    /**
     * Check and update personal records for the activity.
     * Called internally from processActivityAsync background thread.
     * Runs in a separate transaction to isolate from main upload transaction.
     *
     * @param activityId the activity ID to process
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updatePersonalRecordsAsync(UUID activityId) {
        try {
            log.debug("Async: Checking personal records for activity {}", activityId);

            Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));

            personalRecordService.checkAndUpdatePersonalRecords(activity);

            log.info("Async: Personal records updated for activity {}", activityId);

        } catch (Exception e) {
            log.error("Async: Failed to update personal records for activity {}: {}",
                activityId, e.getMessage(), e);
            // Don't rethrow - error logged, operation fails independently
        }
    }

    /**
     * Update heatmap grid with activity GPS data.
     * Called internally from processActivityAsync background thread.
     * Runs in a separate transaction to isolate from main upload transaction.
     *
     * @param activityId the activity ID to process
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateHeatmapAsync(UUID activityId) {
        try {
            log.debug("Async: Updating heatmap for activity {}", activityId);

            Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));

            heatmapGridService.updateHeatmapForActivity(activity);

            log.info("Async: Heatmap updated for activity {}", activityId);

        } catch (Exception e) {
            log.error("Async: Failed to update heatmap for activity {}: {}",
                activityId, e.getMessage(), e);
            // Don't rethrow - error logged, operation fails independently
        }
    }

    /**
     * Fetch weather data for the activity location and time.
     * Called internally from processActivityAsync background thread.
     * Runs in a separate transaction to isolate from main upload transaction.
     *
     * Must complete before federation push to allow future integration of weather in share images.
     *
     * @param activityId the activity ID to process
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fetchWeatherAsync(UUID activityId) {
        try {
            log.debug("Async: Fetching weather for activity {}", activityId);

            Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));

            weatherService.fetchWeatherForActivity(activity);

            log.info("Async: Weather fetched for activity {}", activityId);

        } catch (Exception e) {
            log.error("Async: Failed to fetch weather for activity {}: {}",
                activityId, e.getMessage(), e);
            // Don't rethrow - error logged, operation fails independently
        }
    }

    /**
     * Publish activity to the Fediverse (ActivityPub federation).
     * Generates activity image and sends Create activity to all follower inboxes.
     * Called internally from processActivityAsync background thread.
     * Runs in a separate transaction to isolate from main upload transaction.
     *
     * Only publishes if activity visibility is PUBLIC or FOLLOWERS.
     * This method should run AFTER weather fetch completes for future share pic integration.
     *
     * @param activityId the activity ID to publish
     * @param userId the user ID who owns the activity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishToFederationAsync(UUID activityId, UUID userId) {
        try {
            log.debug("Async: Publishing activity {} to Fediverse", activityId);

            Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

            // Only publish if activity is PUBLIC or FOLLOWERS
            if (activity.getVisibility() != Activity.Visibility.PUBLIC &&
                activity.getVisibility() != Activity.Visibility.FOLLOWERS) {
                log.debug("Async: Skipping federation for private activity {}", activityId);
                return;
            }

            String activityUri = baseUrl + "/activities/" + activity.getId();
            String actorUri = baseUrl + "/users/" + user.getUsername();

            // Generate activity image (map with GPS track)
            String imageUrl = null;
            try {
                imageUrl = activityImageService.generateActivityImage(activity);
            } catch (Exception e) {
                log.warn("Async: Failed to generate activity image for {}: {}", activityId, e.getMessage());
                // Continue without image
            }

            // Build ActivityPub Note object
            Map<String, Object> noteObject = new HashMap<>();
            noteObject.put("id", activityUri);
            noteObject.put("type", "Note");
            noteObject.put("attributedTo", actorUri);
            noteObject.put("published", activity.getCreatedAt().toString());
            noteObject.put("content", formatActivityContent(activity));
            noteObject.put("url", baseUrl + "/activities/" + activity.getId());

            // Set visibility (to/cc fields)
            if (activity.getVisibility() == Activity.Visibility.PUBLIC) {
                noteObject.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
                noteObject.put("cc", List.of(actorUri + "/followers"));
            } else {
                // FOLLOWERS only
                noteObject.put("to", List.of(actorUri + "/followers"));
            }

            // Attach activity image if generated
            if (imageUrl != null) {
                Map<String, Object> imageAttachment = new HashMap<>();
                imageAttachment.put("type", "Image");
                imageAttachment.put("mediaType", "image/png");
                imageAttachment.put("url", imageUrl);
                imageAttachment.put("name", "Activity map showing " + activity.getActivityType() + " route");
                noteObject.put("attachment", List.of(imageAttachment));
            }

            // Send to all follower inboxes
            federationService.sendCreateActivity(
                activityUri,
                noteObject,
                user,
                activity.getVisibility() == Activity.Visibility.PUBLIC
            );

            log.info("Async: Activity {} published to Fediverse", activityId);

        } catch (Exception e) {
            log.error("Async: Failed to publish activity {} to Fediverse: {}",
                activityId, e.getMessage(), e);
            // Don't rethrow - error logged, operation fails independently
        }
    }

    /**
     * Format activity content for ActivityPub Note.
     * Uses plain text with Unicode symbols for maximum compatibility across Fediverse platforms.
     *
     * Format:
     * - Title (if present)
     * - Description (if present)
     * - Activity type with emoji
     * - Distance (if present)
     * - Duration (if present)
     * - Elevation gain (if present)
     *
     * @param activity the activity to format
     * @return formatted content string
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

        // Metrics on separate lines
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
                .append(String.format("%.0f m", activity.getElevationGain()));
        }

        return content.toString();
    }

    /**
     * Get an emoji for the activity type.
     *
     * @param type the activity type
     * @return emoji representing the activity type
     */
    private String getActivityEmoji(Activity.ActivityType type) {
        return switch (type) {
            case RUN -> "🏃";
            case RIDE -> "🚴";
            case HIKE -> "🥾";
            case WALK -> "🚶";
            case SWIM -> "🏊";
            case ALPINE_SKI, BACKCOUNTRY_SKI, NORDIC_SKI -> "⛷️";
            case SNOWBOARD -> "🏂";
            case ROWING -> "🚣";
            case KAYAKING, CANOEING -> "🛶";
            case INLINE_SKATING -> "⛸️";
            case ROCK_CLIMBING, MOUNTAINEERING -> "🧗";
            case YOGA -> "🧘";
            case WORKOUT -> "💪";
            default -> "🏋️";
        };
    }
}
