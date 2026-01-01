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

    private HeatmapGridService heatmapGridService;
    private ObjectMapper objectMapper;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        heatmapGridService = new HeatmapGridService(
                heatmapGridRepository,
                activityRepository,
                objectMapper
        );
        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    @Test
    void testUpdateHeatmapForActivity_WithValidTrackPoints() throws Exception {
        // Create test activity with track points JSON
        UUID userId = UUID.randomUUID();
        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
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

        // Mock repository behavior
        when(heatmapGridRepository.findByUserIdAndGridCell(any(UUID.class), any(Point.class)))
                .thenReturn(Optional.empty());
        when(heatmapGridRepository.save(any(UserHeatmapGrid.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        heatmapGridService.updateHeatmapForActivity(activity);

        // Verify that grid cells were saved
        ArgumentCaptor<UserHeatmapGrid> gridCaptor = ArgumentCaptor.forClass(UserHeatmapGrid.class);
        verify(heatmapGridRepository, atLeastOnce()).save(gridCaptor.capture());

        List<UserHeatmapGrid> savedGrids = gridCaptor.getAllValues();
        assertFalse(savedGrids.isEmpty(), "Should save at least one grid cell");

        // Verify grid cell properties
        UserHeatmapGrid firstGrid = savedGrids.get(0);
        assertEquals(userId, firstGrid.getUserId());
        assertNotNull(firstGrid.getGridCell());
        assertTrue(firstGrid.getPointCount() > 0);
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
        // Create test user and activities
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        // Create activities with track points
        List<Activity> activities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Activity activity = Activity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .activityType(Activity.ActivityType.RUN)
                    .title("Test Run " + i)
                    .build();

            List<Map<String, Object>> trackPoints = new ArrayList<>();
            trackPoints.add(createTrackPoint(52.520008 + i * 0.01, 13.404954 + i * 0.01));
            activity.setTrackPointsJson(objectMapper.writeValueAsString(trackPoints));
            activities.add(activity);
        }

        // Mock repository behavior
        when(activityRepository.findByUserIdOrderByStartedAtDesc(userId))
                .thenReturn(activities);
        when(heatmapGridRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        heatmapGridService.recalculateUserHeatmap(user);

        // Verify
        verify(heatmapGridRepository).deleteByUserId(userId);
        verify(activityRepository).findByUserIdOrderByStartedAtDesc(userId);
        verify(heatmapGridRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    void testGetUserHeatmapData_WithBoundingBox() {
        UUID userId = UUID.randomUUID();
        List<UserHeatmapGrid> expectedGrids = new ArrayList<>();

        // Mock repository
        when(heatmapGridRepository.findByUserIdWithinBoundingBox(
                userId, 13.0, 52.0, 14.0, 53.0))
                .thenReturn(expectedGrids);

        // Execute
        List<UserHeatmapGrid> result = heatmapGridService.getUserHeatmapData(
                userId, 13.0, 52.0, 14.0, 53.0);

        // Verify
        assertEquals(expectedGrids, result);
        verify(heatmapGridRepository).findByUserIdWithinBoundingBox(
                userId, 13.0, 52.0, 14.0, 53.0);
    }

    @Test
    void testGetUserHeatmapData_WithoutBoundingBox() {
        UUID userId = UUID.randomUUID();
        List<UserHeatmapGrid> expectedGrids = new ArrayList<>();

        // Mock repository
        when(heatmapGridRepository.findByUserId(userId))
                .thenReturn(expectedGrids);

        // Execute
        List<UserHeatmapGrid> result = heatmapGridService.getUserHeatmapData(
                userId, null, null, null, null);

        // Verify
        assertEquals(expectedGrids, result);
        verify(heatmapGridRepository).findByUserId(userId);
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
