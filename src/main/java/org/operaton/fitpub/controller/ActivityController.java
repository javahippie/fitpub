package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.ActivityDTO;
import org.operaton.fitpub.model.dto.ActivityUpdateRequest;
import org.operaton.fitpub.model.dto.ActivityUploadRequest;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.FitFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for activity management.
 * Handles FIT file uploads, activity retrieval, updates, and deletion.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final FitFileService fitFileService;
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
     * Uploads a FIT file and creates a new activity.
     *
     * @param file the FIT file
     * @param request the upload request with metadata
     * @param userDetails the authenticated user
     * @return the created activity
     */
    @PostMapping("/upload")
    public ResponseEntity<ActivityDTO> uploadActivity(
        @RequestParam("file") MultipartFile file,
        @Valid @ModelAttribute ActivityUploadRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} uploading FIT file: {}", userDetails.getUsername(), file.getOriginalFilename());

        UUID userId = getUserId(userDetails);

        Activity activity = fitFileService.processFitFile(
            file,
            userId,
            request.getTitle(),
            request.getDescription(),
            request.getVisibility()
        );

        ActivityDTO dto = ActivityDTO.fromEntity(activity);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Retrieves an activity by ID.
     *
     * @param id the activity ID
     * @param userDetails the authenticated user
     * @return the activity
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActivityDTO> getActivity(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = getUserId(userDetails);

        Activity activity = fitFileService.getActivity(id, userId);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        ActivityDTO dto = ActivityDTO.fromEntity(activity);
        return ResponseEntity.ok(dto);
    }

    /**
     * Lists all activities for the authenticated user with pagination.
     *
     * @param userDetails the authenticated user
     * @param page page number (default: 0)
     * @param size page size (default: 10)
     * @return page of activities
     */
    @GetMapping
    public ResponseEntity<?> getUserActivities(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        log.info("User {} retrieving activities (page: {}, size: {})", userDetails.getUsername(), page, size);

        UUID userId = getUserId(userDetails);

        org.springframework.data.domain.Page<Activity> activityPage =
            fitFileService.getUserActivitiesPaginated(userId, page, size);

        // Convert to DTOs
        org.springframework.data.domain.Page<ActivityDTO> dtoPage = activityPage.map(ActivityDTO::fromEntity);

        // Return Spring Page object with all pagination metadata
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Updates activity metadata.
     *
     * @param id the activity ID
     * @param request the update request
     * @param userDetails the authenticated user
     * @return the updated activity
     */
    @PutMapping("/{id}")
    public ResponseEntity<ActivityDTO> updateActivity(
        @PathVariable UUID id,
        @Valid @RequestBody ActivityUpdateRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} updating activity {}", userDetails.getUsername(), id);

        UUID userId = getUserId(userDetails);

        Activity activity = fitFileService.getActivity(id, userId);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Update fields
        activity.setTitle(request.getTitle());
        activity.setDescription(request.getDescription());
        activity.setVisibility(request.getVisibility());

        Activity updated = fitFileService.updateActivity(activity);

        ActivityDTO dto = ActivityDTO.fromEntity(updated);
        return ResponseEntity.ok(dto);
    }

    /**
     * Deletes an activity.
     *
     * @param id the activity ID
     * @param userDetails the authenticated user
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} deleting activity {}", userDetails.getUsername(), id);

        UUID userId = getUserId(userDetails);

        boolean deleted = fitFileService.deleteActivity(id, userId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists public activities for a specific user by username.
     *
     * @param username the username
     * @param page page number (default: 0)
     * @param size page size (default: 10)
     * @return page of public activities
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<?> getUserPublicActivities(
        @PathVariable String username,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("Retrieving public activities for user: {}", username);

        // Get user by username
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Get public activities only
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("startedAt").descending());

        org.springframework.data.domain.Page<Activity> activityPage =
            fitFileService.getPublicActivitiesByUserId(user.getId(), pageable);

        // Convert to DTOs
        org.springframework.data.domain.Page<ActivityDTO> dtoPage = activityPage.map(ActivityDTO::fromEntity);

        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Gets the GPS track data for an activity in GeoJSON format.
     * Public activities can be accessed without authentication.
     * Private/followers activities require authentication and proper access.
     *
     * @param id the activity ID
     * @param userDetails the authenticated user (optional for public activities)
     * @return GeoJSON FeatureCollection with track data
     */
    @GetMapping("/{id}/track")
    public ResponseEntity<?> getActivityTrack(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.debug("Retrieving track data for activity {}", id);

        // First try to get the activity regardless of user
        Activity activity = fitFileService.getActivityById(id);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Check visibility and access permissions
        if (activity.getVisibility() != Activity.Visibility.PUBLIC) {
            // Non-public activities require authentication
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            UUID userId = getUserId(userDetails);

            // Check if user owns the activity
            if (!activity.getUserId().equals(userId)) {
                // TODO: Check if user is following the activity owner (for FOLLOWERS visibility)
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // Build GeoJSON FeatureCollection
        ActivityDTO dto = ActivityDTO.fromEntity(activity);

        if (dto.getSimplifiedTrack() == null) {
            // Return empty FeatureCollection if no track data
            return ResponseEntity.ok(java.util.Map.of(
                "type", "FeatureCollection",
                "features", java.util.List.of()
            ));
        }

        // Create GeoJSON Feature with the track
        java.util.Map<String, Object> feature = new java.util.LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", dto.getSimplifiedTrack());

        // Add properties
        java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("title", activity.getTitle());
        properties.put("activityType", activity.getActivityType().name());
        properties.put("distance", activity.getTotalDistance());
        properties.put("duration", activity.getTotalDurationSeconds());
        feature.put("properties", properties);

        // Create FeatureCollection
        java.util.Map<String, Object> geoJson = new java.util.LinkedHashMap<>();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("features", java.util.List.of(feature));

        return ResponseEntity.ok(geoJson);
    }
}
