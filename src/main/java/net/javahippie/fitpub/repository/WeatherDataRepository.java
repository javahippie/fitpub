package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.WeatherData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing weather data.
 */
@Repository
public interface WeatherDataRepository extends JpaRepository<WeatherData, UUID> {

    /**
     * Find weather data for a specific activity.
     *
     * @param activityId the activity ID
     * @return optional weather data
     */
    Optional<WeatherData> findByActivityId(UUID activityId);

    /**
     * Check if weather data exists for an activity.
     *
     * @param activityId the activity ID
     * @return true if weather data exists
     */
    boolean existsByActivityId(UUID activityId);

    /**
     * Delete weather data for an activity.
     *
     * @param activityId the activity ID
     */
    void deleteByActivityId(UUID activityId);
}
