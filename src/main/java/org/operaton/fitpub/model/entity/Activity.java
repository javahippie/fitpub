package org.operaton.fitpub.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /**
     * Original FIT file for re-processing if needed.
     * Allows us to re-parse with updated algorithms.
     */
    @Lob
    @Column(name = "raw_fit_file")
    private byte[] rawFitFile;

    @OneToOne(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    private ActivityMetrics metrics;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
}
