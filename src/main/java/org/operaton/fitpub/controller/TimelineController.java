package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.TimelineActivityDTO;
import org.operaton.fitpub.service.TimelineService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    /**
     * Get the federated timeline for the authenticated user.
     * Shows activities from users they follow.
     *
     * GET /api/timeline/federated?page=0&size=20
     *
     * @param authentication the authenticated user
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/federated")
    public ResponseEntity<Page<TimelineActivityDTO>> getFederatedTimeline(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        log.debug("Federated timeline request from user: {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<TimelineActivityDTO> timeline = timelineService.getFederatedTimeline(userId, pageable);

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get the public timeline.
     * Shows all public activities from all users.
     *
     * GET /api/timeline/public?page=0&size=20
     *
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/public")
    public ResponseEntity<Page<TimelineActivityDTO>> getPublicTimeline(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("Public timeline request");

        Pageable pageable = PageRequest.of(page, size);
        Page<TimelineActivityDTO> timeline = timelineService.getPublicTimeline(pageable);

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get the user's own timeline.
     * Shows only activities by the authenticated user.
     *
     * GET /api/timeline/user?page=0&size=20
     *
     * @param authentication the authenticated user
     * @param page page number (default: 0)
     * @param size page size (default: 20)
     * @return page of timeline activities
     */
    @GetMapping("/user")
    public ResponseEntity<Page<TimelineActivityDTO>> getUserTimeline(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(authentication.getName());
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
