package net.javahippie.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.PersonalRecord;
import net.javahippie.fitpub.repository.PersonalRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for tracking and managing personal records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;

    /**
     * Check and update personal records for an activity.
     * Called after an activity is saved or updated.
     *
     * @param activity the activity to check for PRs
     * @return list of new personal records set
     */
    @Transactional
    public List<PersonalRecord> checkAndUpdatePersonalRecords(Activity activity) {
        List<PersonalRecord> newRecords = new java.util.ArrayList<>();

        if (activity.getUserId() == null) {
            return newRecords;
        }

        PersonalRecord.ActivityType activityType = mapActivityType(activity.getActivityType().name());

        // Check longest distance
        if (activity.getTotalDistance() != null) {
            PersonalRecord distanceRecord = checkRecord(
                    activity.getUserId(),
                    activityType,
                    PersonalRecord.RecordType.LONGEST_DISTANCE,
                    activity.getTotalDistance(),
                    "meters",
                    activity.getId(),
                    activity.getStartedAt()
            );
            if (distanceRecord != null) {
                newRecords.add(distanceRecord);
            }

            // Check specific distance PRs (5K, 10K, etc.) for runs
            if (activityType == PersonalRecord.ActivityType.RUN) {
                newRecords.addAll(checkDistancePRs(activity));
            }
        }

        // Check longest duration
        if (activity.getTotalDurationSeconds() != null) {
            PersonalRecord durationRecord = checkRecord(
                    activity.getUserId(),
                    activityType,
                    PersonalRecord.RecordType.LONGEST_DURATION,
                    BigDecimal.valueOf(activity.getTotalDurationSeconds()),
                    "seconds",
                    activity.getId(),
                    activity.getStartedAt()
            );
            if (durationRecord != null) {
                newRecords.add(durationRecord);
            }
        }

        // Check highest elevation gain
        if (activity.getElevationGain() != null) {
            PersonalRecord elevationRecord = checkRecord(
                    activity.getUserId(),
                    activityType,
                    PersonalRecord.RecordType.HIGHEST_ELEVATION_GAIN,
                    activity.getElevationGain(),
                    "meters",
                    activity.getId(),
                    activity.getStartedAt()
            );
            if (elevationRecord != null) {
                newRecords.add(elevationRecord);
            }
        }

        // Check max speed (from metrics)
        if (activity.getMetrics() != null && activity.getMetrics().getMaxSpeed() != null) {
            PersonalRecord maxSpeedRecord = checkRecord(
                    activity.getUserId(),
                    activityType,
                    PersonalRecord.RecordType.MAX_SPEED,
                    activity.getMetrics().getMaxSpeed(),
                    "mps",
                    activity.getId(),
                    activity.getStartedAt()
            );
            if (maxSpeedRecord != null) {
                newRecords.add(maxSpeedRecord);
            }
        }

        // Check best average pace (for runs)
        if (activityType == PersonalRecord.ActivityType.RUN &&
            activity.getTotalDistance() != null &&
            activity.getTotalDurationSeconds() != null &&
            activity.getTotalDistance().compareTo(BigDecimal.valueOf(1000)) >= 0) { // At least 1km

            BigDecimal avgPace = BigDecimal.valueOf(activity.getTotalDurationSeconds())
                    .divide(activity.getTotalDistance().divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP), 2, RoundingMode.HALF_UP);

            // Lower pace is better (faster)
            PersonalRecord paceRecord = checkRecordLowerIsBetter(
                    activity.getUserId(),
                    activityType,
                    PersonalRecord.RecordType.BEST_AVERAGE_PACE,
                    avgPace,
                    "seconds_per_km",
                    activity.getId(),
                    activity.getStartedAt()
            );
            if (paceRecord != null) {
                newRecords.add(paceRecord);
            }
        }

        return newRecords;
    }

    /**
     * Check specific distance PRs (1K, 5K, 10K, half marathon, marathon).
     */
    private List<PersonalRecord> checkDistancePRs(Activity activity) {
        List<PersonalRecord> records = new java.util.ArrayList<>();

        if (activity.getTotalDistance() == null || activity.getTotalDurationSeconds() == null) {
            return records;
        }

        double distanceKm = activity.getTotalDistance().doubleValue() / 1000.0;
        long durationSeconds = activity.getTotalDurationSeconds();

        // Define target distances and their record types
        record DistanceTarget(double km, PersonalRecord.RecordType recordType) {}
        List<DistanceTarget> targets = List.of(
                new DistanceTarget(1.0, PersonalRecord.RecordType.FASTEST_1K),
                new DistanceTarget(5.0, PersonalRecord.RecordType.FASTEST_5K),
                new DistanceTarget(10.0, PersonalRecord.RecordType.FASTEST_10K),
                new DistanceTarget(21.1, PersonalRecord.RecordType.FASTEST_HALF_MARATHON),
                new DistanceTarget(42.2, PersonalRecord.RecordType.FASTEST_MARATHON)
        );

        for (DistanceTarget target : targets) {
            // Activity must be at least the target distance (within 5% tolerance)
            if (distanceKm >= target.km * 0.95) {
                // Calculate time for target distance (proportional)
                double timeForDistance = durationSeconds * (target.km / distanceKm);
                BigDecimal time = BigDecimal.valueOf(timeForDistance).setScale(2, RoundingMode.HALF_UP);

                // Lower time is better
                PersonalRecord record = checkRecordLowerIsBetter(
                        activity.getUserId(),
                        PersonalRecord.ActivityType.RUN,
                        target.recordType,
                        time,
                        "seconds",
                        activity.getId(),
                        activity.getStartedAt()
                );
                if (record != null) {
                    records.add(record);
                }
            }
        }

        return records;
    }

    /**
     * Check if a new record has been set (higher is better).
     */
    private PersonalRecord checkRecord(UUID userId, PersonalRecord.ActivityType activityType,
                                      PersonalRecord.RecordType recordType, BigDecimal value,
                                      String unit, UUID activityId, LocalDateTime achievedAt) {
        Optional<PersonalRecord> existingRecord = personalRecordRepository
                .findByUserIdAndActivityTypeAndRecordType(userId, activityType, recordType);

        if (existingRecord.isEmpty() || value.compareTo(existingRecord.get().getValue()) > 0) {
            PersonalRecord recordToSave;

            if (existingRecord.isPresent()) {
                // Update existing record
                PersonalRecord existing = existingRecord.get();
                recordToSave = existing;
                recordToSave.setPreviousValue(existing.getValue());
                recordToSave.setPreviousAchievedAt(existing.getAchievedAt());
                recordToSave.setValue(value);
                recordToSave.setActivityId(activityId);
                recordToSave.setAchievedAt(achievedAt);
            } else {
                // Create new record
                recordToSave = PersonalRecord.builder()
                        .userId(userId)
                        .activityType(activityType)
                        .recordType(recordType)
                        .value(value)
                        .unit(unit)
                        .activityId(activityId)
                        .achievedAt(achievedAt)
                        .build();
            }

            personalRecordRepository.save(recordToSave);
            log.info("Personal record set: {} {} - {} {}", activityType, recordType, value, unit);
            return recordToSave;
        }

        return null;
    }

    /**
     * Check if a new record has been set (lower is better, e.g., pace, time).
     */
    private PersonalRecord checkRecordLowerIsBetter(UUID userId, PersonalRecord.ActivityType activityType,
                                                   PersonalRecord.RecordType recordType, BigDecimal value,
                                                   String unit, UUID activityId, LocalDateTime achievedAt) {
        Optional<PersonalRecord> existingRecord = personalRecordRepository
                .findByUserIdAndActivityTypeAndRecordType(userId, activityType, recordType);

        if (existingRecord.isEmpty() || value.compareTo(existingRecord.get().getValue()) < 0) {
            PersonalRecord recordToSave;

            if (existingRecord.isPresent()) {
                // Update existing record
                PersonalRecord existing = existingRecord.get();
                recordToSave = existing;
                recordToSave.setPreviousValue(existing.getValue());
                recordToSave.setPreviousAchievedAt(existing.getAchievedAt());
                recordToSave.setValue(value);
                recordToSave.setActivityId(activityId);
                recordToSave.setAchievedAt(achievedAt);
            } else {
                // Create new record
                recordToSave = PersonalRecord.builder()
                        .userId(userId)
                        .activityType(activityType)
                        .recordType(recordType)
                        .value(value)
                        .unit(unit)
                        .activityId(activityId)
                        .achievedAt(achievedAt)
                        .build();
            }

            personalRecordRepository.save(recordToSave);
            log.info("Personal record set: {} {} - {} {}", activityType, recordType, value, unit);
            return recordToSave;
        }

        return null;
    }

    /**
     * Map Activity.ActivityType to PersonalRecord.ActivityType.
     */
    private PersonalRecord.ActivityType mapActivityType(String activityType) {
        if (activityType == null) {
            return PersonalRecord.ActivityType.OTHER;
        }

        return switch (activityType.toUpperCase()) {
            case "RUN", "RUNNING" -> PersonalRecord.ActivityType.RUN;
            case "RIDE", "CYCLING", "BIKE" -> PersonalRecord.ActivityType.RIDE;
            case "HIKE", "HIKING" -> PersonalRecord.ActivityType.HIKE;
            case "WALK", "WALKING" -> PersonalRecord.ActivityType.WALK;
            case "SWIM", "SWIMMING" -> PersonalRecord.ActivityType.SWIM;
            default -> PersonalRecord.ActivityType.OTHER;
        };
    }

    /**
     * Get all personal records for a user.
     */
    @Transactional(readOnly = true)
    public List<PersonalRecord> getPersonalRecords(UUID userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId);
    }

    /**
     * Get personal records for a user filtered by activity type.
     */
    @Transactional(readOnly = true)
    public List<PersonalRecord> getPersonalRecordsByType(UUID userId, PersonalRecord.ActivityType activityType) {
        return personalRecordRepository.findByUserIdAndActivityTypeOrderByAchievedAtDesc(userId, activityType);
    }

    /**
     * Get count of personal records for a user.
     */
    @Transactional(readOnly = true)
    public long getPersonalRecordCount(UUID userId) {
        return personalRecordRepository.countByUserId(userId);
    }
}
