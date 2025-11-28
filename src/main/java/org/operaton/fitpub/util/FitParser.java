package org.operaton.fitpub.util;

import com.garmin.fit.*;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.operaton.fitpub.exception.FitFileProcessingException;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Garmin FIT files.
 * Extracts GPS coordinates, activity metrics, and sensor data.
 */
@Component
@Slf4j
public class FitParser {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private static final double SEMICIRCLES_TO_DEGREES = 180.0 / Math.pow(2, 31);
    private static final double MPS_TO_KPH = 3.6;

    /**
     * Parses a FIT file and returns the extracted data.
     *
     * @param fileData the FIT file data
     * @return ParsedFitData containing activity information
     * @throws FitFileProcessingException if parsing fails
     */
    public ParsedFitData parse(byte[] fileData) {
        try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
            return parse(inputStream);
        } catch (Exception e) {
            throw new FitFileProcessingException("Failed to parse FIT file", e);
        }
    }

    /**
     * Parses a FIT file from an input stream.
     *
     * @param inputStream the input stream
     * @return ParsedFitData containing activity information
     * @throws FitFileProcessingException if parsing fails
     */
    public ParsedFitData parse(InputStream inputStream) {
        ParsedFitData parsedData = new ParsedFitData();
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

        // Listen for record messages (GPS points)
        broadcaster.addListener((RecordMesgListener) record -> {
            TrackPointData trackPoint = extractTrackPoint(record);
            if (trackPoint != null) {
                parsedData.getTrackPoints().add(trackPoint);
            }
        });

        // Listen for session messages (summary data)
        broadcaster.addListener((SessionMesgListener) session -> {
            extractSessionData(session, parsedData);
        });

        // Listen for activity messages
        broadcaster.addListener((ActivityMesgListener) activity -> {
            extractActivityData(activity, parsedData);
        });

        // Listen for lap messages
        broadcaster.addListener((LapMesgListener) lap -> {
            log.debug("Lap data: distance={}, time={}", lap.getTotalDistance(), lap.getTotalTimerTime());
        });

        try {
            if (!decode.read(inputStream, broadcaster)) {
                throw new FitFileProcessingException("Failed to decode FIT file");
            }

            if (parsedData.getTrackPoints().isEmpty()) {
                throw new FitFileProcessingException("No GPS track points found in FIT file");
            }

            log.info("Successfully parsed FIT file: {} track points, activity type: {}",
                parsedData.getTrackPoints().size(), parsedData.getActivityType());

            return parsedData;
        } catch (FitRuntimeException e) {
            throw new FitFileProcessingException("Error decoding FIT file", e);
        }
    }

    /**
     * Extracts a track point from a record message.
     */
    private TrackPointData extractTrackPoint(RecordMesg record) {
        Integer positionLat = record.getPositionLat();
        Integer positionLong = record.getPositionLong();

        if (positionLat == null || positionLong == null) {
            return null; // Skip points without GPS coordinates
        }

        TrackPointData point = new TrackPointData();

        // Convert semicircles to degrees
        double latitude = positionLat * SEMICIRCLES_TO_DEGREES;
        double longitude = positionLong * SEMICIRCLES_TO_DEGREES;

        point.setLatitude(latitude);
        point.setLongitude(longitude);

        // Extract timestamp
        if (record.getTimestamp() != null) {
            point.setTimestamp(convertDateTime(record.getTimestamp()));
        }

        // Extract elevation
        if (record.getAltitude() != null) {
            point.setElevation(BigDecimal.valueOf(record.getAltitude()).setScale(2, RoundingMode.HALF_UP));
        }

        // Extract heart rate
        if (record.getHeartRate() != null) {
            point.setHeartRate(record.getHeartRate().intValue());
        }

        // Extract cadence
        if (record.getCadence() != null) {
            point.setCadence(record.getCadence().intValue());
        }

        // Extract power
        if (record.getPower() != null) {
            point.setPower(record.getPower());
        }

        // Extract speed (convert m/s to km/h)
        if (record.getSpeed() != null) {
            point.setSpeed(BigDecimal.valueOf(record.getSpeed() * MPS_TO_KPH)
                .setScale(2, RoundingMode.HALF_UP));
        }

        // Extract distance
        if (record.getDistance() != null) {
            point.setDistance(BigDecimal.valueOf(record.getDistance()).setScale(2, RoundingMode.HALF_UP));
        }

        // Extract temperature
        if (record.getTemperature() != null) {
            point.setTemperature(BigDecimal.valueOf(record.getTemperature()).setScale(2, RoundingMode.HALF_UP));
        }

        return point;
    }

    /**
     * Extracts session data from a session message.
     */
    private void extractSessionData(SessionMesg session, ParsedFitData parsedData) {
        if (session.getStartTime() != null) {
            parsedData.setStartTime(convertDateTime(session.getStartTime()));
        }

        if (session.getTotalElapsedTime() != null) {
            long totalSeconds = session.getTotalElapsedTime().longValue();
            parsedData.setTotalDuration(Duration.ofSeconds(totalSeconds));

            if (parsedData.getStartTime() != null) {
                parsedData.setEndTime(parsedData.getStartTime().plus(Duration.ofSeconds(totalSeconds)));
            }
        }

        if (session.getTotalDistance() != null) {
            parsedData.setTotalDistance(
                BigDecimal.valueOf(session.getTotalDistance()).setScale(2, RoundingMode.HALF_UP)
            );
        }

        if (session.getTotalAscent() != null) {
            parsedData.setElevationGain(
                BigDecimal.valueOf(session.getTotalAscent()).setScale(2, RoundingMode.HALF_UP)
            );
        }

        if (session.getTotalDescent() != null) {
            parsedData.setElevationLoss(
                BigDecimal.valueOf(session.getTotalDescent()).setScale(2, RoundingMode.HALF_UP)
            );
        }

        // Extract metrics
        ActivityMetricsData metrics = new ActivityMetricsData();

        if (session.getAvgSpeed() != null) {
            metrics.setAverageSpeed(
                BigDecimal.valueOf(session.getAvgSpeed() * MPS_TO_KPH).setScale(2, RoundingMode.HALF_UP)
            );
        }

        if (session.getMaxSpeed() != null) {
            metrics.setMaxSpeed(
                BigDecimal.valueOf(session.getMaxSpeed() * MPS_TO_KPH).setScale(2, RoundingMode.HALF_UP)
            );
        }

        if (session.getAvgHeartRate() != null) {
            metrics.setAverageHeartRate(session.getAvgHeartRate().intValue());
        }

        if (session.getMaxHeartRate() != null) {
            metrics.setMaxHeartRate(session.getMaxHeartRate().intValue());
        }

        if (session.getAvgCadence() != null) {
            metrics.setAverageCadence(session.getAvgCadence().intValue());
        }

        if (session.getMaxCadence() != null) {
            metrics.setMaxCadence(session.getMaxCadence().intValue());
        }

        if (session.getAvgPower() != null) {
            metrics.setAveragePower(session.getAvgPower());
        }

        if (session.getMaxPower() != null) {
            metrics.setMaxPower(session.getMaxPower());
        }

        if (session.getNormalizedPower() != null) {
            metrics.setNormalizedPower(session.getNormalizedPower());
        }

        if (session.getTotalCalories() != null) {
            metrics.setCalories(session.getTotalCalories());
        }

        if (session.getTotalMovingTime() != null) {
            metrics.setMovingTime(Duration.ofSeconds(session.getTotalMovingTime().longValue()));
        }

        if (session.getTotalStrides() != null) {
            metrics.setTotalSteps(session.getTotalStrides().intValue() * 2); // Strides to steps
        }

        parsedData.setMetrics(metrics);

        // Determine activity type
        if (session.getSport() != null) {
            parsedData.setActivityType(mapSportToActivityType(session.getSport()));
        }
    }

    /**
     * Extracts activity data from an activity message.
     */
    private void extractActivityData(ActivityMesg activity, ParsedFitData parsedData) {
        if (activity.getTimestamp() != null) {
            parsedData.setActivityTimestamp(convertDateTime(activity.getTimestamp()));
        }

        if (activity.getTotalTimerTime() != null) {
            log.debug("Activity total timer time: {}", activity.getTotalTimerTime());
        }
    }

    /**
     * Converts FIT DateTime to LocalDateTime.
     */
    private LocalDateTime convertDateTime(DateTime dateTime) {
        long timestamp = dateTime.getTimestamp();
        Instant instant = Instant.ofEpochSecond(timestamp);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * Maps FIT sport type to our activity type.
     */
    private Activity.ActivityType mapSportToActivityType(Sport sport) {
        if (sport == Sport.RUNNING) {
            return Activity.ActivityType.RUN;
        } else if (sport == Sport.CYCLING) {
            return Activity.ActivityType.RIDE;
        } else if (sport == Sport.HIKING) {
            return Activity.ActivityType.HIKE;
        } else if (sport == Sport.WALKING) {
            return Activity.ActivityType.WALK;
        } else if (sport == Sport.SWIMMING) {
            return Activity.ActivityType.SWIM;
        } else if (sport == Sport.ROWING) {
            return Activity.ActivityType.ROWING;
        } else {
            return Activity.ActivityType.OTHER;
        }
    }

    /**
     * Data class for track point information.
     */
    @lombok.Data
    public static class TrackPointData {
        private LocalDateTime timestamp;
        private double latitude;
        private double longitude;
        private BigDecimal elevation;
        private Integer heartRate;
        private Integer cadence;
        private Integer power;
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
    @lombok.Data
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

    /**
     * Data class holding all parsed FIT file data.
     */
    @lombok.Data
    public static class ParsedFitData {
        private List<TrackPointData> trackPoints = new ArrayList<>();
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime activityTimestamp;
        private BigDecimal totalDistance;
        private Duration totalDuration;
        private BigDecimal elevationGain;
        private BigDecimal elevationLoss;
        private Activity.ActivityType activityType = Activity.ActivityType.OTHER;
        private ActivityMetricsData metrics;
    }
}
