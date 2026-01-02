package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.HeatmapDataDTO;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.model.entity.UserHeatmapGrid;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.HeatmapGridService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user activity heatmap data.
 */
@RestController
@RequestMapping("/api/heatmap")
@RequiredArgsConstructor
@Slf4j
public class HeatmapController {

    private final HeatmapGridService heatmapGridService;
    private final UserRepository userRepository;

    /**
     * Get heatmap data for the authenticated user.
     * Optionally filtered by bounding box (viewport).
     *
     * @param userDetails authenticated user
     * @param minLon minimum longitude (optional)
     * @param minLat minimum latitude (optional)
     * @param maxLon maximum longitude (optional)
     * @param maxLat maximum latitude (optional)
     * @return heatmap data in GeoJSON format
     */
    @GetMapping("/me")
    public ResponseEntity<HeatmapDataDTO> getMyHeatmap(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Double maxLat) {

        log.debug("User {} requesting heatmap data", userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<UserHeatmapGrid> gridCells = heatmapGridService.getUserHeatmapData(
                user.getId(), minLon, minLat, maxLon, maxLat);

        Integer maxIntensity = heatmapGridService.getMaxPointCount(user.getId());

        HeatmapDataDTO heatmapData = HeatmapDataDTO.fromGridCells(gridCells, maxIntensity);

        log.debug("Returning {} grid cells for user {}", gridCells.size(), userDetails.getUsername());

        return ResponseEntity.ok(heatmapData);
    }

    /**
     * Get heatmap data for a specific user by username.
     * Only returns data for public activities.
     *
     * @param username the username
     * @param minLon minimum longitude (optional)
     * @param minLat minimum latitude (optional)
     * @param maxLon maximum longitude (optional)
     * @param maxLat maximum latitude (optional)
     * @return heatmap data in GeoJSON format
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<HeatmapDataDTO> getUserHeatmap(
            @PathVariable String username,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Double maxLat) {

        log.debug("Requesting heatmap data for user {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<UserHeatmapGrid> gridCells = heatmapGridService.getUserHeatmapData(
                user.getId(), minLon, minLat, maxLon, maxLat);

        Integer maxIntensity = heatmapGridService.getMaxPointCount(user.getId());

        HeatmapDataDTO heatmapData = HeatmapDataDTO.fromGridCells(gridCells, maxIntensity);

        log.debug("Returning {} grid cells for user {}", gridCells.size(), username);

        return ResponseEntity.ok(heatmapData);
    }

    /**
     * Rebuild heatmap for the authenticated user.
     * This triggers a full recalculation of the user's heatmap grid from all their activities.
     *
     * @param userDetails authenticated user
     * @return success message
     */
    @PostMapping("/me/rebuild")
    public ResponseEntity<?> rebuildMyHeatmap(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("User {} requested heatmap rebuild", userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        try {
            heatmapGridService.recalculateUserHeatmap(user);
            log.info("Heatmap rebuild completed successfully for user {}", userDetails.getUsername());
            return ResponseEntity.ok().body(new RebuildResponse("Heatmap rebuilt successfully"));
        } catch (Exception e) {
            log.error("Failed to rebuild heatmap for user {}", userDetails.getUsername(), e);
            return ResponseEntity.internalServerError()
                    .body(new RebuildResponse("Failed to rebuild heatmap: " + e.getMessage()));
        }
    }

    /**
     * Simple response DTO for rebuild endpoint
     */
    private record RebuildResponse(String message) {}
}
