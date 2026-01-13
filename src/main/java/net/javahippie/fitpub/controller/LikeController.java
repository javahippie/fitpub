package net.javahippie.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.dto.LikeDTO;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.Like;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.ActivityRepository;
import net.javahippie.fitpub.repository.LikeRepository;
import net.javahippie.fitpub.repository.UserRepository;
import net.javahippie.fitpub.service.FederationService;
import net.javahippie.fitpub.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final FederationService federationService;
    private final NotificationService notificationService;

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

        // Create notification for activity owner
        String likerActorUri = user.getActorUri(baseUrl);
        notificationService.createActivityLikedNotification(activity, likerActorUri);

        // Send ActivityPub Like activity to followers if activity is public
        if (activity.getVisibility() == Activity.Visibility.PUBLIC) {
            String activityUri = baseUrl + "/activities/" + activityId;
            federationService.sendLikeActivity(activityUri, user);
        }

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

        // Get activity for visibility check
        Activity activity = activityRepository.findById(activityId).orElse(null);

        likeRepository.deleteByActivityIdAndUserId(activityId, user.getId());

        log.info("User {} unliked activity {}", user.getUsername(), activityId);

        // Send ActivityPub Undo Like activity to followers if activity is public
        if (activity != null && activity.getVisibility() == Activity.Visibility.PUBLIC) {
            String activityUri = baseUrl + "/activities/" + activityId;
            String likeId = baseUrl + "/activities/like/" + UUID.randomUUID();
            String actorUri = baseUrl + "/users/" + user.getUsername();

            Map<String, Object> likeActivity = new HashMap<>();
            likeActivity.put("type", "Like");
            likeActivity.put("id", likeId);
            likeActivity.put("actor", actorUri);
            likeActivity.put("object", activityUri);

            federationService.sendUndoActivity(likeId, likeActivity, user);
        }

        return ResponseEntity.noContent().build();
    }
}
