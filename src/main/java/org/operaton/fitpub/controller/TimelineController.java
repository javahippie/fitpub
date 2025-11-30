package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.TimelineActivityDTO;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.TimelineService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for timeline endpoints.
 * Provides access to federated, public, and user timelines.
 */
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
@Slf4j
public class TimelineController {

    private final TimelineService timelineService;
    private final UserRepository userRepository;

    /**
     * Helper method to get user ID from authenticated UserDetails.
     *
     * @param userDetails the authenticated user details
     * @return the user's UUID
     * @throws UsernameNotFoundException if user not found
     */
    private UUID getUserId(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));
        return user.getId();
    }

    /**
     * Get the federated timeline for the authenticated user.
     * Shows activities from users they follow.
     *
     * GET /api/timeline/federated?page=0&size=20
     *
     * @param userDetails the authenticated user details
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/federated")
    public ResponseEntity<Page<TimelineActivityDTO>> getFederatedTimeline(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = getUserId(userDetails);
        log.debug("Federated timeline request from user: {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<TimelineActivityDTO> timeline = timelineService.getFederatedTimeline(userId, pageable);

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get the public timeline.
     * Shows all public activities from all users.
     * Optionally authenticated - if user is logged in, will show which activities they've liked.
     *
     * GET /api/timeline/public?page=0&size=20
     *
     * @param userDetails the authenticated user details (optional)
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/public")
    public ResponseEntity<Page<TimelineActivityDTO>> getPublicTimeline(
        @AuthenticationPrincipal(errorOnInvalidType = false) UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = null;
        if (userDetails != null) {
            userId = getUserId(userDetails);
            log.debug("Public timeline request from authenticated user: {}", userId);
        } else {
            log.debug("Public timeline request (unauthenticated)");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<TimelineActivityDTO> timeline = timelineService.getPublicTimeline(userId, pageable);

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get the user's own timeline.
     * Shows only activities by the authenticated user.
     *
     * GET /api/timeline/user?page=0&size=20
     *
     * @param userDetails the authenticated user details
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/user")
    public ResponseEntity<Page<TimelineActivityDTO>> getUserTimeline(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = getUserId(userDetails);
        log.debug("User timeline request from user: {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<TimelineActivityDTO> timeline = timelineService.getUserTimeline(userId, pageable);

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get another user's public timeline by username.
     * Shows public activities by a specific user.
     *
     * GET /api/timeline/user/{username}?page=0&size=20
     *
     * @param username the username
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<Page<TimelineActivityDTO>> getUserTimelineByUsername(
        @PathVariable String username,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("User timeline request for username: {}", username);

        // TODO: Implement getUserTimelineByUsername in TimelineService
        // For now, return not implemented
        return ResponseEntity.status(501).build();
    }
}
