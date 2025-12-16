package org.operaton.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for smoothing speed data from GPS tracks.
 * Removes unrealistic spikes caused by GPS inaccuracies.
 */
@Component
@Slf4j
public class SpeedSmoother {

    // Maximum realistic speeds in m/s by activity type
    private static final double MAX_RUNNING_SPEED_MPS = 15.0;  // ~54 km/h (elite sprinter)
    private static final double MAX_CYCLING_SPEED_MPS = 30.0;  // ~108 km/h (downhill/sprint)
    private static final double MAX_WALKING_SPEED_MPS = 3.0;   // ~11 km/h
    private static final double MAX_HIKING_SPEED_MPS = 4.0;    // ~14 km/h
    private static final double MAX_SWIMMING_SPEED_MPS = 3.0;  // ~11 km/h
    private static final double MAX_DEFAULT_SPEED_MPS = 20.0;  // ~72 km/h

    // Maximum realistic acceleration in m/s² (human physical limits)
    private static final double MAX_ACCELERATION_RUNNING = 4.0;
    private static final double MAX_ACCELERATION_CYCLING = 5.0;
    private static final double MAX_ACCELERATION_DEFAULT = 6.0;

    // Moving median window size for smoothing
    private static final int MEDIAN_WINDOW_SIZE = 5;

    /**
     * Smooths speed data for track points and recalculates max speed.
     *
     * @param trackPoints the track points with speed data
     * @param activityType the activity type
     * @return smoothed maximum speed in km/h, or null if no valid speeds
     */
    public BigDecimal smoothAndCalculateMaxSpeed(
        List<FitParser.TrackPointData> trackPoints,
        Activity.ActivityType activityType
    ) {
        if (trackPoints == null || trackPoints.isEmpty()) {
            return null;
        }

        double maxSpeedMps = getMaxSpeedThreshold(activityType);
        double maxAcceleration = getMaxAccelerationThreshold(activityType);

        // Step 1: Apply speed threshold to remove obvious outliers
        applySpeedThreshold(trackPoints, maxSpeedMps);

        // Step 2: Apply acceleration-based filtering
        applyAccelerationFilter(trackPoints, maxAcceleration);

        // Step 3: Apply moving median filter for smoothing
        applyMedianFilter(trackPoints);

        // Step 4: Calculate max speed from smoothed data
        BigDecimal maxSpeed = calculateMaxSpeed(trackPoints);

        if (maxSpeed != null) {
            log.debug("Smoothed max speed: {} km/h for activity type: {}",
                maxSpeed, activityType);
        }

        return maxSpeed;
    }

    /**
     * Gets the maximum realistic speed threshold for an activity type.
     */
    private double getMaxSpeedThreshold(Activity.ActivityType activityType) {
        if (activityType == null) {
            return MAX_DEFAULT_SPEED_MPS;
        }

        return switch (activityType) {
            case RUN -> MAX_RUNNING_SPEED_MPS;
            case RIDE -> MAX_CYCLING_SPEED_MPS;
            case WALK -> MAX_WALKING_SPEED_MPS;
            case HIKE -> MAX_HIKING_SPEED_MPS;
            case SWIM -> MAX_SWIMMING_SPEED_MPS;
            default -> MAX_DEFAULT_SPEED_MPS;
        };
    }

    /**
     * Gets the maximum realistic acceleration for an activity type.
     */
    private double getMaxAccelerationThreshold(Activity.ActivityType activityType) {
        if (activityType == null) {
            return MAX_ACCELERATION_DEFAULT;
        }

        return switch (activityType) {
            case RUN -> MAX_ACCELERATION_RUNNING;
            case RIDE -> MAX_ACCELERATION_CYCLING;
            default -> MAX_ACCELERATION_DEFAULT;
        };
    }

