package org.operaton.fitpub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.operaton.fitpub.exception.FitFileProcessingException;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;
import org.operaton.fitpub.repository.ActivityMetricsRepository;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.util.ActivityFormatter;
import org.operaton.fitpub.util.FitFileValidator;
import org.operaton.fitpub.util.FitParser;
import org.operaton.fitpub.util.TrackSimplifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for processing FIT files and creating activities.
 * Uses JSONB for track points and simplified LineString for map rendering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FitFileService {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private final FitFileValidator validator;
    private final FitParser parser;
    private final TrackSimplifier trackSimplifier;
    private final ActivityRepository activityRepository;
    private final ActivityMetricsRepository metricsRepository;
    private final ObjectMapper objectMapper;

    /**
     * Processes an uploaded FIT file and creates an activity.
     *
     * @param file the uploaded FIT file
     * @param userId the user ID
     * @param title optional custom title (will be auto-generated if null)
     * @param description optional description
     * @param visibility visibility level
     * @return the created activity
     * @throws FitFileProcessingException if processing fails
     */
    @Transactional
    public Activity processFitFile(
        MultipartFile file,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility
    ) {
        try {
            // Validate file
            log.info("Processing FIT file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
            validator.validate(file.getInputStream(), file.getSize());

            // Parse FIT file
            byte[] fileData = file.getBytes();
            FitParser.ParsedFitData parsedData = parser.parse(fileData);

            // Create activity entity
            Activity activity = createActivity(parsedData, userId, title, description, visibility, fileData);

            // Convert track points to JSONB
            String trackPointsJson = convertTrackPointsToJson(parsedData.getTrackPoints());
            activity.setTrackPointsJson(trackPointsJson);

            // Create full LineString from all points
            LineString fullTrack = createLineStringFromTrackPoints(parsedData.getTrackPoints());

            // Simplify track for map rendering
            Coordinate[] coordinates = fullTrack.getCoordinates();
            LineString simplifiedTrack = trackSimplifier.simplify(coordinates);
            activity.setSimplifiedTrack(simplifiedTrack);

            // Create metrics
            if (parsedData.getMetrics() != null) {
                ActivityMetrics metrics = parsedData.getMetrics().toEntity(activity);
                calculateAdditionalMetrics(metrics, parsedData.getTrackPoints());
                activity.setMetrics(metrics);
            }

            // Save activity (single INSERT instead of 855!)
            Activity savedActivity = activityRepository.save(activity);

            log.info("Successfully created activity {} with {} track points (simplified to {} for map)",
                savedActivity.getId(),
                parsedData.getTrackPoints().size(),
                simplifiedTrack.getNumPoints());

            return savedActivity;
        } catch (IOException e) {
            throw new FitFileProcessingException("Failed to read FIT file", e);
        }
    }

    /**
     * Processes FIT file data directly (for testing or non-upload scenarios).
     *
     * @param fileData the FIT file bytes
     * @param userId the user ID
     * @param visibility visibility level
     * @return the created activity
     */
    @Transactional
    public Activity processFitFile(byte[] fileData, UUID userId, Activity.Visibility visibility) {
        validator.validate(fileData);
        FitParser.ParsedFitData parsedData = parser.parse(fileData);
        return createActivityFromParsedData(parsedData, userId, null, null, visibility, fileData);
    }

    /**
     * Creates an activity entity from parsed FIT data.
     */
    private Activity createActivity(
        FitParser.ParsedFitData parsedData,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility,
        byte[] rawFile
    ) {
        String activityTitle = title != null && !title.isBlank()
            ? title
            : generateTitle(parsedData);

        // Default to PUBLIC if visibility not specified
        Activity.Visibility activityVisibility = visibility != null ? visibility : Activity.Visibility.PUBLIC;

        return Activity.builder()
            .userId(userId)
            .activityType(parsedData.getActivityType())
            .title(activityTitle)
            .description(description)
            .startedAt(parsedData.getStartTime())
            .endedAt(parsedData.getEndTime())
            .visibility(activityVisibility)
            .totalDistance(parsedData.getTotalDistance())
            .totalDurationSeconds(parsedData.getTotalDuration() != null ? parsedData.getTotalDuration().getSeconds() : null)
            .elevationGain(parsedData.getElevationGain())
            .elevationLoss(parsedData.getElevationLoss())
            .rawFitFile(rawFile)
            .build();
    }

    /**
     * Creates an activity from parsed data (internal method).
     */
    private Activity createActivityFromParsedData(
        FitParser.ParsedFitData parsedData,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility,
        byte[] rawFile
    ) {
        Activity activity = createActivity(parsedData, userId, title, description, visibility, rawFile);

        String trackPointsJson = convertTrackPointsToJson(parsedData.getTrackPoints());
        activity.setTrackPointsJson(trackPointsJson);

        LineString fullTrack = createLineStringFromTrackPoints(parsedData.getTrackPoints());
        LineString simplifiedTrack = trackSimplifier.simplify(fullTrack.getCoordinates());
        activity.setSimplifiedTrack(simplifiedTrack);

        if (parsedData.getMetrics() != null) {
            ActivityMetrics metrics = parsedData.getMetrics().toEntity(activity);
            calculateAdditionalMetrics(metrics, parsedData.getTrackPoints());
            activity.setMetrics(metrics);
        }

        return activityRepository.save(activity);
    }

    /**
     * Converts track points to JSON string for JSONB storage.
     */
    private String convertTrackPointsToJson(List<FitParser.TrackPointData> trackPoints) {
        try {
            return objectMapper.writeValueAsString(trackPoints);
        } catch (JsonProcessingException e) {
            throw new FitFileProcessingException("Failed to serialize track points to JSON", e);
        }
    }

    /**
     * Creates a PostGIS LineString from track points.
     */
    private LineString createLineStringFromTrackPoints(List<FitParser.TrackPointData> trackPoints) {
        Coordinate[] coordinates = trackPoints.stream()
            .map(tp -> new Coordinate(tp.getLongitude(), tp.getLatitude()))
            .toArray(Coordinate[]::new);

        return GEOMETRY_FACTORY.createLineString(coordinates);
    }

    /**
     * Generates a default title for an activity based on time of day.
     * Examples: "Morning Run", "Evening Ride", "Night Walk"
     */
    private String generateTitle(FitParser.ParsedFitData parsedData) {
        return ActivityFormatter.generateActivityTitle(
            parsedData.getStartTime(),
            parsedData.getActivityType()
        );
    }

    /**
     * Calculates additional metrics not provided by the FIT file.
     */
    private void calculateAdditionalMetrics(
        ActivityMetrics metrics,
        List<FitParser.TrackPointData> trackPoints
    ) {
        if (trackPoints.isEmpty()) {
            return;
        }

        // Calculate min/max elevation
        BigDecimal minElevation = null;
        BigDecimal maxElevation = null;

        for (FitParser.TrackPointData tp : trackPoints) {
            if (tp.getElevation() != null) {
                if (minElevation == null || tp.getElevation().compareTo(minElevation) < 0) {
                    minElevation = tp.getElevation();
                }
                if (maxElevation == null || tp.getElevation().compareTo(maxElevation) > 0) {
                    maxElevation = tp.getElevation();
                }
            }
        }

        metrics.setMinElevation(minElevation);
        metrics.setMaxElevation(maxElevation);

        // Calculate average temperature
        BigDecimal tempSum = BigDecimal.ZERO;
        int tempCount = 0;

        for (FitParser.TrackPointData tp : trackPoints) {
            if (tp.getTemperature() != null) {
                tempSum = tempSum.add(tp.getTemperature());
                tempCount++;
            }
        }

        if (tempCount > 0) {
            metrics.setAverageTemperature(
                tempSum.divide(BigDecimal.valueOf(tempCount), 2, BigDecimal.ROUND_HALF_UP)
            );
        }
    }

    /**
     * Deletes an activity and all associated data.
     *
     * @param activityId the activity ID
     * @param userId the user ID (for authorization)
     * @return true if deleted, false if not found or unauthorized
     */
    @Transactional
    public boolean deleteActivity(UUID activityId, UUID userId) {
        return activityRepository.findByIdAndUserId(activityId, userId)
            .map(activity -> {
                activityRepository.delete(activity);
                log.info("Deleted activity {} for user {}", activityId, userId);
                return true;
            })
            .orElse(false);
    }

    /**
     * Retrieves an activity by ID.
     *
     * @param activityId the activity ID
     * @param userId the user ID (for authorization)
     * @return the activity or null if not found
     */
    @Transactional(readOnly = true)
    public Activity getActivity(UUID activityId, UUID userId) {
        return activityRepository.findByIdAndUserId(activityId, userId).orElse(null);
    }

    /**
     * Retrieves an activity by ID without user authorization check.
     * This is used for public activity access (e.g., viewing public tracks).
     * Caller is responsible for checking visibility and access permissions.
     *
     * @param activityId the activity ID
     * @return the activity or null if not found
     */
    @Transactional(readOnly = true)
    public Activity getActivityById(UUID activityId) {
        return activityRepository.findById(activityId).orElse(null);
    }

    /**
     * Retrieves all activities for a user.
     *
     * @param userId the user ID
     * @return list of activities
     */
    @Transactional(readOnly = true)
    public List<Activity> getUserActivities(UUID userId) {
        return activityRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    /**
     * Retrieves activities for a user with pagination.
     *
     * @param userId the user ID
     * @param page page number (0-indexed)
     * @param size page size
     * @return page of activities
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Activity> getUserActivitiesPaginated(UUID userId, int page, int size) {
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("startedAt").descending());
        return activityRepository.findByUserIdOrderByStartedAtDesc(userId, pageable);
    }

    /**
     * Retrieves public activities for a user with pagination.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return page of public activities
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Activity> getPublicActivitiesByUserId(UUID userId, org.springframework.data.domain.Pageable pageable) {
        return activityRepository.findByUserIdAndVisibilityOrderByStartedAtDesc(userId, Activity.Visibility.PUBLIC, pageable);
    }

    /**
     * Update an existing activity's metadata.
     *
     * @param activity the activity with updated fields
     * @return the updated activity
     * @throws IllegalArgumentException if activity doesn't exist or user doesn't own it
     */
    @Transactional
    public Activity updateActivity(Activity activity) {
        // Verify activity exists and belongs to the user
        Activity existing = activityRepository.findById(activity.getId())
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activity.getId()));

        if (!existing.getUserId().equals(activity.getUserId())) {
            throw new IllegalArgumentException("User does not own this activity");
        }

        // Update allowed fields
        existing.setTitle(activity.getTitle());
        existing.setDescription(activity.getDescription());
        existing.setVisibility(activity.getVisibility());

        return activityRepository.save(existing);
    }
}
