package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.model.entity.UserHeatmapGrid;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.UserHeatmapGridRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing user activity heatmap grids.
 * Aggregates GPS track points into spatial grid cells for efficient heatmap rendering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeatmapGridService {

    private final UserHeatmapGridRepository heatmapGridRepository;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    /**
     * Grid resolution in degrees (~100m at equator).
     * 0.001 degrees = ~111 meters
     */
    private static final double GRID_SIZE = 0.001;

    /**
     * SRID for WGS84 coordinate system.
     */
    private static final int SRID = 4326;

    /**
     * Geometry factory for creating PostGIS points.
     */
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    /**
     * Sampling rate for large activities.
     * Process every Nth point to avoid overwhelming the grid.
     */
    private static final int SAMPLING_RATE = 10;

    /**
     * Update heatmap grid for a single activity.
     * Called when a new activity is uploaded.
     *
     * @param activity the activity to process
     */
    @Transactional
    public void updateHeatmapForActivity(Activity activity) {
        log.info("Updating heatmap grid for activity {} (user {})", activity.getId(), activity.getUserId());

        List<Point> gridCells = extractGridCellsFromActivity(activity);
        if (gridCells.isEmpty()) {
            log.warn("No grid cells extracted from activity {}", activity.getId());
            return;
        }

        // Count frequency of each grid cell
        Map<String, Integer> cellCounts = new HashMap<>();
        for (Point cell : gridCells) {
            String key = cellKey(cell);
            cellCounts.put(key, cellCounts.getOrDefault(key, 0) + 1);
        }

        // Upsert grid cells
        for (Map.Entry<String, Integer> entry : cellCounts.entrySet()) {
            Point cell = parseCell(entry.getKey());
            int count = entry.getValue();
            upsertGridCell(activity.getUserId(), cell, count);
        }

        log.info("Updated {} unique grid cells for activity {}", cellCounts.size(), activity.getId());
    }

    /**
     * Recalculate entire heatmap for a user.
     * Called by scheduled job or when user requests full recalculation.
     *
     * @param user the user to recalculate
     */
    @Transactional
    public void recalculateUserHeatmap(User user) {
        log.info("Recalculating heatmap for user {}", user.getUsername());

        // Delete existing grid
        heatmapGridRepository.deleteByUserId(user.getId());

        // Get all activities for user
        List<Activity> activities = activityRepository.findByUserIdOrderByStartedAtDesc(user.getId());
        if (activities.isEmpty()) {
            log.info("No activities found for user {}", user.getUsername());
            return;
        }

        // Aggregate all grid cells across all activities
        Map<String, Integer> allCellCounts = new HashMap<>();

        for (Activity activity : activities) {
            List<Point> gridCells = extractGridCellsFromActivity(activity);
            for (Point cell : gridCells) {
                String key = cellKey(cell);
                allCellCounts.put(key, allCellCounts.getOrDefault(key, 0) + 1);
            }
        }

        // Bulk insert grid cells
        List<UserHeatmapGrid> gridEntities = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : allCellCounts.entrySet()) {
            Point cell = parseCell(entry.getKey());
            UserHeatmapGrid grid = UserHeatmapGrid.builder()
                    .userId(user.getId())
                    .gridCell(cell)
                    .pointCount(entry.getValue())
                    .build();
            gridEntities.add(grid);
        }

        heatmapGridRepository.saveAll(gridEntities);
        log.info("Recalculated {} grid cells for user {} from {} activities",
                gridEntities.size(), user.getUsername(), activities.size());
    }

    /**
     * Get heatmap data for a user, optionally filtered by bounding box.
     *
     * @param userId the user ID
     * @param minLon minimum longitude (optional)
     * @param minLat minimum latitude (optional)
     * @param maxLon maximum longitude (optional)
     * @param maxLat maximum latitude (optional)
     * @return list of grid cells with intensities
     */
    @Transactional(readOnly = true)
    public List<UserHeatmapGrid> getUserHeatmapData(UUID userId, Double minLon, Double minLat, Double maxLon, Double maxLat) {
        if (minLon != null && minLat != null && maxLon != null && maxLat != null) {
            log.debug("Fetching heatmap for user {} with bounding box", userId);
            return heatmapGridRepository.findByUserIdWithinBoundingBox(userId, minLon, minLat, maxLon, maxLat);
        } else {
            log.debug("Fetching full heatmap for user {}", userId);
            return heatmapGridRepository.findByUserId(userId);
        }
    }

    /**
     * Get maximum point count for a user (for normalization).
     *
     * @param userId the user ID
     * @return maximum point count
     */
    @Transactional(readOnly = true)
    public Integer getMaxPointCount(UUID userId) {
        Integer max = heatmapGridRepository.findMaxPointCountByUserId(userId);
        return max != null ? max : 1; // Avoid division by zero
    }

    /**
     * Extract grid cells from an activity's track points.
     *
     * @param activity the activity
     * @return list of grid cell points
     */
    private List<Point> extractGridCellsFromActivity(Activity activity) {
        String trackPointsJson = activity.getTrackPointsJson();
        if (trackPointsJson == null || trackPointsJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(trackPointsJson);
            if (!root.isArray()) {
                log.warn("Track points JSON is not an array for activity {}", activity.getId());
                return Collections.emptyList();
            }

            List<Point> gridCells = new ArrayList<>();
            int pointIndex = 0;

            for (JsonNode pointNode : root) {
                // Sample every Nth point for large activities
                if (pointIndex % SAMPLING_RATE != 0) {
                    pointIndex++;
                    continue;
                }

                JsonNode latNode = pointNode.get("latitude");
                JsonNode lonNode = pointNode.get("longitude");

                if (latNode != null && lonNode != null) {
                    double lat = latNode.asDouble();
                    double lon = lonNode.asDouble();
                    Point gridCell = snapToGrid(lat, lon);
                    gridCells.add(gridCell);
                }

                pointIndex++;
            }

            return gridCells;
        } catch (Exception e) {
            log.error("Failed to parse track points for activity {}", activity.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Snap a coordinate to the nearest grid cell center.
     *
     * @param lat latitude
     * @param lon longitude
     * @return grid cell point
     */
    private Point snapToGrid(double lat, double lon) {
        double gridLat = Math.floor(lat / GRID_SIZE) * GRID_SIZE + (GRID_SIZE / 2);
        double gridLon = Math.floor(lon / GRID_SIZE) * GRID_SIZE + (GRID_SIZE / 2);
        return geometryFactory.createPoint(new Coordinate(gridLon, gridLat));
    }

    /**
     * Upsert a grid cell (insert or increment count).
     *
     * @param userId the user ID
     * @param gridCell the grid cell point
     * @param increment the count to add
     */
    private void upsertGridCell(UUID userId, Point gridCell, int increment) {
        Optional<UserHeatmapGrid> existing = heatmapGridRepository.findByUserIdAndGridCell(userId, gridCell);
        if (existing.isPresent()) {
            UserHeatmapGrid grid = existing.get();
            grid.setPointCount(grid.getPointCount() + increment);
            heatmapGridRepository.save(grid);
        } else {
            UserHeatmapGrid grid = UserHeatmapGrid.builder()
                    .userId(userId)
                    .gridCell(gridCell)
                    .pointCount(increment)
                    .build();
            heatmapGridRepository.save(grid);
        }
    }

    /**
     * Generate a unique key for a grid cell.
     *
     * @param cell the grid cell point
     * @return cell key string
     */
    private String cellKey(Point cell) {
        return String.format("%.6f,%.6f", cell.getY(), cell.getX());
    }

    /**
     * Parse a cell from a key string.
     *
     * @param key the cell key
     * @return grid cell point
     */
    private Point parseCell(String key) {
        String[] parts = key.split(",");
        double lat = Double.parseDouble(parts[0]);
        double lon = Double.parseDouble(parts[1]);
        return geometryFactory.createPoint(new Coordinate(lon, lat));
    }
}
