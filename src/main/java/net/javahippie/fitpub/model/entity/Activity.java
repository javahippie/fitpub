package net.javahippie.fitpub.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.LineString;

import javax.sound.midi.Track;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Entity representing a fitness activity (workout).
 * Stores metadata, simplified track for map rendering, and full track data as JSONB.
 * This design optimizes for scalability by avoiding normalized track_points table.
 */
@Entity
@Table(name = "activities", indexes = {
    @Index(name = "idx_activity_user_id", columnList = "user_id"),
    @Index(name = "idx_activity_started_at", columnList = "started_at"),
    @Index(name = "idx_activity_type", columnList = "activity_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    /**
     * Timezone ID where the activity was recorded (e.g., "Europe/Berlin", "America/New_York").
     * Stored to display activity times in the athlete's local timezone.
     * Defaults to UTC if timezone cannot be determined from FIT file.
     */
    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    /**
     * Simplified track for map rendering (50-200 points).
     * Uses Douglas-Peucker algorithm to reduce point count while maintaining shape.
     */
    @Column(name = "simplified_track", columnDefinition = "geometry(LineString, 4326)")
    private LineString simplifiedTrack;

    /**
     * Full track data stored as JSONB for detail view.
     * Contains all original track points with sensor data.
     * Much more efficient than normalized track_points table.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "track_points_json", columnDefinition = "jsonb")
    private String trackPointsJson;

    @Column(name = "total_distance", precision = 10, scale = 2)
    private BigDecimal totalDistance;

    @Column(name = "total_duration_seconds")
    private Long totalDurationSeconds;

    @Column(name = "elevation_gain", precision = 8, scale = 2)
    private BigDecimal elevationGain;

    @Column(name = "elevation_loss", precision = 8, scale = 2)
    private BigDecimal elevationLoss;

    @Column(name = "activity_location")
    private String activityLocation;

    /**
     * Original activity file (FIT or GPX) for re-processing if needed.
     * Allows us to re-parse with updated algorithms.
     */
    @Lob
    @Column(name = "raw_activity_file")
    private byte[] rawActivityFile;

    /**
     * Source file format: "FIT" (Garmin/Wahoo devices) or "GPX" (GPS Exchange Format).
     */
    @Column(name = "source_file_format", nullable = false, length = 10)
    private String sourceFileFormat;

    /**
     * Indicates if this is an indoor activity (e.g., virtual rides, indoor trainer sessions).
     * Indoor activities are displayed in timeline but excluded from heatmap generation.
     */
    @Column(name = "indoor", nullable = false)
    @Builder.Default
    private Boolean indoor = false;

    /**
     * SubSport from FIT file (e.g., INDOOR_CYCLING, TREADMILL, ROAD, MOUNTAIN, TRAIL).
     * NULL for GPX files or if not available.
     */
    @Column(name = "sub_sport", length = 50)
    private String subSport;

    /**
     * Method used to determine the indoor flag.
     * Values: FIT_SUBSPORT, GPX_EXTENSION, HEURISTIC_NO_GPS, HEURISTIC_STATIONARY, MANUAL
     * NULL for legacy activities uploaded before this feature.
     */
    @Column(name = "indoor_detection_method", length = 20)
    private String indoorDetectionMethod;

    /**
     * Indicates if this is a race/competition activity.
     * Race activities use total time for pace calculation instead of moving time.
     */
    @Column(name = "race", nullable = false)
    @Builder.Default
    private Boolean race = false;

    @OneToOne(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    private ActivityMetrics metrics;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Optional<TrackPoint> findFirstTrackpoint() throws JsonProcessingException {
        // Get first track point for location
        JsonNode trackPoints = new ObjectMapper().readTree(this.getTrackPointsJson());
        log.debug("Parsed track points, is array: {}, size: {}",
                trackPoints.isArray(), trackPoints.isArray() ? trackPoints.size() : "N/A");

        if (!trackPoints.isArray() || trackPoints.isEmpty()) {
            log.warn("Track points is not an array or is empty for activity {}", this.getId());
            return Optional.empty();
        }

        JsonNode firstPoint = trackPoints.get(0);
        log.debug("First track point JSON: {}", firstPoint.toString());

        // Check if lat/lon fields exist (support both "lat"/"lon" and "latitude"/"longitude")
        boolean hasLat = firstPoint.has("lat") || firstPoint.has("latitude");
        boolean hasLon = firstPoint.has("lon") || firstPoint.has("longitude");

        if (!hasLat || !hasLon) {
            // Collect field names from iterator
            java.util.List<String> fieldNames = new java.util.ArrayList<>();
            firstPoint.fieldNames().forEachRemaining(fieldNames::add);

            log.error("First track point MISSING lat/lon fields for activity {}.", this.getId());
            log.error("Available fields in track point: {}", fieldNames);
            log.error("First track point content: {}", firstPoint);
            return Optional.empty();
        }

        // Extract coordinates (try both short and long field names)
        double lat = firstPoint.has("lat") ? firstPoint.get("lat").asDouble() : firstPoint.get("latitude").asDouble();
        double lon = firstPoint.has("lon") ? firstPoint.get("lon").asDouble() : firstPoint.get("longitude").asDouble();
        log.debug("Extracted location from first track point: lat={}, lon={}", lat, lon);
        return Optional.of(new TrackPoint(lon, lat));
    }

    /**
     * Helper method to set metrics for this activity
     */
    public void setMetrics(ActivityMetrics metrics) {
        this.metrics = metrics;
        if (metrics != null) {
            metrics.setActivity(this);
        }
    }

    /**
     * Activity types supported by the platform
     */
    public enum ActivityType {
        RUN,
        RIDE,
        HIKE,
        WALK,
        SWIM,
        ALPINE_SKI,
        BACKCOUNTRY_SKI,
        NORDIC_SKI,
        SNOWBOARD,
        ROWING,
        KAYAKING,
        CANOEING,
        INLINE_SKATING,
        ROCK_CLIMBING,
        MOUNTAINEERING,
        YOGA,
        WORKOUT,
        OTHER
    }

    /**
     * Visibility levels for activities
     */
    public enum Visibility {
        PUBLIC,
        FOLLOWERS,
        PRIVATE
    }

    /**
     * Methods for detecting indoor activities
     */
    public enum IndoorDetectionMethod {
        /** Detected from FIT file SubSport field (most accurate) */
        FIT_SUBSPORT,
        /** Detected from GPX file extension fields */
        GPX_EXTENSION,
        /** Heuristic: No GPS track data present */
        HEURISTIC_NO_GPS,
        /** Heuristic: GPS track exists but all points are stationary (within 50m radius) */
        HEURISTIC_STATIONARY,
        /** Manually set by user */
        MANUAL
    }
}
