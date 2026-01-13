package net.javahippie.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.javahippie.fitpub.model.entity.Like;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Like data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeDTO {

    private UUID id;
    private UUID activityId;
    private String actorUri;  // Local user URI or remote actor URI
    private String displayName;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private boolean local;

    /**
     * Creates a DTO from a Like entity.
     */
    public static LikeDTO fromEntity(Like like, String baseUrl) {
        String actorUri;
        if (like.isLocal()) {
            // Build local actor URI: https://domain/users/userId
            actorUri = String.format("%s/users/%s", baseUrl, like.getUserId());
        } else {
            actorUri = like.getRemoteActorUri();
        }

        return LikeDTO.builder()
            .id(like.getId())
            .activityId(like.getActivityId())
            .actorUri(actorUri)
            .displayName(like.getDisplayName())
            .avatarUrl(like.getAvatarUrl())
            .createdAt(like.getCreatedAt())
            .local(like.isLocal())
            .build();
    }
}
