package org.operaton.fitpub.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.operaton.fitpub.model.entity.Activity;

import java.math.BigDecimal;
import java.time.Duration;
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

    // Social interaction counts (populated separately)
    private Long likesCount;
    private Long commentsCount;
    private Boolean likedByCurrentUser; // True if current user has liked this activity

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
            .activityType(activity.getActivityType().name())
            .title(activity.getTitle())
            .description(activity.getDescription())
            .startedAt(activity.getStartedAt())
            .endedAt(activity.getEndedAt())
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
        if (activity.getSimplifiedTrack() != null) {
            builder.simplifiedTrack(lineStringToGeoJson(activity.getSimplifiedTrack()));
        }

        // Parse track points from JSONB
        if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
            builder.trackPoints(parseTrackPoints(activity.getTrackPointsJson()));
        }

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
}
