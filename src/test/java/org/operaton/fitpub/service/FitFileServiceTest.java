package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.fitpub.exception.FitFileProcessingException;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.repository.ActivityMetricsRepository;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.util.FitFileValidator;
import org.operaton.fitpub.util.FitParser;
import org.operaton.fitpub.util.TrackSimplifier;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FitFileService.
 */
@ExtendWith(MockitoExtension.class)
class FitFileServiceTest {

    @Mock
    private FitFileValidator validator;

    @Mock
    private FitParser parser;

    @Mock
    private TrackSimplifier trackSimplifier;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityMetricsRepository metricsRepository;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private FitFileService fitFileService;

    private UUID testUserId;
    private MockMultipartFile testFile;
    private FitParser.ParsedFitData testParsedData;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testFile = new MockMultipartFile(
            "file",
            "test-activity.fit",
            "application/octet-stream",
            new byte[100]
        );

        // Configure ObjectMapper for Java 8 Time
        objectMapper.registerModule(new JavaTimeModule());

        // Create test parsed data
        testParsedData = createTestParsedData();
    }

    @Test
    @DisplayName("Should successfully process a valid FIT file")
    void testProcessFitFileSuccess() throws Exception {
        // Arrange
        when(parser.parse(any(byte[].class))).thenReturn(testParsedData);
        when(trackSimplifier.simplify(any())).thenAnswer(invocation -> {
            Coordinate[] coords = invocation.getArgument(0);
            return new GeometryFactory().createLineString(coords);
        });
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId(UUID.randomUUID());
            activity.setCreatedAt(LocalDateTime.now());
            activity.setUpdatedAt(LocalDateTime.now());
            return activity;
        });

        // Act
        Activity result = fitFileService.processFitFile(
            testFile,
            testUserId,
            "Test Run",
            "Morning run",
            Activity.Visibility.PUBLIC
        );

        // Assert
        assertNotNull(result);
        assertEquals("Test Run", result.getTitle());
        assertEquals("Morning run", result.getDescription());
        assertEquals(testUserId, result.getUserId());
        assertEquals(Activity.Visibility.PUBLIC, result.getVisibility());
        assertEquals(Activity.ActivityType.RUN, result.getActivityType());

        verify(validator).validate(any(), anyLong());
        verify(parser).parse(any(byte[].class));
        verify(activityRepository).save(any(Activity.class));
    }

    @Test
    @DisplayName("Should generate default title when title is null")
    void testProcessFitFileWithDefaultTitle() throws Exception {
        // Arrange
        when(parser.parse(any(byte[].class))).thenReturn(testParsedData);
        when(trackSimplifier.simplify(any())).thenAnswer(invocation -> {
            Coordinate[] coords = invocation.getArgument(0);
            return new GeometryFactory().createLineString(coords);
        });
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId(UUID.randomUUID());
            return activity;
        });

        // Act
        Activity result = fitFileService.processFitFile(
            testFile,
            testUserId,
            null,
            null,
            Activity.Visibility.PUBLIC
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.getTitle().contains("Run"));
        assertTrue(result.getTitle().contains(testParsedData.getStartTime().toLocalDate().toString()));
    }

    @Test
    @DisplayName("Should throw exception when validator fails")
    void testProcessFitFileValidationFailure() throws Exception {
        // Arrange
        doThrow(new FitFileProcessingException("Invalid file"))
            .when(validator).validate(any(), anyLong());

        // Act & Assert
        assertThrows(FitFileProcessingException.class, () ->
            fitFileService.processFitFile(
                testFile,
                testUserId,
                "Test",
                null,
                Activity.Visibility.PUBLIC
            )
        );

        verify(parser, never()).parse(any(byte[].class));
        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    @DisplayName("Should throw exception when parser fails")
    void testProcessFitFileParsingFailure() throws Exception {
        // Arrange
        when(parser.parse(any(byte[].class)))
            .thenThrow(new FitFileProcessingException("Parsing failed"));

        // Act & Assert
        assertThrows(FitFileProcessingException.class, () ->
            fitFileService.processFitFile(
                testFile,
                testUserId,
                "Test",
                null,
                Activity.Visibility.PUBLIC
            )
        );

        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    @DisplayName("Should successfully delete an activity")
    void testDeleteActivity() {
        // Arrange
        UUID activityId = UUID.randomUUID();
        Activity activity = Activity.builder()
            .id(activityId)
            .userId(testUserId)
            .build();

        when(activityRepository.findByIdAndUserId(activityId, testUserId))
            .thenReturn(Optional.of(activity));

        // Act
        boolean result = fitFileService.deleteActivity(activityId, testUserId);

        // Assert
        assertTrue(result);
        verify(activityRepository).delete(activity);
    }

    @Test
    @DisplayName("Should return false when deleting non-existent activity")
    void testDeleteNonExistentActivity() {
        // Arrange
        UUID activityId = UUID.randomUUID();
        when(activityRepository.findByIdAndUserId(activityId, testUserId))
            .thenReturn(Optional.empty());

        // Act
        boolean result = fitFileService.deleteActivity(activityId, testUserId);

        // Assert
        assertFalse(result);
        verify(activityRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should retrieve activity by ID and user ID")
    void testGetActivity() {
        // Arrange
        UUID activityId = UUID.randomUUID();
        Activity activity = Activity.builder()
            .id(activityId)
            .userId(testUserId)
            .title("Test Activity")
            .build();

        when(activityRepository.findByIdAndUserId(activityId, testUserId))
            .thenReturn(Optional.of(activity));

        // Act
        Activity result = fitFileService.getActivity(activityId, testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(activityId, result.getId());
        assertEquals("Test Activity", result.getTitle());
    }

    @Test
    @DisplayName("Should return null for non-existent activity")
    void testGetNonExistentActivity() {
        // Arrange
        UUID activityId = UUID.randomUUID();
        when(activityRepository.findByIdAndUserId(activityId, testUserId))
            .thenReturn(Optional.empty());

        // Act
        Activity result = fitFileService.getActivity(activityId, testUserId);

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Should retrieve all activities for a user")
    void testGetUserActivities() {
        // Arrange
        List<Activity> activities = new ArrayList<>();
        activities.add(Activity.builder().id(UUID.randomUUID()).userId(testUserId).build());
        activities.add(Activity.builder().id(UUID.randomUUID()).userId(testUserId).build());

        when(activityRepository.findByUserIdOrderByStartedAtDesc(testUserId))
            .thenReturn(activities);

        // Act
        List<Activity> result = fitFileService.getUserActivities(testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should process FIT file with metrics")
    void testProcessFitFileWithMetrics() throws Exception {
        // Arrange
        when(parser.parse(any(byte[].class))).thenReturn(testParsedData);
        when(trackSimplifier.simplify(any())).thenAnswer(invocation -> {
            Coordinate[] coords = invocation.getArgument(0);
            return new GeometryFactory().createLineString(coords);
        });

        ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
        when(activityRepository.save(activityCaptor.capture())).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId(UUID.randomUUID());
            return activity;
        });

        // Act
        Activity result = fitFileService.processFitFile(
            testFile,
            testUserId,
            "Complete Activity",
            null,
            Activity.Visibility.PUBLIC
        );

        // Assert
        assertNotNull(result);
        Activity savedActivity = activityCaptor.getValue();

        assertNotNull(savedActivity.getSimplifiedTrack());
        assertNotNull(savedActivity.getTrackPointsJson());
        assertNotNull(savedActivity.getMetrics());
        assertEquals(testParsedData.getTotalDistance(), savedActivity.getTotalDistance());
        assertEquals(testParsedData.getTotalDuration(), savedActivity.getTotalDuration());
    }

    /**
     * Creates test parsed FIT data with realistic values.
     */
    private FitParser.ParsedFitData createTestParsedData() {
        FitParser.ParsedFitData data = new FitParser.ParsedFitData();

        LocalDateTime startTime = LocalDateTime.of(2024, 1, 15, 8, 0, 0);
        data.setStartTime(startTime);
        data.setEndTime(startTime.plusMinutes(30));
        data.setActivityType(Activity.ActivityType.RUN);
        data.setTotalDistance(BigDecimal.valueOf(5000.0));
        data.setTotalDuration(Duration.ofMinutes(30));
        data.setElevationGain(BigDecimal.valueOf(100.0));
        data.setElevationLoss(BigDecimal.valueOf(95.0));

        // Add test track points
        List<FitParser.TrackPointData> trackPoints = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            FitParser.TrackPointData tp = new FitParser.TrackPointData();
            tp.setTimestamp(startTime.plusMinutes(i * 3));
            tp.setLatitude(47.0 + i * 0.001);
            tp.setLongitude(8.0 + i * 0.001);
            tp.setElevation(BigDecimal.valueOf(500 + i * 10));
            tp.setHeartRate(140 + i);
            tp.setSpeed(BigDecimal.valueOf(10.0));
            trackPoints.add(tp);
        }

        data.setTrackPoints(trackPoints);

        // Add test metrics
        FitParser.ActivityMetricsData metrics = new FitParser.ActivityMetricsData();
        metrics.setAverageSpeed(BigDecimal.valueOf(10.0));
        metrics.setMaxSpeed(BigDecimal.valueOf(15.0));
        metrics.setAverageHeartRate(150);
        metrics.setMaxHeartRate(170);
        metrics.setCalories(300);
        data.setMetrics(metrics);

        return data;
    }
}
