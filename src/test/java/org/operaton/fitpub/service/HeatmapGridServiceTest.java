package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.model.entity.UserHeatmapGrid;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.UserHeatmapGridRepository;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HeatmapGridService.
 */
@ExtendWith(MockitoExtension.class)
class HeatmapGridServiceTest {

    @Mock
    private UserHeatmapGridRepository heatmapGridRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private HeatmapGridService heatmapGridService;
    private ObjectMapper objectMapper;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        heatmapGridService = new HeatmapGridService(
                heatmapGridRepository,
                activityRepository,
                objectMapper,
                entityManager,
                jdbcTemplate
        );
        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    @Test
    void testUpdateHeatmapForActivity_WithValidTrackPoints() throws Exception {
        // Create test activity with track points JSON
        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        Activity activity = Activity.builder()
                .id(activityId)
                .userId(userId)
                .activityType(Activity.ActivityType.RUN)
                .title("Test Run")
                .visibility(Activity.Visibility.PUBLIC)
                .build();

        // Create track points JSON (3 points in a small area)
        List<Map<String, Object>> trackPoints = new ArrayList<>();
        trackPoints.add(createTrackPoint(52.520008, 13.404954)); // Berlin
        trackPoints.add(createTrackPoint(52.520108, 13.405054)); // ~15m away
        trackPoints.add(createTrackPoint(52.520208, 13.405154)); // ~30m away

        String trackPointsJson = objectMapper.writeValueAsString(trackPoints);
        activity.setTrackPointsJson(trackPointsJson);

        // Execute - now uses native SQL query
        heatmapGridService.updateHeatmapForActivity(activity);

        // Verify that native query method was called
        verify(heatmapGridRepository).updateHeatmapForActivityNative(activityId);
    }

    @Test
    void testUpdateHeatmapForActivity_WithEmptyTrackPoints() {
        // Create activity with empty track points
        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .activityType(Activity.ActivityType.RUN)
                .title("Test Run")
                .trackPointsJson("[]")
                .build();

        // Execute
        heatmapGridService.updateHeatmapForActivity(activity);

        // Verify no grid cells were saved
        verify(heatmapGridRepository, never()).save(any(UserHeatmapGrid.class));
    }

    @Test
    void testUpdateHeatmapForActivity_WithNullTrackPoints() {
        // Create activity with null track points
        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .activityType(Activity.ActivityType.RUN)
                .title("Test Run")
                .trackPointsJson(null)
                .build();

        // Execute
        heatmapGridService.updateHeatmapForActivity(activity);

        // Verify no grid cells were saved
        verify(heatmapGridRepository, never()).save(any(UserHeatmapGrid.class));
    }

    @Test
    void testRecalculateUserHeatmap() throws Exception {
        // Create test user
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        // Mock JDBC template to simulate deleting existing grid cells
        when(jdbcTemplate.update(anyString(), any(UUID.class)))
                .thenReturn(10);  // Simulate deleting 10 rows

        // Execute - now uses native SQL query
        heatmapGridService.recalculateUserHeatmap(user);

        // Verify
        verify(jdbcTemplate).update(anyString(), eq(userId));  // Delete existing grid
        verify(entityManager).flush();  // Flush changes
        verify(entityManager).clear();  // Clear persistence context
        verify(heatmapGridRepository).recalculateUserHeatmapNative(userId);  // Native recalculation
    }

    @Test
    void testGetUserHeatmapData_WithBoundingBox() {
        UUID userId = UUID.randomUUID();
        List<UserHeatmapGrid> expectedGrids = new ArrayList<>();

        // Mock repository - using aggregated method with default grid size (0.0001)
        when(heatmapGridRepository.findByUserIdWithinBoundingBoxAggregated(
                userId, 13.0, 52.0, 14.0, 53.0, 0.0001))
                .thenReturn(expectedGrids);

        // Execute with null zoom (uses default grid size 0.0001)
        List<UserHeatmapGrid> result = heatmapGridService.getUserHeatmapData(
                userId, 13.0, 52.0, 14.0, 53.0, null);

        // Verify
        assertEquals(expectedGrids, result);
        verify(heatmapGridRepository).findByUserIdWithinBoundingBoxAggregated(
                userId, 13.0, 52.0, 14.0, 53.0, 0.0001);
    }

    @Test
    void testGetUserHeatmapData_WithoutBoundingBox() {
        UUID userId = UUID.randomUUID();
        List<UserHeatmapGrid> expectedGrids = new ArrayList<>();

        // Mock repository - using aggregated method with default grid size (0.0001)
        when(heatmapGridRepository.findByUserIdAggregated(userId, 0.0001))
                .thenReturn(expectedGrids);

        // Execute with null zoom (uses default grid size 0.0001)
        List<UserHeatmapGrid> result = heatmapGridService.getUserHeatmapData(
                userId, null, null, null, null, null);

        // Verify
        assertEquals(expectedGrids, result);
        verify(heatmapGridRepository).findByUserIdAggregated(userId, 0.0001);
    }

    @Test
    void testGetMaxPointCount() {
        UUID userId = UUID.randomUUID();
        Integer expectedMax = 150;

        // Mock repository
        when(heatmapGridRepository.findMaxPointCountByUserId(userId))
                .thenReturn(expectedMax);

        // Execute
        Integer result = heatmapGridService.getMaxPointCount(userId);

        // Verify
        assertEquals(expectedMax, result);
        verify(heatmapGridRepository).findMaxPointCountByUserId(userId);
    }

    @Test
    void testGetMaxPointCount_ReturnsOneWhenNull() {
        UUID userId = UUID.randomUUID();

        // Mock repository to return null
        when(heatmapGridRepository.findMaxPointCountByUserId(userId))
                .thenReturn(null);

        // Execute
        Integer result = heatmapGridService.getMaxPointCount(userId);

        // Verify it returns 1 to avoid division by zero
        assertEquals(1, result);
    }

    /**
     * Helper method to create a track point map.
     */
    private Map<String, Object> createTrackPoint(double latitude, double longitude) {
        Map<String, Object> point = new HashMap<>();
        point.put("latitude", latitude);
        point.put("longitude", longitude);
        point.put("elevation", 100.0);
        point.put("timestamp", "2025-01-01T12:00:00");
        return point;
    }
}
