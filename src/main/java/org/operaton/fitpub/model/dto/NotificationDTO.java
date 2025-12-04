package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Notification data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private UUID id;
    private String type;
    private String actorUri;
    private String actorDisplayName;
    private String actorUsername;
    private String actorAvatarUrl;
    private UUID activityId;
    private String activityTitle;
    private UUID commentId;
    private String commentText;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    /**
     * Creates a DTO from a Notification entity.
     *
     * @param notification the notification entity
     * @return notification DTO
     */
    public static NotificationDTO fromEntity(Notification notification) {
        return NotificationDTO.builder()
            .id(notification.getId())
            .type(notification.getType().name())
            .actorUri(notification.getActorUri())
            .actorDisplayName(notification.getActorDisplayName())
            .actorUsername(notification.getActorUsername())
            .actorAvatarUrl(notification.getActorAvatarUrl())
            .activityId(notification.getActivityId())
            .activityTitle(notification.getActivityTitle())
            .commentId(notification.getCommentId())
            .commentText(notification.getCommentText())
            .read(notification.isRead())
            .createdAt(notification.getCreatedAt())
            .readAt(notification.getReadAt())
            .build();
    }
}
