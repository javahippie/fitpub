package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.PrivacyZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for PrivacyZone entities.
 */
@Repository
public interface PrivacyZoneRepository extends JpaRepository<PrivacyZone, UUID> {

    /**
     * Find all active privacy zones for a user.
     *
     * @param userId the user ID
     * @return list of active privacy zones
     */
    List<PrivacyZone> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Find all privacy zones for a user (including inactive).
     *
     * @param userId the user ID
     * @return list of all privacy zones
     */
    List<PrivacyZone> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Check if a point falls within any of the user's active privacy zones.
     * Uses PostGIS ST_DWithin for efficient radius-based spatial queries.
     *
     * @param userId the user ID
     * @param latitude the point latitude
     * @param longitude the point longitude
     * @return true if point is within any privacy zone
     */
    @Query(value = "SELECT EXISTS(" +
                   "SELECT 1 FROM privacy_zones " +
                   "WHERE user_id = :userId " +
                   "AND is_active = TRUE " +
                   "AND ST_DWithin(" +
                   "    center_point::geography, " +
                   "    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, " +
                   "    radius_meters" +
                   ")" +
                   ")", nativeQuery = true)
    boolean isPointInAnyZone(
        @Param("userId") UUID userId,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude
    );

    /**
     * Find all privacy zones that a point falls within.
     * Returns the actual zones (not just boolean) for detailed filtering.
     *
     * @param userId the user ID
     * @param latitude the point latitude
     * @param longitude the point longitude
     * @return list of privacy zones containing this point
     */
    @Query(value = "SELECT * FROM privacy_zones " +
                   "WHERE user_id = :userId " +
                   "AND is_active = TRUE " +
                   "AND ST_DWithin(" +
                   "    center_point::geography, " +
                   "    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, " +
                   "    radius_meters" +
                   ")", nativeQuery = true)
    List<PrivacyZone> findZonesContainingPoint(
        @Param("userId") UUID userId,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude
    );

    /**
     * Count active privacy zones for a user.
     *
     * @param userId the user ID
     * @return count of active zones
     */
    long countByUserIdAndIsActiveTrue(UUID userId);
}
