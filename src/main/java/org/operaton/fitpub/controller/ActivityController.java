package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.ActivityDTO;
import org.operaton.fitpub.model.dto.ActivityUpdateRequest;
import org.operaton.fitpub.model.dto.ActivityUploadRequest;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.ActivityFileService;
import org.operaton.fitpub.service.ActivityImageService;
import org.operaton.fitpub.service.FederationService;
import org.operaton.fitpub.service.FitFileService;
import org.operaton.fitpub.util.ActivityFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for activity management.
 * Handles activity file uploads (FIT, GPX), activity retrieval, updates, and deletion.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final ActivityFileService activityFileService;
    private final FitFileService fitFileService;
    private final UserRepository userRepository;
    private final FederationService federationService;
    private final ActivityImageService activityImageService;
    private final org.operaton.fitpub.service.WeatherService weatherService;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Helper method to get user ID from authenticated UserDetails.
     *
     * @param userDetails the authenticated user details
     * @return the user's UUID
     * @throws UsernameNotFoundException if user not found
     */
    private UUID getUserId(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));
        return user.getId();
    }

    /**
     * Uploads an activity file (FIT or GPX) and creates a new activity.
     *
     * @param file the activity file (FIT or GPX)
     * @param request the upload request with metadata
     * @param userDetails the authenticated user
     * @return the created activity
     */
    @PostMapping("/upload")
    public ResponseEntity<ActivityDTO> uploadActivity(
        @RequestParam("file") MultipartFile file,
        @Valid @ModelAttribute ActivityUploadRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} uploading activity file: {}", userDetails.getUsername(), file.getOriginalFilename());

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Activity activity = activityFileService.processActivityFile(
            file,
            user.getId(),
            request.getTitle(),
            request.getDescription(),
            request.getVisibility()
        );

        // Send ActivityPub Create activity to followers if public or followers-only
        if (activity.getVisibility() == Activity.Visibility.PUBLIC ||
            activity.getVisibility() == Activity.Visibility.FOLLOWERS) {

            String activityUri = baseUrl + "/activities/" + activity.getId();
            String actorUri = baseUrl + "/users/" + user.getUsername();

            // Create the Note object representing the activity
            Map<String, Object> noteObject = new HashMap<>();
            noteObject.put("id", activityUri);
            noteObject.put("type", "Note");
            noteObject.put("attributedTo", actorUri);
            noteObject.put("published", activity.getCreatedAt().toString());
            noteObject.put("content", formatActivityContent(activity));

            if (activity.getVisibility() == Activity.Visibility.PUBLIC) {
                noteObject.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
                noteObject.put("cc", List.of(actorUri + "/followers"));
            } else {
                noteObject.put("to", List.of(actorUri + "/followers"));
            }

            // Add URL to the activity page
            noteObject.put("url", baseUrl + "/activities/" + activity.getId());

            // Generate and attach activity image
            String imageUrl = activityImageService.generateActivityImage(activity);
            if (imageUrl != null) {
                Map<String, Object> imageAttachment = new HashMap<>();
                imageAttachment.put("type", "Image");
                imageAttachment.put("mediaType", "image/png");
                imageAttachment.put("url", imageUrl);
                imageAttachment.put("name", "Activity map showing " + activity.getActivityType() + " route");
                noteObject.put("attachment", List.of(imageAttachment));
            }

            federationService.sendCreateActivity(
                activityUri,
                noteObject,
                user,
                activity.getVisibility() == Activity.Visibility.PUBLIC
            );
        }

        ActivityDTO dto = ActivityDTO.fromEntity(activity);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Format activity content for ActivityPub.
     * Uses plain text with Unicode symbols for maximum compatibility across Fediverse platforms.
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

    /**
     * Simple HTML escaping.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Retrieves an activity by ID.
     * Public activities can be viewed without authentication.
     * Non-public activities require authentication and ownership/follower access.
     *
     * @param id the activity ID
     * @param userDetails the authenticated user (optional)
     * @return the activity
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActivityDTO> getActivity(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        // First try to get the activity directly
        Activity activity = fitFileService.getActivityById(id);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Check visibility
        if (activity.getVisibility() == Activity.Visibility.PUBLIC) {
            // Public activities are always accessible
            ActivityDTO dto = ActivityDTO.fromEntity(activity);
            return ResponseEntity.ok(dto);
        }

        // For non-public activities, require authentication
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID userId = getUserId(userDetails);

        // Check if user has access (owner or follower)
        Activity checkedActivity = fitFileService.getActivity(id, userId);
        if (checkedActivity == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ActivityDTO dto = ActivityDTO.fromEntity(checkedActivity);
        return ResponseEntity.ok(dto);
    }

    /**
     * Lists all activities for the authenticated user with pagination.
     *
     * @param userDetails the authenticated user
     * @param page page number (default: 0)
     * @param size page size (default: 10)
     * @return page of activities
     */
    @GetMapping
    public ResponseEntity<?> getUserActivities(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        log.info("User {} retrieving activities (page: {}, size: {})", userDetails.getUsername(), page, size);

        UUID userId = getUserId(userDetails);

        org.springframework.data.domain.Page<Activity> activityPage =
            fitFileService.getUserActivitiesPaginated(userId, page, size);

        // Convert to DTOs
        org.springframework.data.domain.Page<ActivityDTO> dtoPage = activityPage.map(ActivityDTO::fromEntity);

        // Return Spring Page object with all pagination metadata
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Updates activity metadata.
     *
     * @param id the activity ID
     * @param request the update request
     * @param userDetails the authenticated user
     * @return the updated activity
     */
    @PutMapping("/{id}")
    public ResponseEntity<ActivityDTO> updateActivity(
        @PathVariable UUID id,
        @Valid @RequestBody ActivityUpdateRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} updating activity {}", userDetails.getUsername(), id);

        UUID userId = getUserId(userDetails);

        try {
            Activity updated = fitFileService.updateActivity(
                id,
                userId,
                request.getTitle(),
                request.getDescription(),
                request.getVisibility()
            );

            ActivityDTO dto = ActivityDTO.fromEntity(updated);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Activity update failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deletes an activity.
     *
     * @param id the activity ID
     * @param userDetails the authenticated user
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} deleting activity {}", userDetails.getUsername(), id);

        UUID userId = getUserId(userDetails);

        // Get activity before deletion to send Delete activity to followers
        Activity activity = fitFileService.getActivity(id, userId);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Only send Delete activity if it was previously federated (public or followers-only)
        boolean shouldFederate = activity.getVisibility() == Activity.Visibility.PUBLIC ||
                                activity.getVisibility() == Activity.Visibility.FOLLOWERS;

        // Delete from database
        boolean deleted = fitFileService.deleteActivity(id, userId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        // Send Delete activity to followers if the activity was federated
        if (shouldFederate) {
            User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            String activityUri = baseUrl + "/activities/" + id;
            federationService.sendDeleteActivity(activityUri, user);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists public activities for a specific user by username.
     *
     * @param username the username
     * @param page page number (default: 0)
     * @param size page size (default: 10)
     * @return page of public activities
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<?> getUserPublicActivities(
        @PathVariable String username,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("Retrieving public activities for user: {}", username);

        // Get user by username
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Get public activities only
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("startedAt").descending());

        org.springframework.data.domain.Page<Activity> activityPage =
            fitFileService.getPublicActivitiesByUserId(user.getId(), pageable);

        // Convert to DTOs
        org.springframework.data.domain.Page<ActivityDTO> dtoPage = activityPage.map(ActivityDTO::fromEntity);

        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Gets the GPS track data for an activity in GeoJSON format.
     * Public activities can be accessed without authentication.
     * Private/followers activities require authentication and proper access.
     *
     * @param id the activity ID
     * @param userDetails the authenticated user (optional for public activities)
     * @return GeoJSON FeatureCollection with track data
     */
    @GetMapping("/{id}/track")
    public ResponseEntity<?> getActivityTrack(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.debug("Retrieving track data for activity {}", id);

        // First try to get the activity regardless of user
        Activity activity = fitFileService.getActivityById(id);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Check visibility and access permissions
        if (activity.getVisibility() != Activity.Visibility.PUBLIC) {
            // Non-public activities require authentication
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            UUID userId = getUserId(userDetails);

            // Check if user owns the activity
            if (!activity.getUserId().equals(userId)) {
                // TODO: Check if user is following the activity owner (for FOLLOWERS visibility)
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // Build GeoJSON FeatureCollection
        ActivityDTO dto = ActivityDTO.fromEntity(activity);

        // Use high-resolution track points if available, otherwise fall back to simplified track
        java.util.List<java.util.List<Double>> coordinates = new java.util.ArrayList<>();

        if (dto.getTrackPoints() != null && !dto.getTrackPoints().isEmpty()) {
            // Use high-resolution track points
            for (java.util.Map<String, Object> point : dto.getTrackPoints()) {
                Double longitude = (Double) point.get("longitude");
                Double latitude = (Double) point.get("latitude");
                Double elevation = (Double) point.get("elevation");

                if (longitude != null && latitude != null) {
                    if (elevation != null) {
                        coordinates.add(java.util.List.of(longitude, latitude, elevation));
                    } else {
                        coordinates.add(java.util.List.of(longitude, latitude));
                    }
                }
            }
        } else if (dto.getSimplifiedTrack() != null) {
            // Fall back to simplified track if high-res not available
            @SuppressWarnings("unchecked")
            java.util.List<java.util.List<Double>> simplifiedCoords =
                (java.util.List<java.util.List<Double>>) dto.getSimplifiedTrack().get("coordinates");
            if (simplifiedCoords != null) {
                coordinates = simplifiedCoords;
            }
        }

        if (coordinates.isEmpty()) {
            // Return empty FeatureCollection if no track data
            return ResponseEntity.ok(java.util.Map.of(
                "type", "FeatureCollection",
                "features", java.util.List.of()
            ));
        }

        // Create GeoJSON geometry
        java.util.Map<String, Object> geometry = new java.util.LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);

        // Create GeoJSON Feature with the track
        java.util.Map<String, Object> feature = new java.util.LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);

        // Add properties
        java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("title", activity.getTitle());
        properties.put("activityType", activity.getActivityType().name());
        properties.put("distance", activity.getTotalDistance());
        properties.put("duration", activity.getTotalDurationSeconds());
        feature.put("properties", properties);

        // Create FeatureCollection
        java.util.Map<String, Object> geoJson = new java.util.LinkedHashMap<>();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("features", java.util.List.of(feature));

        return ResponseEntity.ok(geoJson);
    }

    /**
     * Serves the generated activity image.
     *
     * @param id the activity ID
     * @return the activity image
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<org.springframework.core.io.Resource> getActivityImage(@PathVariable UUID id) {
        try {
            java.io.File imageFile = activityImageService.getActivityImageFile(id);

            if (!imageFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource =
                new org.springframework.core.io.FileSystemResource(imageFile);

            return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(resource);
        } catch (Exception e) {
            log.error("Error serving activity image for {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get weather data for an activity.
     *
     * @param id the activity ID
     * @return weather data or 404 if not found
     */
    @GetMapping("/{id}/weather")
    public ResponseEntity<?> getActivityWeather(@PathVariable UUID id) {
        try {
            return weatherService.getWeatherForActivity(id)
                .map(weatherData -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", weatherData.getId());
                    response.put("activityId", weatherData.getActivityId());
                    response.put("temperatureCelsius", weatherData.getTemperatureCelsius());
                    response.put("feelsLikeCelsius", weatherData.getFeelsLikeCelsius());
                    response.put("humidity", weatherData.getHumidity());
                    response.put("pressure", weatherData.getPressure());
                    response.put("windSpeedMps", weatherData.getWindSpeedMps());
                    response.put("windSpeedKmh", weatherData.getWindSpeedKmh());
                    response.put("windDirection", weatherData.getWindDirection());
                    response.put("windDirectionCardinal", weatherData.getWindDirectionCardinal());
                    response.put("weatherCondition", weatherData.getWeatherCondition());
                    response.put("weatherDescription", weatherData.getWeatherDescription());
                    response.put("weatherIcon", weatherData.getWeatherIcon());
                    response.put("weatherEmoji", weatherData.getWeatherEmoji());
                    response.put("cloudiness", weatherData.getCloudiness());
                    response.put("visibilityMeters", weatherData.getVisibilityMeters());
                    response.put("precipitationMm", weatherData.getPrecipitationMm());
                    response.put("snowMm", weatherData.getSnowMm());
                    response.put("sunrise", weatherData.getSunrise());
                    response.put("sunset", weatherData.getSunset());
                    response.put("dataSource", weatherData.getDataSource());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving weather data for activity {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
