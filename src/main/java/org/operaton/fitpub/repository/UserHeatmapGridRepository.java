package org.operaton.fitpub.repository;

import org.locationtech.jts.geom.Point;
import org.operaton.fitpub.model.entity.UserHeatmapGrid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserHeatmapGrid entities.
 */
@Repository
public interface UserHeatmapGridRepository extends JpaRepository<UserHeatmapGrid, Long> {

    /**
     * Find all grid cells for a user.
     *
     * @param userId the user ID
     * @return list of grid cells
     */
    List<UserHeatmapGrid> findByUserId(UUID userId);

    /**
     * Find grid cells for a user within a bounding box.
     * Uses PostGIS ST_MakeEnvelope to create a bounding box and ST_Intersects for spatial filtering.
     *
     * @param userId the user ID
     * @param minLon minimum longitude
     * @param minLat minimum latitude
     * @param maxLon maximum longitude
     * @param maxLat maximum latitude
     * @return list of grid cells within the bounding box
     */
    @Query(value = "SELECT * FROM user_heatmap_grid " +
                   "WHERE user_id = :userId " +
                   "AND ST_Intersects(grid_cell, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))",
           nativeQuery = true)
    List<UserHeatmapGrid> findByUserIdWithinBoundingBox(
        @Param("userId") UUID userId,
        @Param("minLon") double minLon,
        @Param("minLat") double minLat,
        @Param("maxLon") double maxLon,
        @Param("maxLat") double maxLat
    );

    /**
     * Find a grid cell for a user by exact coordinates.
     *
     * @param userId the user ID
     * @param gridCell the grid cell point
     * @return optional grid cell
     */
    Optional<UserHeatmapGrid> findByUserIdAndGridCell(UUID userId, Point gridCell);

    /**
     * Delete all grid cells for a user.
     * Used when recalculating the entire heatmap.
     * Uses native SQL to ensure PostGIS geometry types are handled correctly.
     *
     * @param userId the user ID
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_heatmap_grid WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") UUID userId);

    /**
     * Count grid cells for a user.
     *
     * @param userId the user ID
     * @return count of grid cells
     */
    long countByUserId(UUID userId);

    /**
     * Find maximum point count for a user.
     * Used for normalizing intensity values.
     *
     * @param userId the user ID
     * @return maximum point count
     */
    @Query("SELECT MAX(g.pointCount) FROM UserHeatmapGrid g WHERE g.userId = :userId")
    Integer findMaxPointCountByUserId(@Param("userId") UUID userId);
}
