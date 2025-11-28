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
     * Lists all activities for the authenticated user.
     *
     * @param userDetails the authenticated user
     * @return list of activities
     */
    @GetMapping
    public ResponseEntity<List<ActivityDTO>> getUserActivities(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} retrieving activities", userDetails.getUsername());

        UUID userId = getUserId(userDetails);

        List<Activity> activities = fitFileService.getUserActivities(userId);
        List<ActivityDTO> dtos = activities.stream()
            .map(ActivityDTO::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
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
}