    /**
     * Applies speed threshold to remove obvious outliers.
     * Replaces speeds exceeding threshold with null.
     */
    private void applySpeedThreshold(List<FitParser.TrackPointData> trackPoints, double maxSpeedMps) {
        int outlierCount = 0;

        for (FitParser.TrackPointData point : trackPoints) {
            if (point.getSpeed() != null) {
                // Convert km/h to m/s for comparison
                double speedMps = point.getSpeed().doubleValue() / 3.6;

                if (speedMps > maxSpeedMps) {
                    point.setSpeed(null);
                    outlierCount++;
                }
            }
        }

        if (outlierCount > 0) {
            log.debug("Removed {} speed outliers exceeding threshold {} m/s",
                outlierCount, maxSpeedMps);
        }
    }

    /**
     * Applies acceleration-based filtering.
     * Removes points with unrealistic acceleration changes.
     */
    private void applyAccelerationFilter(
        List<FitParser.TrackPointData> trackPoints,
        double maxAcceleration
    ) {
        int outlierCount = 0;

        for (int i = 1; i < trackPoints.size(); i++) {
            FitParser.TrackPointData prev = trackPoints.get(i - 1);
            FitParser.TrackPointData curr = trackPoints.get(i);

            if (prev.getSpeed() == null || curr.getSpeed() == null ||
                prev.getTimestamp() == null || curr.getTimestamp() == null) {
                continue;
            }

            // Calculate time delta in seconds
            Duration timeDelta = Duration.between(prev.getTimestamp(), curr.getTimestamp());
            double seconds = timeDelta.getSeconds() + timeDelta.getNano() / 1_000_000_000.0;

            if (seconds <= 0) {
                continue;
            }

            // Calculate acceleration (m/s²)
            double speedPrevMps = prev.getSpeed().doubleValue() / 3.6;
            double speedCurrMps = curr.getSpeed().doubleValue() / 3.6;
            double acceleration = Math.abs(speedCurrMps - speedPrevMps) / seconds;

            if (acceleration > maxAcceleration) {
                // Mark current point's speed as invalid
                curr.setSpeed(null);
                outlierCount++;
            }
        }

        if (outlierCount > 0) {
            log.debug("Removed {} speed values with unrealistic acceleration (> {} m/s²)",
                outlierCount, maxAcceleration);
        }
    }

    /**
     * Applies moving median filter to smooth speed data.
     * Fills in nulls with interpolated values where possible.
     */
    private void applyMedianFilter(List<FitParser.TrackPointData> trackPoints) {
        int windowSize = Math.min(MEDIAN_WINDOW_SIZE, trackPoints.size());
        if (windowSize < 3) {
            return; // Not enough points for meaningful smoothing
        }

        List<BigDecimal> smoothedSpeeds = new ArrayList<>(trackPoints.size());

        for (int i = 0; i < trackPoints.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(trackPoints.size(), i + windowSize / 2 + 1);

            // Collect valid speeds in window
            List<Double> windowSpeeds = new ArrayList<>();
            for (int j = start; j < end; j++) {
                if (trackPoints.get(j).getSpeed() != null) {
                    windowSpeeds.add(trackPoints.get(j).getSpeed().doubleValue());
                }
            }

            if (!windowSpeeds.isEmpty()) {
                // Calculate median
                Collections.sort(windowSpeeds);
                double median;
                int size = windowSpeeds.size();
                if (size % 2 == 0) {
                    median = (windowSpeeds.get(size / 2 - 1) + windowSpeeds.get(size / 2)) / 2.0;
                } else {
                    median = windowSpeeds.get(size / 2);
                }

                smoothedSpeeds.add(BigDecimal.valueOf(median).setScale(2, RoundingMode.HALF_UP));
            } else {
                smoothedSpeeds.add(null);
            }
        }

        // Apply smoothed speeds back to track points
        for (int i = 0; i < trackPoints.size(); i++) {
            if (smoothedSpeeds.get(i) != null) {
                trackPoints.get(i).setSpeed(smoothedSpeeds.get(i));
            }
        }

        log.debug("Applied moving median filter with window size {}", windowSize);
    }

    /**
     * Calculates maximum speed from smoothed track points.
     */
    private BigDecimal calculateMaxSpeed(List<FitParser.TrackPointData> trackPoints) {
        return trackPoints.stream()
            .map(FitParser.TrackPointData::getSpeed)
            .filter(speed -> speed != null)
            .max(BigDecimal::compareTo)
            .orElse(null);
    }
}
