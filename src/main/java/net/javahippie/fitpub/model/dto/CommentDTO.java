package net.javahippie.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.javahippie.fitpub.model.entity.Comment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Comment data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {

    private UUID id;
    private UUID activityId;
    private String actorUri;  // Local user URI or remote actor URI
    private String displayName;
    private String avatarUrl;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean local;
    private boolean canDelete;  // True if current user can delete this comment

    /**
     * Creates a DTO from a Comment entity.
     */
    public static CommentDTO fromEntity(Comment comment, String baseUrl, UUID currentUserId) {
        String actorUri;
        boolean canDelete = false;

        if (comment.isLocal()) {
            // Build local actor URI
            actorUri = String.format("%s/users/%s", baseUrl, comment.getUserId());
            // User can delete their own comments
            if (currentUserId != null && currentUserId.equals(comment.getUserId())) {
                canDelete = true;
            }
        } else {
            actorUri = comment.getRemoteActorUri();
        }

        return CommentDTO.builder()
            .id(comment.getId())
            .activityId(comment.getActivityId())
            .actorUri(actorUri)
            .displayName(comment.getDisplayName())
            .avatarUrl(comment.getAvatarUrl())
            .content(comment.getContent())
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .local(comment.isLocal())
            .canDelete(canDelete)
            .build();
    }
}
