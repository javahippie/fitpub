package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.LikeDTO;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.Like;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.LikeRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for like operations.
 */
@RestController
@RequestMapping("/api/activities/{activityId}/likes")
@RequiredArgsConstructor
@Slf4j
public class LikeController {

    private final LikeRepository likeRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Helper method to get user from authenticated UserDetails.
     */
    private User getUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Get all likes for an activity.
     *
     * @param activityId the activity ID
     * @return list of likes
     */
    @GetMapping
    public ResponseEntity<List<LikeDTO>> getLikes(@PathVariable UUID activityId) {
        // Check if activity exists
        if (!activityRepository.existsById(activityId)) {
            return ResponseEntity.notFound().build();
        }

        List<Like> likes = likeRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        List<LikeDTO> likeDTOs = likes.stream()
            .map(like -> LikeDTO.fromEntity(like, baseUrl))
            .collect(Collectors.toList());

        return ResponseEntity.ok(likeDTOs);
    }

    /**
     * Like an activity.
     *
     * @param activityId the activity ID
     * @param userDetails the authenticated user
     * @return the created like
     */
    @PostMapping
    @Transactional
    public ResponseEntity<LikeDTO> likeActivity(
        @PathVariable UUID activityId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);

        // Check if activity exists
        Activity activity = activityRepository.findById(activityId)
            .orElse(null);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if already liked
        if (likeRepository.existsByActivityIdAndUserId(activityId, user.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // Create like
        Like like = Like.builder()
            .activityId(activityId)
            .userId(user.getId())
            .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
            .avatarUrl(user.getAvatarUrl())
            .build();

        Like saved = likeRepository.save(like);

        log.info("User {} liked activity {}", user.getUsername(), activityId);

        // TODO: Send ActivityPub Like activity to followers if activity is public

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(LikeDTO.fromEntity(saved, baseUrl));
    }

    /**
     * Unlike an activity.
     *
     * @param activityId the activity ID
     * @param userDetails the authenticated user
     * @return no content
     */
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> unlikeActivity(
        @PathVariable UUID activityId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);

        // Check if like exists
        if (!likeRepository.existsByActivityIdAndUserId(activityId, user.getId())) {
            return ResponseEntity.notFound().build();
        }

        likeRepository.deleteByActivityIdAndUserId(activityId, user.getId());

        log.info("User {} unliked activity {}", user.getUsername(), activityId);

        // TODO: Send ActivityPub Undo Like activity to followers if activity is public

        return ResponseEntity.noContent().build();
    }
}
