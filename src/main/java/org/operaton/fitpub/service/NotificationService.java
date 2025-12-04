package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.*;
import org.operaton.fitpub.repository.NotificationRepository;
import org.operaton.fitpub.repository.RemoteActorRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing user notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RemoteActorRepository remoteActorRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Create a notification when someone likes an activity.
     *
     * @param activity the activity that was liked
     * @param likerActorUri the URI of the user who liked the activity
     */
    @Transactional
    public void createActivityLikedNotification(Activity activity, String likerActorUri) {
        // Get the activity owner
        User activityOwner = userRepository.findById(activity.getUserId())
            .orElse(null);
        if (activityOwner == null) {
            log.warn("Could not find activity owner for activity: {}", activity.getId());
            return;
        }

        // Don't notify if user liked their own activity
        String activityOwnerUri = activityOwner.getActorUri(baseUrl);
        if (activityOwnerUri.equals(likerActorUri)) {
            return;
        }

        // Get actor information
        ActorInfo actorInfo = getActorInfo(likerActorUri);
        if (actorInfo == null) {
            log.warn("Could not find actor info for URI: {}", likerActorUri);
            return;
        }

        Notification notification = Notification.builder()
            .user(activityOwner)
            .type(Notification.NotificationType.ACTIVITY_LIKED)
            .actorUri(likerActorUri)
            .actorDisplayName(actorInfo.displayName)
            .actorUsername(actorInfo.username)
            .actorAvatarUrl(actorInfo.avatarUrl)
            .activityId(activity.getId())
            .activityTitle(activity.getTitle() != null ? activity.getTitle() : "Untitled Activity")
            .build();

        notificationRepository.save(notification);
        log.debug("Created ACTIVITY_LIKED notification for user {} from {}", activityOwner.getUsername(), actorInfo.username);
    }

    /**
     * Create a notification when someone comments on an activity.
     *
     * @param activity the activity that was commented on
     * @param comment the comment
     * @param commenterActorUri the URI of the user who commented
     */
    @Transactional
    public void createActivityCommentedNotification(Activity activity, Comment comment, String commenterActorUri) {
        // Get the activity owner
        User activityOwner = userRepository.findById(activity.getUserId())
            .orElse(null);
        if (activityOwner == null) {
            log.warn("Could not find activity owner for activity: {}", activity.getId());
            return;
        }

        // Don't notify if user commented on their own activity
        String activityOwnerUri = activityOwner.getActorUri(baseUrl);
        if (activityOwnerUri.equals(commenterActorUri)) {
            return;
        }

        // Get actor information
        ActorInfo actorInfo = getActorInfo(commenterActorUri);
        if (actorInfo == null) {
            log.warn("Could not find actor info for URI: {}", commenterActorUri);
            return;
        }

        // Truncate comment text for preview
        String commentPreview = comment.getContent();
        if (commentPreview != null && commentPreview.length() > 200) {
            commentPreview = commentPreview.substring(0, 197) + "...";
        }

        Notification notification = Notification.builder()
            .user(activityOwner)
            .type(Notification.NotificationType.ACTIVITY_COMMENTED)
            .actorUri(commenterActorUri)
            .actorDisplayName(actorInfo.displayName)
            .actorUsername(actorInfo.username)
            .actorAvatarUrl(actorInfo.avatarUrl)
            .activityId(activity.getId())
            .activityTitle(activity.getTitle() != null ? activity.getTitle() : "Untitled Activity")
            .commentId(comment.getId())
            .commentText(commentPreview)
            .build();

        notificationRepository.save(notification);
        log.debug("Created ACTIVITY_COMMENTED notification for user {} from {}", activityOwner.getUsername(), actorInfo.username);
    }

    /**
     * Create a notification when someone follows a user.
     *
     * @param followedUser the user who was followed
     * @param followerActorUri the URI of the user who followed
     */
    @Transactional
    public void createUserFollowedNotification(User followedUser, String followerActorUri) {
        // Get actor information
        ActorInfo actorInfo = getActorInfo(followerActorUri);
        if (actorInfo == null) {
            log.warn("Could not find actor info for URI: {}", followerActorUri);
            return;
        }

        Notification notification = Notification.builder()
            .user(followedUser)
            .type(Notification.NotificationType.USER_FOLLOWED)
            .actorUri(followerActorUri)
            .actorDisplayName(actorInfo.displayName)
            .actorUsername(actorInfo.username)
            .actorAvatarUrl(actorInfo.avatarUrl)
            .build();

        notificationRepository.save(notification);
        log.debug("Created USER_FOLLOWED notification for user {} from {}", followedUser.getUsername(), actorInfo.username);
    }

    /**
     * Get all notifications for a user.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return page of notifications
     */
    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable);
    }

    /**
     * Get unread notifications for a user.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return page of unread notifications
     */
    @Transactional(readOnly = true)
    public Page<Notification> getUnreadNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findUnreadByUserId(userId, pageable);
    }

    /**
     * Count unread notifications for a user.
     *
     * @param userId the user ID
     * @return count of unread notifications
     */
    @Transactional(readOnly = true)
    public long countUnreadNotifications(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Mark a notification as read.
     *
     * @param notificationId the notification ID
     * @param userId the user ID (for authorization)
     * @return true if marked as read, false if not found or not owned by user
     */
    @Transactional
    public boolean markAsRead(UUID notificationId, UUID userId) {
        return notificationRepository.findById(notificationId)
            .filter(n -> n.getUser().getId().equals(userId))
            .map(notification -> {
                if (!notification.isRead()) {
                    notification.markAsRead();
                    notificationRepository.save(notification);
                }
                return true;
            })
            .orElse(false);
    }

    /**
     * Mark all notifications as read for a user.
     *
     * @param userId the user ID
     * @return number of notifications marked as read
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsReadByUserId(userId);
    }

    /**
     * Delete a notification.
     *
     * @param notificationId the notification ID
     * @param userId the user ID (for authorization)
     * @return true if deleted, false if not found or not owned by user
     */
    @Transactional
    public boolean deleteNotification(UUID notificationId, UUID userId) {
        return notificationRepository.findById(notificationId)
            .filter(n -> n.getUser().getId().equals(userId))
            .map(notification -> {
                notificationRepository.delete(notification);
                return true;
            })
            .orElse(false);
    }

    /**
     * Clean up old read notifications (older than 30 days).
     *
     * @param userId the user ID
     * @return number of notifications deleted
     */
    @Transactional
    public int cleanupOldNotifications(UUID userId) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        return notificationRepository.deleteOldReadNotifications(userId, cutoffDate);
    }

    /**
     * Helper method to get actor information from either local users or remote actors.
     *
     * @param actorUri the actor URI
     * @return actor information or null if not found
     */
    private ActorInfo getActorInfo(String actorUri) {
        // Check if it's a local user
        if (actorUri.startsWith(baseUrl)) {
            String username = actorUri.substring(actorUri.lastIndexOf("/") + 1);
            return userRepository.findByUsername(username)
                .map(user -> new ActorInfo(
                    user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                    user.getUsername(),
                    user.getAvatarUrl()
                ))
                .orElse(null);
        }

        // Check if it's a remote actor
        return remoteActorRepository.findByActorUri(actorUri)
            .map(actor -> new ActorInfo(
                actor.getDisplayName() != null ? actor.getDisplayName() : actor.getUsername(),
                actor.getUsername(),
                actor.getAvatarUrl()
            ))
            .orElse(null);
    }

    /**
     * Internal class to hold actor information.
     */
    private static class ActorInfo {
        String displayName;
        String username;
        String avatarUrl;

        ActorInfo(String displayName, String username, String avatarUrl) {
            this.displayName = displayName;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }
}
