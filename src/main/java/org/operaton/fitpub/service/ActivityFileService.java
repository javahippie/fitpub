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

    private final FitFileValidator fitValidator;
    private final GpxFileValidator gpxValidator;
    private final FitParser fitParser;
    private final GpxParser gpxParser;
    private final TrackSimplifier trackSimplifier;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final PersonalRecordService personalRecordService;
    private final AchievementService achievementService;
    private final TrainingLoadService trainingLoadService;
    private final ActivitySummaryService activitySummaryService;
    private final WeatherService weatherService;
    private final HeatmapGridService heatmapGridService;

    /**
     * Processes an uploaded activity file (FIT or GPX) and creates an activity.
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
            return createActivityFromParsedData(parsedData, userId, title, description, visibility, fileData);
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
     */
    private Activity createActivityFromParsedData(
        ParsedActivityData parsedData,
        UUID userId,
        String title,
        String description,
        Activity.Visibility visibility,
        byte[] rawFile
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

        // Create full LineString from all points
        LineString fullTrack = createLineStringFromTrackPoints(parsedData.getTrackPoints());

        // Simplify track for map rendering
        LineString simplifiedTrack = trackSimplifier.simplify(fullTrack.getCoordinates());
        activity.setSimplifiedTrack(simplifiedTrack);

        // Create metrics
        if (parsedData.getMetrics() != null) {
            ActivityMetrics metrics = parsedData.getMetrics().toEntity(activity);
            calculateAdditionalMetrics(metrics, parsedData.getTrackPoints());
            activity.setMetrics(metrics);
        }

        // Save activity (single INSERT instead of 855!)
        Activity savedActivity = activityRepository.save(activity);

        log.info("Successfully created {} activity {} with {} track points (simplified to {} for map)",
            parsedData.getSourceFormat(),
            savedActivity.getId(),
            parsedData.getTrackPoints().size(),
            simplifiedTrack.getNumPoints());

        // Check for personal records and achievements
        personalRecordService.checkAndUpdatePersonalRecords(savedActivity);
        achievementService.checkAndAwardAchievements(savedActivity);

        // Update heatmap grid
        heatmapGridService.updateHeatmapForActivity(savedActivity);

        // Update training load and summaries (async)
        trainingLoadService.updateTrainingLoad(savedActivity);
        activitySummaryService.updateSummariesForActivity(savedActivity);

        // Fetch weather data (async, non-blocking)
        try {
            weatherService.fetchWeatherForActivity(savedActivity);
        } catch (Exception e) {
            log.warn("Failed to fetch weather data for activity {}: {}", savedActivity.getId(), e.getMessage());
            // Don't fail the activity creation if weather fetching fails
        }

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
