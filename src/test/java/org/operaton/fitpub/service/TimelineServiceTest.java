package org.operaton.fitpub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.fitpub.model.dto.TimelineActivityDTO;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActivity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.RemoteActivityRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private org.operaton.fitpub.repository.LikeRepository likeRepository;

    @Mock
    private org.operaton.fitpub.repository.CommentRepository commentRepository;

    @Mock
    private org.operaton.fitpub.repository.RemoteActorRepository remoteActorRepository;

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

        // Mock local activities
        List<Activity> localActivities = List.of(
                createLocalActivity("Morning Run", LocalDateTime.now().minusHours(2))
        );
        Page<Activity> localActivitiesPage = new PageImpl<>(localActivities);
        when(activityRepository.findByUserIdInAndVisibilityInOrderByStartedAtDesc(
                anyList(), anyList(), any(Pageable.class)))
                .thenReturn(localActivitiesPage);

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
        verify(activityRepository).findByUserIdInAndVisibilityInOrderByStartedAtDesc(
                anyList(), anyList(), any(Pageable.class));
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
        List<Activity> localActivities = List.of(
                createLocalActivity("Solo Run", LocalDateTime.now())
        );
        Page<Activity> localActivitiesPage = new PageImpl<>(localActivities);
        when(activityRepository.findByUserIdInAndVisibilityInOrderByStartedAtDesc(
                anyList(), anyList(), any(Pageable.class)))
                .thenReturn(localActivitiesPage);

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
        Activity localActivity = createLocalActivity("Local Run", LocalDateTime.now().minusHours(1));
        Page<Activity> localActivitiesPage = new PageImpl<>(List.of(localActivity));
        when(activityRepository.findByUserIdInAndVisibilityInOrderByStartedAtDesc(
                anyList(), anyList(), any(Pageable.class)))
                .thenReturn(localActivitiesPage);

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

    private Activity createLocalActivity(String title, LocalDateTime startedAt) {
        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        activity.setUserId(userId);
        activity.setTitle(title);
        activity.setDescription("Test activity");
        activity.setActivityType(Activity.ActivityType.RUN);
        activity.setStartedAt(startedAt);
        activity.setVisibility(Activity.Visibility.PUBLIC);
        activity.setTotalDistance(java.math.BigDecimal.valueOf(5000));
        activity.setTotalDurationSeconds(1800L);
        return activity;
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
