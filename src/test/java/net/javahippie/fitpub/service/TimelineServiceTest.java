package net.javahippie.fitpub.service;

import net.javahippie.fitpub.repository.CommentRepository;
import net.javahippie.fitpub.repository.LikeRepository;
import net.javahippie.fitpub.repository.RemoteActorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import net.javahippie.fitpub.model.dto.TimelineActivityDTO;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.Follow;
import net.javahippie.fitpub.model.entity.RemoteActivity;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.ActivityRepository;
import net.javahippie.fitpub.repository.FollowRepository;
import net.javahippie.fitpub.repository.RemoteActivityRepository;
import net.javahippie.fitpub.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimelineService.
 * Tests timeline retrieval with mixed local and remote activities.
 */
@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private RemoteActivityRepository remoteActivityRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private RemoteActorRepository remoteActorRepository;

    @Mock
    private TimelineResultMapper timelineResultMapper;

    @InjectMocks
    private TimelineService timelineService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should retrieve federated timeline with remote activities without errors")
    void testGetFederatedTimeline_WithRemoteActivities_NoError() {
        // Given: User follows remote actors
        List<Follow> follows = List.of(
                createRemoteFollow("https://remote.example/users/alice"),
                createRemoteFollow("https://remote.example/users/bob")
        );

        when(followRepository.findAcceptedFollowingByUserId(eq(userId)))
                .thenReturn(follows);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Mock local activities using Object[] (as returned by native query)
        Object[] row1 = createTimelineRow("Morning Run", LocalDateTime.now().minusHours(2));
        List<Object[]> localActivityRows = new ArrayList<>();
        localActivityRows.add(row1);
        Page<Object[]> localActivitiesPage = new PageImpl<>(localActivityRows);
        when(activityRepository.findFederatedTimelineWithStats(
                anyList(), anyList(), eq(userId), any(Pageable.class)))
                .thenReturn(localActivitiesPage);

        // Mock the mapper
        TimelineActivityDTO dto1 = createTimelineDTO("Morning Run", LocalDateTime.now().minusHours(2));
        when(timelineResultMapper.mapToTimelineActivityDTO(any(Object[].class)))
                .thenReturn(dto1);

        // Mock remote activities - this should use publishedAt for sorting
        List<RemoteActivity> remoteActivities = List.of(
                createRemoteActivity("Remote Run 1", Instant.now().minusSeconds(3600)),
                createRemoteActivity("Remote Run 2", Instant.now().minusSeconds(7200))
        );
        Page<RemoteActivity> remoteActivitiesPage = new PageImpl<>(remoteActivities);
        when(remoteActivityRepository.findByRemoteActorUriInAndVisibilityIn(
                anyList(), anyList(), any(Pageable.class)))
                .thenReturn(remoteActivitiesPage);

        Pageable pageable = PageRequest.of(0, 20);

        // When - This should NOT throw an exception about 'startedAt' not found
        Page<TimelineActivityDTO> result = assertDoesNotThrow(() ->
                timelineService.getFederatedTimeline(userId, pageable)
        );

        // Then
        assertNotNull(result, "Result should not be null");
        assertFalse(result.isEmpty(), "Result should contain activities");

        // Verify that both repositories were called
        verify(activityRepository).findFederatedTimelineWithStats(
                anyList(), anyList(), eq(userId), any(Pageable.class));
        verify(remoteActivityRepository).findByRemoteActorUriInAndVisibilityIn(
                anyList(), anyList(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should handle empty remote activities gracefully")
    void testGetFederatedTimeline_NoRemoteActivities() {
        // Given: User has no remote follows
        when(followRepository.findAcceptedFollowingByUserId(eq(userId)))
                .thenReturn(new ArrayList<>());
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Mock local activities only
        Object[] row1 = createTimelineRow("Solo Run", LocalDateTime.now());
        List<Object[]> localActivityRows = new ArrayList<>();
        localActivityRows.add(row1);
        Page<Object[]> localActivitiesPage = new PageImpl<>(localActivityRows);
        when(activityRepository.findFederatedTimelineWithStats(
                anyList(), anyList(), eq(userId), any(Pageable.class)))
                .thenReturn(localActivitiesPage);

        // Mock the mapper
        TimelineActivityDTO dto1 = createTimelineDTO("Solo Run", LocalDateTime.now());
        when(timelineResultMapper.mapToTimelineActivityDTO(any(Object[].class)))
                .thenReturn(dto1);

        Pageable pageable = PageRequest.of(0, 20);

        // When
        Page<TimelineActivityDTO> result = timelineService.getFederatedTimeline(userId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());

        // Verify remote repository was NOT called (no remote follows)
        verify(remoteActivityRepository, never()).findByRemoteActorUriInAndVisibilityIn(
                anyList(), anyList(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should merge local and remote activities without errors")
    void testGetFederatedTimeline_MergedActivitiesSorted() {
        // Given
        List<Follow> follows = List.of(
                createRemoteFollow("https://remote.example/users/alice")
        );
        when(followRepository.findAcceptedFollowingByUserId(eq(userId)))
                .thenReturn(follows);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Local activity
        Object[] row1 = createTimelineRow("Local Run", LocalDateTime.now().minusHours(1));
        List<Object[]> localActivityRows = new ArrayList<>();
        localActivityRows.add(row1);
        Page<Object[]> localActivitiesPage = new PageImpl<>(localActivityRows);
        when(activityRepository.findFederatedTimelineWithStats(
                anyList(), anyList(), eq(userId), any(Pageable.class)))
                .thenReturn(localActivitiesPage);

        // Mock the mapper
        TimelineActivityDTO dto1 = createTimelineDTO("Local Run", LocalDateTime.now().minusHours(1));
        when(timelineResultMapper.mapToTimelineActivityDTO(any(Object[].class)))
                .thenReturn(dto1);

        // Remote activity
        RemoteActivity remoteActivity = createRemoteActivity("Remote Run", Instant.now().minusSeconds(7200));
        Page<RemoteActivity> remoteActivitiesPage = new PageImpl<>(List.of(remoteActivity));
        when(remoteActivityRepository.findByRemoteActorUriInAndVisibilityIn(
                anyList(), anyList(), any(Pageable.class)))
                .thenReturn(remoteActivitiesPage);

        Pageable pageable = PageRequest.of(0, 20);

        // When - Should not throw exception
        Page<TimelineActivityDTO> result = assertDoesNotThrow(() ->
                timelineService.getFederatedTimeline(userId, pageable)
        );

        // Then - Should have at least one activity
        assertNotNull(result);
        assertFalse(result.isEmpty(), "Timeline should contain activities");
        assertTrue(result.getContent().size() >= 1, "Timeline should merge both local and remote activities");
    }

    // Helper methods

    private Follow createRemoteFollow(String remoteActorUri) {
        Follow follow = new Follow();
        follow.setId(UUID.randomUUID());
        follow.setFollowerId(userId);
        follow.setFollowingActorUri(remoteActorUri);
        follow.setStatus(Follow.FollowStatus.ACCEPTED);
        return follow;
    }

    /**
     * Creates an Object[] representing a row from the native query.
     * The structure matches what TimelineResultMapper expects.
     */
    private Object[] createTimelineRow(String title, LocalDateTime startedAt) {
        UUID activityId = UUID.randomUUID();
        return new Object[]{
                activityId,                              // 0: activity_id
                userId,                                  // 1: user_id
                "testuser",                              // 2: username
                "Test User",                             // 3: display_name
                null,                                    // 4: avatar_url
                title,                                   // 5: title
                "Test activity",                         // 6: description
                "RUN",                                   // 7: activity_type
                startedAt,                               // 8: started_at
                "PUBLIC",                                // 9: visibility
                BigDecimal.valueOf(5000),                // 10: total_distance
                1800L,                                   // 11: total_duration_seconds
                BigDecimal.valueOf(100),                 // 12: elevation_gain
                0L,                                      // 13: likes_count
                0L                                       // 14: comments_count
        };
    }

    private TimelineActivityDTO createTimelineDTO(String title, LocalDateTime startedAt) {
        return TimelineActivityDTO.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .displayName("Test User")
                .title(title)
                .description("Test activity")
                .activityType("RUN")
                .startedAt(startedAt)
                .visibility("PUBLIC")
                .totalDistance(5000.0)
                .totalDurationSeconds(1800L)
                .elevationGain(100.0)
                .likesCount(0L)
                .commentsCount(0L)
                .isLocal(true)
                .build();
    }

    private RemoteActivity createRemoteActivity(String title, Instant publishedAt) {
        return RemoteActivity.builder()
                .id(UUID.randomUUID())
                .activityUri("https://remote.example/activities/" + UUID.randomUUID())
                .remoteActorUri("https://remote.example/users/alice")
                .title(title)
                .description("Remote test activity")
                .activityType("RUN")
                .publishedAt(publishedAt)
                .visibility(RemoteActivity.Visibility.PUBLIC)
                .totalDistance(5000L)
                .totalDurationSeconds(1800L)
                .build();
    }
}
