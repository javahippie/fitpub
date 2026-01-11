package org.operaton.fitpub.service;

import com.garmin.fit.Decode;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.SessionMesg;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for retroactively detecting and updating indoor activity flags.
 * This is a data migration service to update existing activities in the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndoorActivityMigrationService {

    private final ActivityRepository activityRepository;

    /**
     * Retroactively update indoor flags for all existing FIT activities.
     * Re-parses stored FIT files to detect indoor activities based on SubSport field.
     *
     * @return number of activities updated
     */
    @Transactional
    public int updateIndoorFlagsForExistingActivities() {
        log.info("Starting retroactive indoor activity detection for all FIT activities");

        // Find all activities with FIT files
        List<Activity> fitActivities = activityRepository.findBySourceFileFormatAndRawActivityFileNotNull("FIT");
        log.info("Found {} FIT activities to analyze", fitActivities.size());

        AtomicInteger updatedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        fitActivities.forEach(activity -> {
            try {
                IndoorDetectionResult result = detectIndoorFromFitFile(activity.getRawActivityFile());

                boolean changed = false;

                if (result.isIndoor() != activity.getIndoor()) {
                    activity.setIndoor(result.isIndoor());
                    changed = true;
                }

                if (result.getSubSport() != null && !result.getSubSport().equals(activity.getSubSport())) {
                    activity.setSubSport(result.getSubSport());
                    changed = true;
                }

                if (result.getDetectionMethod() != null &&
                    !result.getDetectionMethod().name().equals(activity.getIndoorDetectionMethod())) {
                    activity.setIndoorDetectionMethod(result.getDetectionMethod().name());
                    changed = true;
                }

                if (changed) {
                    activityRepository.save(activity);
                    updatedCount.incrementAndGet();
                    log.info("Updated activity {} - indoor: {}, subSport: {}, method: {}",
                            activity.getId(), result.isIndoor(), result.getSubSport(),
                            result.getDetectionMethod());
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.warn("Failed to process activity {}: {}", activity.getId(), e.getMessage());
            }
        });

        log.info("Retroactive indoor detection complete: {} activities updated, {} errors",
                updatedCount.get(), errorCount.get());

        return updatedCount.get();
    }

    /**
     * Detect if a FIT file represents an indoor activity.
     * Checks the SubSport field from the session message.
     *
     * @param fitFileBytes raw FIT file bytes
     * @return detection result with indoor flag, SubSport, and detection method
     */
    private IndoorDetectionResult detectIndoorFromFitFile(byte[] fitFileBytes) {
        IndoorDetectionResult result = new IndoorDetectionResult();
        result.setIndoor(false);

        if (fitFileBytes == null || fitFileBytes.length == 0) {
            return result;
        }

        AtomicBoolean isIndoor = new AtomicBoolean(false);
        AtomicReference<String> subSport = new AtomicReference<>(null);
        AtomicReference<Activity.IndoorDetectionMethod> method = new AtomicReference<>(null);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fitFileBytes)) {
            Decode decode = new Decode();
            MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

            // Listen for session messages to extract SubSport
            broadcaster.addListener((SessionMesg session) -> {
                if (session.getSubSport() != null) {
                    String subSportStr = session.getSubSport().toString();
                    subSport.set(subSportStr);

                    String subSportUpper = subSportStr.toUpperCase();
                    boolean detected = subSportUpper.contains("INDOOR") ||
                                     subSportUpper.contains("TREADMILL") ||
                                     subSportUpper.contains("VIRTUAL") ||
                                     subSportUpper.contains("TRAINER");
                    if (detected) {
                        isIndoor.set(true);
                        method.set(Activity.IndoorDetectionMethod.FIT_SUBSPORT);
                        log.debug("Detected indoor activity from SubSport: {}", subSportStr);
                    }
                }
            });

            // Decode the FIT file
            if (!decode.checkFileIntegrity(inputStream)) {
                log.warn("FIT file integrity check failed");
                return result;
            }

            // Reset stream and read
            inputStream.reset();
            decode.read(inputStream, broadcaster);

            result.setIndoor(isIndoor.get());
            result.setSubSport(subSport.get());
            result.setDetectionMethod(method.get());

        } catch (Exception e) {
            log.warn("Failed to parse FIT file: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Result of indoor activity detection.
     */
    @Data
    private static class IndoorDetectionResult {
        private boolean indoor;
        private String subSport;
        private Activity.IndoorDetectionMethod detectionMethod;
    }
}
