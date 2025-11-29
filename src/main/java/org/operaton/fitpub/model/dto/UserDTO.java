package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for User data transfer.
 * Used for public user profiles (excludes sensitive data like password hash and private keys).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private UUID id;
    private String username;
    private String email;  // Only shown to the user themselves
    private String displayName;
    private String bio;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Social counts (populated separately)
    private Long followersCount;
    private Long followingCount;

    /**
     * Creates a DTO from a User entity.
     * Note: email should only be included when user is viewing their own profile.
     * Note: follower/following counts are not populated by this method - set them separately.
     */
    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .bio(user.getBio())
            .avatarUrl(user.getAvatarUrl())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }

    /**
     * Creates a public DTO from a User entity (excludes email).
     * Use this when returning user data to other users.
     */
    public static UserDTO fromEntityPublic(User user) {
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .bio(user.getBio())
            .avatarUrl(user.getAvatarUrl())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
}
