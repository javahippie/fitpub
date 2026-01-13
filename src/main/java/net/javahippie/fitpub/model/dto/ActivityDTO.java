package net.javahippie.fitpub.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.LineString;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.PrivacyZone;
import net.javahippie.fitpub.service.TrackPrivacyFilter;
import net.javahippie.fitpub.util.ActivityFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DTO for Activity data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {

    private UUID id;
    private UUID userId;
    private String activityType;
    private String title;
    private String description;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String timezone; // IANA timezone ID (e.g., "Europe/Berlin")
    private String visibility;
    private BigDecimal totalDistance;
    private Long totalDurationSeconds;
    private BigDecimal elevationGain;
    private BigDecimal elevationLoss;
    private ActivityMetricsDTO metrics;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Map rendering data
    private Map<String, Object> simplifiedTrack; // GeoJSON LineString
    private List<Map<String, Object>> trackPoints; // Full track points from JSONB
    private Boolean hasGpsTrack; // True if activity has GPS data (outdoor), false for indoor activities

    // Indoor activity detection
    private Boolean indoor; // True if activity was performed indoors
    private String subSport; // SubSport field from FIT file (e.g., INDOOR_CYCLING, TREADMILL)
    private String indoorDetectionMethod; // How indoor flag was determined

    // Race/competition flag
    private Boolean race; // True if activity is a race/competition (uses total time for pace calculation)

    // Social interaction counts (populated separately)
    private Long likesCount;
    private Long commentsCount;
    private Boolean likedByCurrentUser; // True if current user has liked this activity

    // Privacy zones (only for activity owner, to show what's hidden for others)
    private List<PrivacyZonePreview> privacyZones;

    // Convenience getters for flattened metrics (for frontend compatibility)
    public Integer getAverageHeartRate() {
        return metrics != null ? metrics.getAverageHeartRate() : null;
    }

    public Integer getMaxHeartRate() {
        return metrics != null ? metrics.getMaxHeartRate() : null;
    }

    public Integer getAverageCadence() {
        return metrics != null ? metrics.getAverageCadence() : null;
    }

    public BigDecimal getAverageSpeed() {
        return metrics != null ? metrics.getAverageSpeed() : null;
    }

    public BigDecimal getMaxSpeed() {
        return metrics != null ? metrics.getMaxSpeed() : null;
    }

    public Integer getCalories() {
        return metrics != null ? metrics.getCalories() : null;
    }

    public Long getMovingTimeSeconds() {
        return metrics != null ? metrics.getMovingTimeSeconds() : null;
    }

    public Long getStoppedTimeSeconds() {
        return metrics != null ? metrics.getStoppedTimeSeconds() : null;
    }

    // Alias for frontend compatibility
    public Long getTotalDuration() {
        return totalDurationSeconds;
    }

    /**
     * Creates a DTO from an Activity entity.
     */
    public static ActivityDTO fromEntity(Activity activity) {
        ActivityDTOBuilder builder = ActivityDTO.builder()
            .id(activity.getId())
            .userId(activity.getUserId())
            .activityType(ActivityFormatter.formatActivityType(activity.getActivityType()))
            .title(activity.getTitle())
            .description(activity.getDescription())
            .startedAt(activity.getStartedAt())
            .endedAt(activity.getEndedAt())
            .timezone(activity.getTimezone())
            .visibility(activity.getVisibility().name())
            .totalDistance(activity.getTotalDistance())
            .elevationGain(activity.getElevationGain())
            .elevationLoss(activity.getElevationLoss())
            .createdAt(activity.getCreatedAt())
            .updatedAt(activity.getUpdatedAt());

        if (activity.getTotalDurationSeconds() != null) {
            builder.totalDurationSeconds(activity.getTotalDurationSeconds());
        }

        if (activity.getMetrics() != null) {
            builder.metrics(ActivityMetricsDTO.fromEntity(activity.getMetrics()));
        }

        // Convert simplified track to GeoJSON
        boolean hasGps = activity.getSimplifiedTrack() != null;
        builder.hasGpsTrack(hasGps);

        if (hasGps) {
            builder.simplifiedTrack(lineStringToGeoJson(activity.getSimplifiedTrack()));
        }

        // Parse track points from JSONB
        if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
            builder.trackPoints(parseTrackPoints(activity.getTrackPointsJson()));
        }

        // Indoor activity detection fields
        builder.indoor(activity.getIndoor() != null ? activity.getIndoor() : false);
        builder.subSport(activity.getSubSport());
        builder.indoorDetectionMethod(activity.getIndoorDetectionMethod());

        // Race flag
        builder.race(activity.getRace() != null ? activity.getRace() : false);

        return builder.build();
    }

    /**
     * Creates a DTO from an Activity entity with privacy zone filtering applied.
     * Filters GPS coordinates that fall within the activity owner's privacy zones.
     *
     * @param activity the activity entity
     * @param requestingUserId the ID of the user requesting the activity (null for anonymous)
     * @param privacyZones the activity owner's active privacy zones
     * @param filter the privacy filter service
     * @return activity DTO with filtered GPS data, or null if entire track was filtered
     */
    public static ActivityDTO fromEntityWithFiltering(
        Activity activity,
        UUID requestingUserId,
        List<PrivacyZone> privacyZones,
        TrackPrivacyFilter filter
    ) {
        // If requester is the activity owner, don't filter (show full track)
        boolean isOwner = requestingUserId != null && requestingUserId.equals(activity.getUserId());

        // If no privacy zones or requester is owner, use standard conversion
        if (privacyZones == null || privacyZones.isEmpty() || isOwner) {
            if (isOwner && privacyZones != null && !privacyZones.isEmpty()) {
                org.slf4j.LoggerFactory.getLogger(ActivityDTO.class)
                    .info("Activity {} - Owner viewing, bypassing {} privacy zones",
                          activity.getId(), privacyZones.size());

                // For owner, return full track but include privacy zones for visualization
                ActivityDTO dto = fromEntity(activity);
                dto.setPrivacyZones(privacyZones.stream()
                    .map(zone -> PrivacyZonePreview.builder()
                        .id(zone.getId())
                        .name(zone.getName())
                        .latitude(zone.getCenterPoint().getY())
                        .longitude(zone.getCenterPoint().getX())
                        .radiusMeters(zone.getRadiusMeters())
                        .build())
                    .collect(java.util.stream.Collectors.toList()));
                return dto;
            }
            return fromEntity(activity);
        }

        // Apply filtering to tracks
        LineString filteredSimplifiedTrack = null;
        String filteredTrackPointsJson = null;

        if (activity.getSimplifiedTrack() != null) {
            filteredSimplifiedTrack = filter.filterLineString(activity.getSimplifiedTrack(), privacyZones);
        }

        if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
            filteredTrackPointsJson = filter.filterTrackPointsJson(activity.getTrackPointsJson(), privacyZones);
        }

        // If entire track was filtered out, return null (activity is completely private)
        if (filteredSimplifiedTrack == null && filteredTrackPointsJson == null) {
            // Return basic activity info without GPS data
            return ActivityDTO.builder()
                .id(activity.getId())
                .userId(activity.getUserId())
                .activityType(ActivityFormatter.formatActivityType(activity.getActivityType()))
                .title(activity.getTitle())
                .description(activity.getDescription())
                .startedAt(activity.getStartedAt())
                .endedAt(activity.getEndedAt())
                .timezone(activity.getTimezone())
                .visibility(activity.getVisibility().name())
                .totalDistance(activity.getTotalDistance())
                .totalDurationSeconds(activity.getTotalDurationSeconds())
                .elevationGain(activity.getElevationGain())
                .elevationLoss(activity.getElevationLoss())
                .metrics(activity.getMetrics() != null ? ActivityMetricsDTO.fromEntity(activity.getMetrics()) : null)
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .hasGpsTrack(false) // Mark as no GPS data available
                .indoor(activity.getIndoor() != null ? activity.getIndoor() : false)
                .subSport(activity.getSubSport())
                .indoorDetectionMethod(activity.getIndoorDetectionMethod())
                .race(activity.getRace() != null ? activity.getRace() : false)
                .build();
        }

        // Build DTO with filtered tracks
        ActivityDTOBuilder builder = ActivityDTO.builder()
            .id(activity.getId())
            .userId(activity.getUserId())
            .activityType(ActivityFormatter.formatActivityType(activity.getActivityType()))
            .title(activity.getTitle())
            .description(activity.getDescription())
            .startedAt(activity.getStartedAt())
            .endedAt(activity.getEndedAt())
            .timezone(activity.getTimezone())
            .visibility(activity.getVisibility().name())
            .totalDistance(activity.getTotalDistance())
            .elevationGain(activity.getElevationGain())
            .elevationLoss(activity.getElevationLoss())
            .createdAt(activity.getCreatedAt())
            .updatedAt(activity.getUpdatedAt());

        if (activity.getTotalDurationSeconds() != null) {
            builder.totalDurationSeconds(activity.getTotalDurationSeconds());
        }

        if (activity.getMetrics() != null) {
            builder.metrics(ActivityMetricsDTO.fromEntity(activity.getMetrics()));
        }

        // Add filtered GPS data
        boolean hasGps = filteredSimplifiedTrack != null;
        builder.hasGpsTrack(hasGps);

        if (hasGps) {
            builder.simplifiedTrack(lineStringToGeoJson(filteredSimplifiedTrack));
        }

        if (filteredTrackPointsJson != null) {
            builder.trackPoints(parseTrackPoints(filteredTrackPointsJson));
        }

        // Indoor activity detection fields
        builder.indoor(activity.getIndoor() != null ? activity.getIndoor() : false);
        builder.subSport(activity.getSubSport());
        builder.indoorDetectionMethod(activity.getIndoorDetectionMethod());

        // Race flag
        builder.race(activity.getRace() != null ? activity.getRace() : false);

        return builder.build();
    }

    /**
     * Converts a JTS LineString to GeoJSON format.
     */
    private static Map<String, Object> lineStringToGeoJson(LineString lineString) {
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "LineString");

        List<List<Double>> coordinates = Stream.of(lineString.getCoordinates())
            .map(coord -> List.of(coord.getX(), coord.getY()))
            .collect(Collectors.toList());

        geoJson.put("coordinates", coordinates);
        return geoJson;
    }

    /**
     * Parses track points from JSONB string.
     */
    private static List<Map<String, Object>> parseTrackPoints(String trackPointsJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(trackPointsJson);

            if (root.isArray()) {
                List<Map<String, Object>> trackPoints = new java.util.ArrayList<>();
                for (JsonNode node : root) {
                    Map<String, Object> point = new LinkedHashMap<>();

                    if (node.has("timestamp")) point.put("timestamp", node.get("timestamp").asText());
                    if (node.has("latitude")) point.put("latitude", node.get("latitude").asDouble());
                    if (node.has("longitude")) point.put("longitude", node.get("longitude").asDouble());
                    if (node.has("elevation")) point.put("elevation", node.get("elevation").asDouble());
                    if (node.has("heartRate")) point.put("heartRate", node.get("heartRate").asInt());
                    if (node.has("cadence")) point.put("cadence", node.get("cadence").asInt());
                    if (node.has("speed")) point.put("speed", node.get("speed").asDouble());
                    if (node.has("power")) point.put("power", node.get("power").asInt());
                    if (node.has("temperature")) point.put("temperature", node.get("temperature").asDouble());

                    trackPoints.add(point);
                }
                return trackPoints;
            }
        } catch (Exception e) {
            // Log error but don't fail the entire DTO creation
            System.err.println("Error parsing track points JSON: " + e.getMessage());
        }
        return null;
    }

    /**
     * Simple preview of a privacy zone for map rendering.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacyZonePreview {
        private UUID id;
        private String name;
        private Double latitude;
        private Double longitude;
        private Integer radiusMeters;
    }
}
