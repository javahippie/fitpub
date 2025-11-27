package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.ActivityDTO;
import org.operaton.fitpub.model.dto.ActivityUpdateRequest;
import org.operaton.fitpub.model.dto.ActivityUploadRequest;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.service.FitFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

        // TODO: Get actual user ID from UserDetails
        UUID userId = UUID.randomUUID(); // Temporary - will be replaced with actual user lookup

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
        // TODO: Get actual user ID from UserDetails
        UUID userId = UUID.randomUUID(); // Temporary

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

        // TODO: Get actual user ID from UserDetails
        UUID userId = UUID.randomUUID(); // Temporary

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

        // TODO: Get actual user ID from UserDetails
        UUID userId = UUID.randomUUID(); // Temporary

        Activity activity = fitFileService.getActivity(id, userId);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        // Update fields
        activity.setTitle(request.getTitle());
        activity.setDescription(request.getDescription());
        activity.setVisibility(request.getVisibility());

        // TODO: Add update method to FitFileService
        // Activity updated = fitFileService.updateActivity(activity);

        ActivityDTO dto = ActivityDTO.fromEntity(activity);
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

        // TODO: Get actual user ID from UserDetails
        UUID userId = UUID.randomUUID(); // Temporary

        boolean deleted = fitFileService.deleteActivity(id, userId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
