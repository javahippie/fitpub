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

    /**
     * Update heatmap grid for a single activity using native PostgreSQL query.
     * This method extracts coordinates from track_points_json, snaps them to grid using PostGIS,
     * and upserts grid cells in a single database operation.
     *
     * Performance: ~20-40x faster than Java processing (200+ queries → 1 query)
     *
     * @param activityId the activity ID
     */
    @Modifying
    @Query(value = """
        WITH extracted_points AS (
            SELECT
                a.user_id,
                CAST((point->>'latitude') AS double precision) AS lat,
                CAST((point->>'longitude') AS double precision) AS lon
            FROM activities a
            CROSS JOIN LATERAL jsonb_array_elements(CAST(a.track_points_json AS jsonb)) AS point
            WHERE a.id = :activityId
              AND a.track_points_json IS NOT NULL
              AND a.indoor = FALSE
        ),
        snapped_grid AS (
            SELECT
                user_id,
                ST_SnapToGrid(
                    ST_SetSRID(ST_MakePoint(lon, lat), 4326),
                    0.0001  -- GRID_SIZE: 0.0001 degrees ≈ 11 meters
                ) AS grid_cell,
                CAST(COUNT(*) AS integer) AS point_count
            FROM extracted_points
            WHERE lat IS NOT NULL AND lon IS NOT NULL
            GROUP BY user_id, ST_SnapToGrid(ST_SetSRID(ST_MakePoint(lon, lat), 4326), 0.0001)
        )
        INSERT INTO user_heatmap_grid (user_id, grid_cell, point_count, last_updated)
        SELECT user_id, grid_cell, point_count, CURRENT_TIMESTAMP
        FROM snapped_grid
        ON CONFLICT (user_id, grid_cell)
        DO UPDATE SET
            point_count = user_heatmap_grid.point_count + EXCLUDED.point_count,
            last_updated = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void updateHeatmapForActivityNative(@Param("activityId") UUID activityId);

    /**
     * Recalculate entire heatmap for a user using native PostgreSQL query.
     * This method processes ALL user activities in a single query, replacing existing grid cells.
     *
     * Performance: ~10-20x faster than Java processing for users with many activities
     *
     * @param userId the user ID
     */
    @Modifying
    @Query(value = """
        WITH all_points AS (
            SELECT
                a.user_id,
                CAST((point->>'latitude') AS double precision) AS lat,
                CAST((point->>'longitude') AS double precision) AS lon
            FROM activities a
            CROSS JOIN LATERAL jsonb_array_elements(CAST(a.track_points_json AS jsonb)) AS point
            WHERE a.user_id = :userId
              AND a.track_points_json IS NOT NULL
              AND a.indoor = FALSE
        ),
        snapped_grid AS (
            SELECT
                user_id,
                ST_SnapToGrid(
                    ST_SetSRID(ST_MakePoint(lon, lat), 4326),
                    0.0001  -- GRID_SIZE: 0.0001 degrees ≈ 11 meters
                ) AS grid_cell,
                CAST(COUNT(*) AS integer) AS point_count
            FROM all_points
            WHERE lat IS NOT NULL AND lon IS NOT NULL
            GROUP BY user_id, ST_SnapToGrid(ST_SetSRID(ST_MakePoint(lon, lat), 4326), 0.0001)
        )
        INSERT INTO user_heatmap_grid (user_id, grid_cell, point_count, last_updated)
        SELECT user_id, grid_cell, point_count, CURRENT_TIMESTAMP
        FROM snapped_grid
        ON CONFLICT (user_id, grid_cell)
        DO UPDATE SET
            point_count = EXCLUDED.point_count,  -- Replace instead of increment for full recalculation
            last_updated = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void recalculateUserHeatmapNative(@Param("userId") UUID userId);

    /**
     * Find grid cells for a user aggregated to a coarser grid size.
     * Re-snaps existing fine grid cells to a coarser grid using PostGIS ST_SnapToGrid.
     *
     * This allows zoom-adaptive heatmap rendering:
     * - At low zoom (world view), aggregate to 0.01° grid (~1.1 km)
     * - At medium zoom (city view), aggregate to 0.001° grid (~111 m)
     * - At high zoom (street view), use 0.0001° grid (~11 m)
     *
     * Performance: Much faster than transferring all fine grid cells and aggregating client-side.
     *
     * @param userId the user ID
     * @param gridSize grid size in degrees (e.g., 0.01, 0.001, 0.0001)
     * @return list of aggregated grid cells
     */
    @Query(value = """
        WITH snapped AS (
            SELECT
                id,
                user_id,
                ST_SnapToGrid(grid_cell, :gridSize) AS snapped_cell,
                point_count,
                last_updated
            FROM user_heatmap_grid
            WHERE user_id = :userId
        )
        SELECT
            MIN(id) AS id,
            user_id,
            snapped_cell AS grid_cell,
            CAST(SUM(point_count) AS integer) AS point_count,
            MAX(last_updated) AS last_updated
        FROM snapped
        GROUP BY user_id, snapped_cell
        ORDER BY SUM(point_count) DESC
        LIMIT 10000
        """, nativeQuery = true)
    List<UserHeatmapGrid> findByUserIdAggregated(
        @Param("userId") UUID userId,
        @Param("gridSize") double gridSize
    );

    /**
     * Find grid cells for a user within a bounding box, aggregated to a coarser grid size.
     * Combines spatial filtering with zoom-adaptive aggregation.
     *
     * @param userId the user ID
     * @param minLon minimum longitude
     * @param minLat minimum latitude
     * @param maxLon maximum longitude
     * @param maxLat maximum latitude
     * @param gridSize grid size in degrees (e.g., 0.01, 0.001, 0.0001)
     * @return list of aggregated grid cells within bounding box
     */
    @Query(value = """
        WITH snapped AS (
            SELECT
                id,
                user_id,
                ST_SnapToGrid(grid_cell, :gridSize) AS snapped_cell,
                point_count,
                last_updated
            FROM user_heatmap_grid
            WHERE user_id = :userId
              AND ST_Intersects(grid_cell, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
        )
        SELECT
            MIN(id) AS id,
            user_id,
            snapped_cell AS grid_cell,
            CAST(SUM(point_count) AS integer) AS point_count,
            MAX(last_updated) AS last_updated
        FROM snapped
        GROUP BY user_id, snapped_cell
        ORDER BY SUM(point_count) DESC
        LIMIT 10000
        """, nativeQuery = true)
    List<UserHeatmapGrid> findByUserIdWithinBoundingBoxAggregated(
        @Param("userId") UUID userId,
        @Param("minLon") double minLon,
        @Param("minLat") double minLat,
        @Param("maxLon") double maxLon,
        @Param("maxLat") double maxLat,
        @Param("gridSize") double gridSize
    );
}
