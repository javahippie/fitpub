package org.operaton.fitpub.util;

import lombok.Data;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Common data structure for parsed activity files (FIT, GPX, etc.).
 * Contains track points, metrics, and metadata extracted from the file.
 */
@Data
public class ParsedActivityData {
    private List<TrackPointData> trackPoints = new ArrayList<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime activityTimestamp;
    private String timezone; // IANA timezone ID (e.g., "Europe/Berlin")
    private BigDecimal totalDistance;
    private Duration totalDuration;
    private BigDecimal elevationGain;
    private BigDecimal elevationLoss;
    private Activity.ActivityType activityType = Activity.ActivityType.OTHER;
    private ActivityMetricsData metrics;
    private String sourceFormat; // "FIT" or "GPX"

    /**
     * Data class for track point information.
     */
    @Data
    public static class TrackPointData {
        private static final int WGS84_SRID = 4326;
        private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), WGS84_SRID);

        private LocalDateTime timestamp;
        private double latitude;
        private double longitude;
        private BigDecimal elevation;
        private Integer heartRate;
        private Integer cadence;
        private Integer power;
        /** Speed in km/h (converted from m/s in FitParser/GpxParser). */
        private BigDecimal speed;
        private BigDecimal temperature;
        private BigDecimal distance;

        public Point toGeometry() {
            return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        }
    }

    /**
     * Data class for activity metrics.
     */
    @Data
    public static class ActivityMetricsData {
        private BigDecimal averageSpeed;
        private BigDecimal maxSpeed;
        private Duration averagePace;
        private Integer averageHeartRate;
        private Integer maxHeartRate;
        private Integer averageCadence;
        private Integer maxCadence;
        private Integer averagePower;
        private Integer maxPower;
        private Integer normalizedPower;
        private Integer calories;
        private BigDecimal averageTemperature;
        private BigDecimal maxElevation;
        private BigDecimal minElevation;
        private BigDecimal totalAscent;
        private BigDecimal totalDescent;
        private Duration movingTime;
        private Duration stoppedTime;
        private Integer totalSteps;

        public ActivityMetrics toEntity(Activity activity) {
            return ActivityMetrics.builder()
                .activity(activity)
                .averageSpeed(averageSpeed)
                .maxSpeed(maxSpeed)
                .averagePaceSeconds(averagePace != null ? averagePace.getSeconds() : null)
                .averageHeartRate(averageHeartRate)
                .maxHeartRate(maxHeartRate)
                .averageCadence(averageCadence)
                .maxCadence(maxCadence)
                .averagePower(averagePower)
                .maxPower(maxPower)
                .normalizedPower(normalizedPower)
                .calories(calories)
                .averageTemperature(averageTemperature)
                .maxElevation(maxElevation)
                .minElevation(minElevation)
                .totalAscent(totalAscent)
                .totalDescent(totalDescent)
                .movingTimeSeconds(movingTime != null ? movingTime.getSeconds() : null)
                .stoppedTimeSeconds(stoppedTime != null ? stoppedTime.getSeconds() : null)
                .totalSteps(totalSteps)
                .build();
        }
    }
}
