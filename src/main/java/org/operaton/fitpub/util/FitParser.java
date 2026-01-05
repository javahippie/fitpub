package org.operaton.fitpub.util;

import com.garmin.fit.*;
import lombok.extern.slf4j.Slf4j;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.operaton.fitpub.exception.FitFileProcessingException;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;
import org.operaton.fitpub.util.ParsedActivityData.ActivityMetricsData;
import org.operaton.fitpub.util.ParsedActivityData.TrackPointData;
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
import java.util.Optional;

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
    private static final double STOPPED_SPEED_THRESHOLD = 0.5; // km/h - below this is considered stopped
    private static final long STOPPED_TIME_THRESHOLD = 30; // seconds - must be stopped this long to count

    // Lazy-loaded timezone engine (expensive to initialize)
    private static TimeZoneEngine timezoneEngine = null;

    private final SpeedSmoother speedSmoother;

    public FitParser(SpeedSmoother speedSmoother) {
        this.speedSmoother = speedSmoother;
    }

    /**
     * Parses a FIT file and returns the extracted data.
     *
     * @param fileData the FIT file data
     * @return ParsedActivityData containing activity information
     * @throws FitFileProcessingException if parsing fails
     */
    public ParsedActivityData parse(byte[] fileData) {
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
     * @return ParsedActivityData containing activity information
     * @throws FitFileProcessingException if parsing fails
     */
    public ParsedActivityData parse(InputStream inputStream) {
        ParsedActivityData parsedData = new ParsedActivityData();
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

            // Process GPS-related data only if track points are present
            if (parsedData.getTrackPoints().isEmpty()) {
                log.info("No GPS track points found in FIT file - likely an indoor activity");
                // Default to UTC timezone for indoor activities
                parsedData.setTimezone("UTC");
            } else {
                // Determine timezone from first GPS coordinate
                determineTimezone(parsedData);

                // Apply speed smoothing and recalculate max speed
                smoothSpeedData(parsedData);
            }

            log.info("Successfully parsed FIT file: {} track points, activity type: {}, timezone: {}",
                parsedData.getTrackPoints().size(), parsedData.getActivityType(), parsedData.getTimezone());

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
    private void extractSessionData(SessionMesg session, ParsedActivityData parsedData) {
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
        } else {
            // Fallback: Calculate moving time from track points if native value is not available
            Duration calculatedMovingTime = calculateMovingTimeFromTrackPoints(parsedData);
            if (calculatedMovingTime != null) {
                metrics.setMovingTime(calculatedMovingTime);
                log.debug("Calculated moving time from track points: {}", calculatedMovingTime);
            }
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
    private void extractActivityData(ActivityMesg activity, ParsedActivityData parsedData) {
        if (activity.getTimestamp() != null) {
            parsedData.setActivityTimestamp(convertDateTime(activity.getTimestamp()));
        }

        if (activity.getTotalTimerTime() != null) {
            log.debug("Activity total timer time: {}", activity.getTotalTimerTime());
        }
    }

    /**
     * Applies speed smoothing to track points and updates max speed in metrics.
     * Removes unrealistic GPS speed spikes and recalculates max speed.
     */
    private void smoothSpeedData(ParsedActivityData parsedData) {
        if (parsedData.getTrackPoints().isEmpty() || parsedData.getMetrics() == null) {
            return;
        }

        // Smooth speed data and get recalculated max speed
        BigDecimal smoothedMaxSpeed = speedSmoother.smoothAndCalculateMaxSpeed(
            parsedData.getTrackPoints(),
            parsedData.getActivityType()
        );

        // Update max speed in metrics if we got a valid smoothed value
        if (smoothedMaxSpeed != null) {
            BigDecimal originalMaxSpeed = parsedData.getMetrics().getMaxSpeed();
            parsedData.getMetrics().setMaxSpeed(smoothedMaxSpeed);

            if (originalMaxSpeed != null && smoothedMaxSpeed.compareTo(originalMaxSpeed) < 0) {
                log.info("Smoothed max speed from {} km/h to {} km/h (removed GPS artifacts)",
                    originalMaxSpeed, smoothedMaxSpeed);
            }
        }
    }

    /**
     * Determines the timezone based on the first GPS coordinate.
     * Uses TimeZoneEngine library for accurate timezone lookup from coordinates.
     */
    private void determineTimezone(ParsedActivityData parsedData) {
        if (parsedData.getTrackPoints().isEmpty()) {
            parsedData.setTimezone("UTC");
            return;
        }

        TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
        double latitude = firstPoint.getLatitude();
        double longitude = firstPoint.getLongitude();

        try {
            // Lazy-load timezone engine (expensive initialization ~200ms first time)
            if (timezoneEngine == null) {
                log.info("Initializing TimeZoneEngine for timezone lookup...");
                timezoneEngine = TimeZoneEngine.initialize();
            }

            Optional<ZoneId> zoneId = timezoneEngine.query(latitude, longitude);
            if (zoneId.isPresent()) {
                parsedData.setTimezone(zoneId.get().getId());
                log.debug("Determined timezone: {} from coordinates ({}, {})",
                    zoneId.get().getId(), latitude, longitude);
            } else {
                log.warn("Could not determine timezone for coordinates ({}, {}), defaulting to UTC",
                    latitude, longitude);
                parsedData.setTimezone("UTC");
            }
        } catch (Exception e) {
            log.error("Error determining timezone, defaulting to UTC", e);
            parsedData.setTimezone("UTC");
        }
    }

    /**
     * Converts FIT DateTime to LocalDateTime.
     * FIT timestamps use a special epoch: December 31, 1989, 00:00:00 UTC.
     * We need to add the offset from Unix epoch (1970) to FIT epoch (1989).
     */
    private LocalDateTime convertDateTime(DateTime dateTime) {
        // FIT epoch offset: seconds between 1970-01-01 and 1989-12-31
        final long FIT_EPOCH_OFFSET = 631065600L;

        long timestamp = dateTime.getTimestamp();
        // Add FIT epoch offset to convert to Unix timestamp
        Instant instant = Instant.ofEpochSecond(timestamp + FIT_EPOCH_OFFSET);
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
     * Calculates moving time from track points when native moving time is not available.
     * Uses same logic as GPX parser: speed < 0.5 km/h for > 30 seconds = stopped.
     */
    private Duration calculateMovingTimeFromTrackPoints(ParsedActivityData parsedData) {
        List<TrackPointData> trackPoints = parsedData.getTrackPoints();

        // For indoor activities or activities without track points, use total duration
        if (trackPoints == null || trackPoints.isEmpty()) {
            Duration totalDuration = parsedData.getTotalDuration();
            if (totalDuration != null) {
                log.debug("No track points available, using total duration as moving time: {}", totalDuration);
                return totalDuration;
            }
            return null;
        }

        // Need at least 2 points to calculate moving time
        if (trackPoints.size() < 2) {
            Duration totalDuration = parsedData.getTotalDuration();
            log.debug("Only 1 track point, using total duration as moving time: {}", totalDuration);
            return totalDuration;
        }

        Duration movingTime = Duration.ZERO;
        Duration stoppedTime = Duration.ZERO;
        LocalDateTime lastStoppedTime = null;

        for (int i = 1; i < trackPoints.size(); i++) {
            TrackPointData prev = trackPoints.get(i - 1);
            TrackPointData curr = trackPoints.get(i);

            if (prev.getTimestamp() == null || curr.getTimestamp() == null) {
                continue;
            }

            Duration timeDelta = Duration.between(prev.getTimestamp(), curr.getTimestamp());

            // Skip unrealistic time deltas (> 1 hour between points)
            if (timeDelta.getSeconds() > 3600) {
                continue;
            }

            // Check if we have speed data
            BigDecimal speed = curr.getSpeed();
            if (speed != null) {
                double speedKmh = speed.doubleValue(); // Already in km/h from FIT parser

                // Track moving vs stopped time
                if (speedKmh < STOPPED_SPEED_THRESHOLD) {
                    if (lastStoppedTime == null) {
                        lastStoppedTime = prev.getTimestamp();
                    }
                    Duration currentStopDuration = Duration.between(lastStoppedTime, curr.getTimestamp());
                    if (currentStopDuration.getSeconds() > STOPPED_TIME_THRESHOLD) {
                        stoppedTime = stoppedTime.plus(timeDelta);
                    }
                } else {
                    lastStoppedTime = null;
                    movingTime = movingTime.plus(timeDelta);
                }
            } else {
                // No speed data, assume moving
                movingTime = movingTime.plus(timeDelta);
            }
        }

        // If we didn't calculate any moving time, use total duration
        if (movingTime.isZero() && stoppedTime.isZero()) {
            Duration totalDuration = parsedData.getTotalDuration();
            log.debug("No speed data in track points, using total duration as moving time: {}", totalDuration);
            return totalDuration;
        }

        log.debug("Calculated moving time from track points: moving={}, stopped={}", movingTime, stoppedTime);
        return movingTime;
    }
}
