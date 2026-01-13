package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.ActivityMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ActivityMetrics entities.
 */
@Repository
public interface ActivityMetricsRepository extends JpaRepository<ActivityMetrics, UUID> {

    /**
     * Find metrics for a specific activity.
     *
     * @param activityId the activity ID
     * @return optional metrics
     */
    @Query("SELECT am FROM ActivityMetrics am WHERE am.activity.id = :activityId")
    Optional<ActivityMetrics> findByActivityId(@Param("activityId") UUID activityId);

    /**
     * Delete metrics for a specific activity.
     *
     * @param activityId the activity ID
     */
    @Query("DELETE FROM ActivityMetrics am WHERE am.activity.id = :activityId")
    void deleteByActivityId(@Param("activityId") UUID activityId);
}
