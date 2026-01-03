package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.TimelineActivityDTO;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActivity;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.RemoteActivityRepository;
import org.operaton.fitpub.repository.RemoteActorRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing timelines.
 * Provides federated timeline of activities from followed users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private final ActivityRepository activityRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final RemoteActivityRepository remoteActivityRepository;
    private final RemoteActorRepository remoteActorRepository;
    private final org.operaton.fitpub.repository.LikeRepository likeRepository;
    private final org.operaton.fitpub.repository.CommentRepository commentRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Get the federated timeline for a user.
     * Includes public activities from:
     * - The user's own activities
     * - Activities from local users they follow
     * - Activities from remote users they follow (federated)
     *
     * @param userId the authenticated user's ID
     * @param pageable pagination parameters
     * @return page of timeline activities
     */
    @Transactional(readOnly = true)
    public Page<TimelineActivityDTO> getFederatedTimeline(UUID userId, Pageable pageable) {
        log.debug("Fetching federated timeline for user: {}", userId);

        User currentUser = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 1. Get followed remote actor URIs
        List<String> remoteActorUris = getFollowedRemoteActorUris(userId);

        // 2. Get followed local user IDs
        List<UUID> followedUserIds = getFollowedLocalUserIds(userId);
        followedUserIds.add(userId); // Include the current user's own activities

        // 3. Fetch local activities from followed users (fetch more to account for merging)
        // We fetch double the page size to have enough items after merging
        // Explicitly sort by startedAt DESC (latest first)
        Pageable expandedPageable = PageRequest.of(0, pageable.getPageSize() * 2,
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));
        Page<Activity> localActivities = activityRepository.findByUserIdInAndVisibilityInOrderByStartedAtDesc(
            followedUserIds,
            List.of(Activity.Visibility.PUBLIC, Activity.Visibility.FOLLOWERS),
            expandedPageable
        );

        // 4. Fetch remote activities from followed remote actors (if any)
        List<RemoteActivity> remoteActivities = new ArrayList<>();
        if (!remoteActorUris.isEmpty()) {
            // Use same pageable with explicit sort for remote activities
            Page<RemoteActivity> remoteActivitiesPage = remoteActivityRepository.findByRemoteActorUriInAndVisibilityIn(
                remoteActorUris,
                List.of(RemoteActivity.Visibility.PUBLIC, RemoteActivity.Visibility.FOLLOWERS),
                expandedPageable
            );
            remoteActivities = remoteActivitiesPage.getContent();
        }

        // 5. Merge local and remote activities
        List<TimelineActivityDTO> mergedActivities = mergeActivities(
            localActivities.getContent(),
            remoteActivities,
            userId
        );

        // 6. Sort chronologically (most recent first) and paginate
        mergedActivities.sort((a, b) -> {
            if (a.getStartedAt() == null && b.getStartedAt() == null) return 0;
            if (a.getStartedAt() == null) return 1;
            if (b.getStartedAt() == null) return -1;
            return b.getStartedAt().compareTo(a.getStartedAt());
        });

        // Apply pagination to the merged list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), mergedActivities.size());
        List<TimelineActivityDTO> paginatedActivities = mergedActivities.subList(
            Math.min(start, mergedActivities.size()),
            end
        );

        return new PageImpl<>(paginatedActivities, pageable, mergedActivities.size());
    }

    /**
     * Get the public timeline.
     * Shows all public activities from all users.
     *
     * @param userId optional user ID for checking liked status (null for unauthenticated)
     * @param pageable pagination parameters
     * @return page of timeline activities
     */
    @Transactional(readOnly = true)
    public Page<TimelineActivityDTO> getPublicTimeline(UUID userId, Pageable pageable) {
        log.debug("Fetching public timeline");

        // Fetch all public activities
        Page<Activity> activities = activityRepository.findByVisibilityOrderByStartedAtDesc(
            Activity.Visibility.PUBLIC,
            pageable
        );

        // Convert to DTOs
        List<TimelineActivityDTO> timelineActivities = activities.getContent().stream()
            .map(activity -> {
                User activityUser = userRepository.findById(activity.getUserId()).orElse(null);
                if (activityUser == null) {
                    return null;
                }
                TimelineActivityDTO dto = TimelineActivityDTO.fromActivity(
                    activity,
                    activityUser.getUsername(),
                    activityUser.getDisplayName() != null ? activityUser.getDisplayName() : activityUser.getUsername(),
                    activityUser.getAvatarUrl()
                );

                // Add social interaction counts
                dto.setLikesCount(likeRepository.countByActivityId(activity.getId()));
                dto.setCommentsCount(commentRepository.countByActivityIdAndNotDeleted(activity.getId()));

                // Check if current user liked this activity (if authenticated)
                if (userId != null) {
                    dto.setLikedByCurrentUser(likeRepository.existsByActivityIdAndUserId(activity.getId(), userId));
                } else {
                    dto.setLikedByCurrentUser(false);
                }

                return dto;
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());

        return new PageImpl<>(timelineActivities, pageable, activities.getTotalElements());
    }

    /**
     * Get user's own timeline (their activities only).
     *
     * @param userId the user's ID
     * @param pageable pagination parameters
     * @return page of timeline activities
     */
    @Transactional(readOnly = true)
    public Page<TimelineActivityDTO> getUserTimeline(UUID userId, Pageable pageable) {
        log.debug("Fetching user timeline for: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Page<Activity> activities = activityRepository.findByUserIdOrderByStartedAtDesc(userId, pageable);

        List<TimelineActivityDTO> timelineActivities = activities.getContent().stream()
            .map(activity -> {
                TimelineActivityDTO dto = TimelineActivityDTO.fromActivity(
                    activity,
                    user.getUsername(),
                    user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                    user.getAvatarUrl()
                );

                // Add social interaction counts
                dto.setLikesCount(likeRepository.countByActivityId(activity.getId()));
                dto.setCommentsCount(commentRepository.countByActivityIdAndNotDeleted(activity.getId()));
                dto.setLikedByCurrentUser(likeRepository.existsByActivityIdAndUserId(activity.getId(), userId));

                return dto;
            })
            .collect(Collectors.toList());

        return new PageImpl<>(timelineActivities, pageable, activities.getTotalElements());
    }

    /**
     * Get IDs of local users that the given user follows.
     *
     * @param userId the user's ID
     * @return list of followed local user IDs
     */
    private List<UUID> getFollowedLocalUserIds(UUID userId) {
        List<Follow> follows = followRepository.findAcceptedFollowingByUserId(userId);
        List<UUID> followedUserIds = new ArrayList<>();

        for (Follow follow : follows) {
            // Check if the followed actor is a local user
            String actorUri = follow.getFollowingActorUri();
            if (actorUri.startsWith(baseUrl + "/users/")) {
                String username = actorUri.substring((baseUrl + "/users/").length());
                userRepository.findByUsername(username).ifPresent(user -> followedUserIds.add(user.getId()));
            }
        }

        return followedUserIds;
    }

    /**
     * Get actor URIs of remote users that the given user follows.
     *
     * @param userId the user's ID
     * @return list of followed remote actor URIs
     */
    private List<String> getFollowedRemoteActorUris(UUID userId) {
        List<Follow> follows = followRepository.findAcceptedFollowingByUserId(userId);
        List<String> remoteActorUris = new ArrayList<>();

        for (Follow follow : follows) {
            // Check if the followed actor is a remote user (not on this instance)
            String actorUri = follow.getFollowingActorUri();
            if (!actorUri.startsWith(baseUrl + "/users/")) {
                remoteActorUris.add(actorUri);
            }
        }

        return remoteActorUris;
    }

    /**
     * Merge local and remote activities into a single list of timeline DTOs.
     *
     * @param localActivities list of local Activity entities
     * @param remoteActivities list of remote RemoteActivity entities
     * @param currentUserId the current user's ID (for like status)
     * @return merged list of TimelineActivityDTOs
     */
    private List<TimelineActivityDTO> mergeActivities(
        List<Activity> localActivities,
        List<RemoteActivity> remoteActivities,
        UUID currentUserId
    ) {
        List<TimelineActivityDTO> merged = new ArrayList<>();

        // Convert local activities to DTOs
        for (Activity activity : localActivities) {
            User activityUser = userRepository.findById(activity.getUserId()).orElse(null);
            if (activityUser == null) {
                continue;
            }

            TimelineActivityDTO dto = TimelineActivityDTO.fromActivity(
                activity,
                activityUser.getUsername(),
                activityUser.getDisplayName() != null ? activityUser.getDisplayName() : activityUser.getUsername(),
                activityUser.getAvatarUrl()
            );

            // Add social interaction counts
            dto.setLikesCount(likeRepository.countByActivityId(activity.getId()));
            dto.setCommentsCount(commentRepository.countByActivityIdAndNotDeleted(activity.getId()));
            dto.setLikedByCurrentUser(likeRepository.existsByActivityIdAndUserId(activity.getId(), currentUserId));

            merged.add(dto);
        }

        // Convert remote activities to DTOs
        for (RemoteActivity remoteActivity : remoteActivities) {
            RemoteActor actor = remoteActorRepository.findByActorUri(remoteActivity.getRemoteActorUri()).orElse(null);
            if (actor == null) {
                log.warn("Remote actor not found for URI: {}", remoteActivity.getRemoteActorUri());
                continue;
            }

            TimelineActivityDTO dto = TimelineActivityDTO.fromRemoteActivity(remoteActivity, actor);

            // Remote activities don't have like/comment counts in this implementation
            // (would require additional federation support)
            dto.setLikesCount(0L);
            dto.setCommentsCount(0L);
            dto.setLikedByCurrentUser(false);

            merged.add(dto);
        }

        return merged;
    }
}
