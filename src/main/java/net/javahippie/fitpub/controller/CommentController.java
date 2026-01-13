package net.javahippie.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.dto.CommentCreateRequest;
import net.javahippie.fitpub.model.dto.CommentDTO;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.Comment;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.ActivityRepository;
import net.javahippie.fitpub.repository.CommentRepository;
import net.javahippie.fitpub.repository.UserRepository;
import net.javahippie.fitpub.service.FederationService;
import net.javahippie.fitpub.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

/**
 * REST controller for comment operations.
 */
@RestController
@RequestMapping("/api/activities/{activityId}/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentRepository commentRepository;
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
     * Get all comments for an activity.
     *
     * @param activityId the activity ID
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @param userDetails the authenticated user (optional)
     * @return page of comments
     */
    @GetMapping
    public ResponseEntity<Page<CommentDTO>> getComments(
        @PathVariable UUID activityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        // Check if activity exists
        if (!activityRepository.existsById(activityId)) {
            return ResponseEntity.notFound().build();
        }

        UUID currentUserId = null;
        if (userDetails != null) {
            User user = getUser(userDetails);
            currentUserId = user.getId();
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> comments = commentRepository.findByActivityIdAndNotDeleted(activityId, pageable);

        UUID finalCurrentUserId = currentUserId;
        Page<CommentDTO> commentDTOs = comments.map(comment ->
            CommentDTO.fromEntity(comment, baseUrl, finalCurrentUserId)
        );

        return ResponseEntity.ok(commentDTOs);
    }

    /**
     * Create a comment on an activity.
     *
     * @param activityId the activity ID
     * @param request the comment create request
     * @param userDetails the authenticated user
     * @return the created comment
     */
    @PostMapping
    @Transactional
    public ResponseEntity<CommentDTO> createComment(
        @PathVariable UUID activityId,
        @Valid @RequestBody CommentCreateRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);

        // Check if activity exists
        Activity activity = activityRepository.findById(activityId)
            .orElse(null);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Create comment
        Comment comment = Comment.builder()
            .activityId(activityId)
            .userId(user.getId())
            .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
            .avatarUrl(user.getAvatarUrl())
            .content(request.getContent().trim())
            .build();

        Comment saved = commentRepository.save(comment);
        commentRepository.flush(); // Ensure @CreationTimestamp is applied

        log.info("User {} commented on activity {}", user.getUsername(), activityId);

        // Create notification for activity owner
        String commenterActorUri = user.getActorUri(baseUrl);
        notificationService.createActivityCommentedNotification(activity, saved, commenterActorUri);

        // Send ActivityPub Create/Note activity to followers if activity is public
        if (activity.getVisibility() == Activity.Visibility.PUBLIC ||
            activity.getVisibility() == Activity.Visibility.FOLLOWERS) {

            String commentUri = baseUrl + "/activities/" + activityId + "/comments/" + saved.getId();
            String activityUri = baseUrl + "/activities/" + activityId;
            String actorUri = baseUrl + "/users/" + user.getUsername();

            // Create Note object for the comment
            Map<String, Object> noteObject = new HashMap<>();
            noteObject.put("id", commentUri);
            noteObject.put("type", "Note");
            noteObject.put("attributedTo", actorUri);
            noteObject.put("inReplyTo", activityUri);
            noteObject.put("content", escapeHtml(saved.getContent()));
            noteObject.put("published", java.time.Instant.now().toString());

            if (activity.getVisibility() == Activity.Visibility.PUBLIC) {
                noteObject.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
                noteObject.put("cc", List.of(actorUri + "/followers"));
            } else {
                noteObject.put("to", List.of(actorUri + "/followers"));
            }

            // Send Create activity
            federationService.sendCreateActivity(commentUri, noteObject, user,
                activity.getVisibility() == Activity.Visibility.PUBLIC);

            log.info("Sent comment federation for comment {} on activity {}", saved.getId(), activityId);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommentDTO.fromEntity(saved, baseUrl, user.getId()));
    }

    /**
     * Delete a comment.
     *
     * @param activityId the activity ID
     * @param commentId the comment ID
     * @param userDetails the authenticated user
     * @return no content
     */
    @DeleteMapping("/{commentId}")
    @Transactional
    public ResponseEntity<Void> deleteComment(
        @PathVariable UUID activityId,
        @PathVariable UUID commentId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);

        // Find comment
        Comment comment = commentRepository.findById(commentId)
            .orElse(null);

        if (comment == null || !comment.getActivityId().equals(activityId)) {
            return ResponseEntity.notFound().build();
        }

        // Check ownership
        if (!comment.getUserId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Soft delete
        comment.setDeleted(true);
        commentRepository.save(comment);

        log.info("User {} deleted comment {}", user.getUsername(), commentId);

        // TODO: Send ActivityPub Delete activity to followers if activity is public

        return ResponseEntity.noContent().build();
    }

    /**
     * Escape HTML entities in text.
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
