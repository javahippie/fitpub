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
import org.operaton.fitpub.exception.GpxFileProcessingException;
import org.operaton.fitpub.exception.UnsupportedFileFormatException;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Unified service for processing activity files (FIT, GPX, etc.) and creating activities.
 * Automatically detects file format and routes to the appropriate parser.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityFileService {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    /**
     * Processing options to control which side effects are executed after activity creation.
     * Used to skip expensive operations during batch imports and re-execute them later as a batch.
     */
    @lombok.Getter
    @lombok.Builder
    public static class ProcessingOptions {
        @lombok.Builder.Default
        private final boolean skipPersonalRecords = false;

        @lombok.Builder.Default
        private final boolean skipAchievements = false;

        @lombok.Builder.Default
        private final boolean skipHeatmap = false;

        @lombok.Builder.Default
        private final boolean skipTrainingLoad = false;

        @lombok.Builder.Default
        private final boolean skipSummaries = false;

        @lombok.Builder.Default
        private final boolean skipWeather = false;

        /**
         * Creates options for batch import mode - skips all side effects.
         * Analytics and social features are recalculated in a batch after import completes.
         *
         * @return processing options with all side effects skipped
         */
        public static ProcessingOptions batchImportMode() {
            return ProcessingOptions.builder()
                .skipPersonalRecords(true)
                .skipAchievements(true)
                .skipHeatmap(true)
                .skipTrainingLoad(true)
                .skipSummaries(true)
                .skipWeather(true)
                .build();
        }

        /**
         * Creates options for normal mode - executes all side effects.
         * This is the default behavior for single activity uploads.
         *
         * @return processing options with no side effects skipped
         */
        public static ProcessingOptions normalMode() {
            return ProcessingOptions.builder().build();
        }
    }

    private final FitFileValidator fitValidator;
    private final GpxFileValidator gpxValidator;
    private final FitParser fitParser;
    private final GpxParser gpxParser;
    private final TrackSimplifier trackSimplifier;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    // Async operations moved to ActivityPostProcessingService:
    // - PersonalRecordService (async)
    // - HeatmapGridService (async)
    // - WeatherService (async)
    // Synchronous operations remain here:
    private final AchievementService achievementService;
    private final TrainingLoadService trainingLoadService;
    private final ActivitySummaryService activitySummaryService;

    /**
     * Processes an uploaded activity file (FIT or GPX) and creates an activity.
     * Uses normal processing mode with all side effects enabled.
     *
     * @param file the uploaded file
     * @param userId the user ID
     * @param title optional custom title (will be auto-generated if null)
     * @param description optional description
     * @param visibility visibility level
     * @return the created activity
     * @throws FitFileProcessingException if FIT processing fails
     * @throws GpxFileProcessingException if GPX processing fails
     * @throws UnsupportedFileFormatException if file format is unknown
     */
    @Transactional
    public Activity processActivityFile(
        MultipartFile file,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility
    ) {
        return processActivityFile(file, userId, title, description, visibility, ProcessingOptions.normalMode());
    }

    /**
     * Processes an uploaded activity file (FIT or GPX) and creates an activity with custom processing options.
     * Allows selective skipping of side effects for batch import scenarios.
     *
     * @param file the uploaded file
     * @param userId the user ID
     * @param title optional custom title (will be auto-generated if null)
     * @param description optional description
     * @param visibility visibility level
     * @param options processing options to control side effects
     * @return the created activity
     * @throws FitFileProcessingException if FIT processing fails
     * @throws GpxFileProcessingException if GPX processing fails
     * @throws UnsupportedFileFormatException if file format is unknown
     */
    @Transactional
    public Activity processActivityFile(
        MultipartFile file,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility,
        ProcessingOptions options
    ) {
        try {
            byte[] fileData = file.getBytes();
            String filename = file.getOriginalFilename();

            log.info("Processing activity file: {}, size: {} bytes", filename, file.getSize());

            // Detect file format
            FileFormat format = detectFileFormat(fileData, filename);
            log.debug("Detected file format: {}", format);

            // Parse based on format
            ParsedActivityData parsedData;
            if (format == FileFormat.FIT) {
                fitValidator.validate(fileData);
                parsedData = fitParser.parse(fileData);
                parsedData.setSourceFormat("FIT");
            } else if (format == FileFormat.GPX) {
                gpxValidator.validate(fileData);
                parsedData = gpxParser.parse(fileData);
                parsedData.setSourceFormat("GPX");
            } else {
                throw new UnsupportedFileFormatException("Unsupported file format: " + filename);
            }

            // Common processing (same for both formats)
            return createActivityFromParsedData(parsedData, userId, title, description, visibility, fileData, options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read activity file", e);
        }
    }

    /**
     * Detects file format from content and filename.
     * Priority: magic bytes > XML header > file extension
     */
    private FileFormat detectFileFormat(byte[] fileData, String filename) {
        // Primary: Check magic bytes for FIT file signature at offset 8: ".FIT"
        if (fileData.length >= 12) {
            if (fileData[8] == '.' && fileData[9] == 'F' &&
                fileData[10] == 'I' && fileData[11] == 'T') {
                return FileFormat.FIT;
            }
        }

        // Secondary: Check XML header for GPX
        if (fileData.length >= 100) {
            String header = new String(fileData, 0, Math.min(200, fileData.length), StandardCharsets.UTF_8);
            if (header.contains("<?xml") && header.contains("<gpx")) {
                return FileFormat.GPX;
            }
        }

        // Fallback: File extension
        if (filename != null && !filename.isEmpty()) {
            String lowerFilename = filename.toLowerCase();
            if (lowerFilename.endsWith(".fit")) {
                return FileFormat.FIT;
            }
            if (lowerFilename.endsWith(".gpx")) {
                return FileFormat.GPX;
            }
        }

        throw new UnsupportedFileFormatException("Unable to detect file format from content or filename");
    }

    /**
     * Creates an activity from parsed data (internal method).
     * This method contains all the common logic for creating activities from any format.
     *
     * @param options processing options to control which side effects are executed
     */
    private Activity createActivityFromParsedData(
        ParsedActivityData parsedData,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility,
        byte[] rawFile,
        ProcessingOptions options
    ) {
        // Generate title if not provided
        String activityTitle = title != null && !title.isBlank()
            ? title
            : ActivityFormatter.generateActivityTitle(parsedData.getStartTime(), parsedData.getActivityType());

        // Default to PUBLIC if visibility not specified
        Activity.Visibility activityVisibility = visibility != null ? visibility : Activity.Visibility.PUBLIC;

        // Create activity entity
        Activity activity = Activity.builder()
            .userId(userId)
            .activityType(parsedData.getActivityType())
            .title(activityTitle)
            .description(description)
            .startedAt(parsedData.getStartTime())
            .endedAt(parsedData.getEndTime())
            .timezone(parsedData.getTimezone())
            .visibility(activityVisibility)
            .totalDistance(parsedData.getTotalDistance())
            .totalDurationSeconds(parsedData.getTotalDuration() != null ? parsedData.getTotalDuration().getSeconds() : null)
            .elevationGain(parsedData.getElevationGain())
            .elevationLoss(parsedData.getElevationLoss())
            .rawActivityFile(rawFile)
            .sourceFileFormat(parsedData.getSourceFormat())
            .build();

        // Convert track points to JSONB
        String trackPointsJson = convertTrackPointsToJson(parsedData.getTrackPoints());
        activity.setTrackPointsJson(trackPointsJson);

        // Create and simplify track only if GPS data is present
        if (!parsedData.getTrackPoints().isEmpty()) {
            // Create full LineString from all points
            LineString fullTrack = createLineStringFromTrackPoints(parsedData.getTrackPoints());

            // Simplify track for map rendering
            LineString simplifiedTrack = trackSimplifier.simplify(fullTrack.getCoordinates());
            activity.setSimplifiedTrack(simplifiedTrack);
        } else {
            // No GPS track for indoor activities
            activity.setSimplifiedTrack(null);
            log.info("Activity has no GPS track (indoor activity)");
        }

        // Create metrics
        if (parsedData.getMetrics() != null) {
            ActivityMetrics metrics = parsedData.getMetrics().toEntity(activity);
            calculateAdditionalMetrics(metrics, parsedData.getTrackPoints());
            activity.setMetrics(metrics);
        }

        // Save activity (single INSERT instead of 855!)
        Activity savedActivity = activityRepository.save(activity);

        if (savedActivity.getSimplifiedTrack() != null) {
            log.info("Successfully created {} activity {} with {} track points (simplified to {} for map)",
                parsedData.getSourceFormat(),
                savedActivity.getId(),
                parsedData.getTrackPoints().size(),
                savedActivity.getSimplifiedTrack().getNumPoints());
        } else {
            log.info("Successfully created {} activity {} (indoor activity without GPS track)",
                parsedData.getSourceFormat(),
                savedActivity.getId());
        }

        // Execute synchronous side effects based on processing options
        // Personal Records, Heatmap, and Weather are now handled asynchronously by caller (ActivityController)
        // In batch import mode, even synchronous operations are skipped and executed later as a batch

        if (!options.isSkipAchievements()) {
            log.debug("Checking achievements for activity {}", savedActivity.getId());
            achievementService.checkAndAwardAchievements(savedActivity);
        } else {
            log.debug("Skipping achievements check for activity {} (batch mode)", savedActivity.getId());
        }

        if (!options.isSkipTrainingLoad()) {
            log.debug("Updating training load for activity {}", savedActivity.getId());
            trainingLoadService.updateTrainingLoad(savedActivity);
        } else {
            log.debug("Skipping training load update for activity {} (batch mode)", savedActivity.getId());
        }

        if (!options.isSkipSummaries()) {
            log.debug("Updating summaries for activity {}", savedActivity.getId());
            activitySummaryService.updateSummariesForActivity(savedActivity);
        } else {
            log.debug("Skipping summaries update for activity {} (batch mode)", savedActivity.getId());
        }

        // Note: Async post-processing (Personal Records, Heatmap, Weather, Federation)
        // is triggered by the caller (ActivityController) via ActivityPostProcessingService
        // This keeps ActivityFileService focused on file parsing and initial activity save

        return savedActivity;
    }

    /**
     * Converts track points to JSON string for JSONB storage.
     */
    private String convertTrackPointsToJson(List<ParsedActivityData.TrackPointData> trackPoints) {
        try {
            return objectMapper.writeValueAsString(trackPoints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize track points to JSON", e);
        }
    }

    /**
     * Creates a PostGIS LineString from track points.
     */
    private LineString createLineStringFromTrackPoints(List<ParsedActivityData.TrackPointData> trackPoints) {
        Coordinate[] coordinates = trackPoints.stream()
            .map(tp -> new Coordinate(tp.getLongitude(), tp.getLatitude()))
            .toArray(Coordinate[]::new);

        return GEOMETRY_FACTORY.createLineString(coordinates);
    }

    /**
     * Calculates additional metrics that might not be in parsed data.
     * For GPX files, most metrics are already calculated in GpxParser.
     * For FIT files, some additional metrics like min/max elevation need calculation.
     */
    private void calculateAdditionalMetrics(
        ActivityMetrics metrics,
        List<ParsedActivityData.TrackPointData> trackPoints
    ) {
        if (trackPoints.isEmpty()) {
            return;
        }

        // Calculate min/max elevation if not already set
        if (metrics.getMinElevation() == null || metrics.getMaxElevation() == null) {
            BigDecimal minElevation = null;
            BigDecimal maxElevation = null;

            for (ParsedActivityData.TrackPointData tp : trackPoints) {
                if (tp.getElevation() != null) {
                    if (minElevation == null || tp.getElevation().compareTo(minElevation) < 0) {
                        minElevation = tp.getElevation();
                    }
                    if (maxElevation == null || tp.getElevation().compareTo(maxElevation) > 0) {
                        maxElevation = tp.getElevation();
                    }
                }
            }

            if (metrics.getMinElevation() == null) metrics.setMinElevation(minElevation);
            if (metrics.getMaxElevation() == null) metrics.setMaxElevation(maxElevation);
        }

        // Calculate average temperature if not already set
        if (metrics.getAverageTemperature() == null) {
            BigDecimal tempSum = BigDecimal.ZERO;
            int tempCount = 0;

            for (ParsedActivityData.TrackPointData tp : trackPoints) {
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
    }

    /**
     * Enum for supported file formats.
     */
    private enum FileFormat {
        FIT, GPX
    }
}
