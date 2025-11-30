package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.TimelineActivityDTO;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final org.operaton.fitpub.repository.LikeRepository likeRepository;
    private final org.operaton.fitpub.repository.CommentRepository commentRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Get the federated timeline for a user.
     * Includes public activities from:
     * - The user's own activities
     * - Activities from users they follow (local users only for now)
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

        // Get list of user IDs that the current user follows
        List<UUID> followedUserIds = getFollowedLocalUserIds(userId);

        // Include the current user's own activities
        followedUserIds.add(userId);

        // Fetch public and followers-only activities from followed users
        Page<Activity> activities = activityRepository.findByUserIdInAndVisibilityInOrderByStartedAtDesc(
            followedUserIds,
            List.of(Activity.Visibility.PUBLIC, Activity.Visibility.FOLLOWERS),
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
                dto.setLikedByCurrentUser(likeRepository.existsByActivityIdAndUserId(activity.getId(), userId));

                return dto;
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());

        return new PageImpl<>(timelineActivities, pageable, activities.getTotalElements());
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
}
