package net.javahippie.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.dto.CreatePrivacyZoneRequest;
import net.javahippie.fitpub.model.dto.PrivacyZoneDTO;
import net.javahippie.fitpub.model.dto.UpdatePrivacyZoneRequest;
import net.javahippie.fitpub.model.entity.PrivacyZone;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.UserRepository;
import net.javahippie.fitpub.service.PrivacyZoneService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for privacy zone management.
 */
@RestController
@RequestMapping("/api/privacy-zones")
@RequiredArgsConstructor
@Slf4j
public class PrivacyZoneController {

    private final PrivacyZoneService privacyZoneService;
    private final UserRepository userRepository;

    /**
     * Get all privacy zones for the authenticated user.
     *
     * @param userDetails the authenticated user
     * @return list of privacy zones
     */
    @GetMapping
    public ResponseEntity<List<PrivacyZoneDTO>> getPrivacyZones(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("User {} retrieving privacy zones", userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<PrivacyZone> zones = privacyZoneService.getUserPrivacyZones(user.getId());
        List<PrivacyZoneDTO> zoneDTOs = zones.stream()
            .map(PrivacyZoneDTO::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(zoneDTOs);
    }

    /**
     * Create a new privacy zone.
     *
     * @param request the zone creation request
     * @param userDetails the authenticated user
     * @return created privacy zone
     */
    @PostMapping
    public ResponseEntity<PrivacyZoneDTO> createPrivacyZone(
        @Valid @RequestBody CreatePrivacyZoneRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} creating privacy zone: {}", userDetails.getUsername(), request.getName());

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        PrivacyZone zone = privacyZoneService.createPrivacyZone(
            user.getId(),
            request.getName(),
            request.getDescription(),
            request.getLatitude(),
            request.getLongitude(),
            request.getRadiusMeters()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(PrivacyZoneDTO.fromEntity(zone));
    }

    /**
     * Update an existing privacy zone.
     *
     * @param zoneId the zone ID
     * @param request the update request
     * @param userDetails the authenticated user
     * @return updated privacy zone
     */
    @PutMapping("/{zoneId}")
    public ResponseEntity<PrivacyZoneDTO> updatePrivacyZone(
        @PathVariable UUID zoneId,
        @Valid @RequestBody UpdatePrivacyZoneRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} updating privacy zone {}", userDetails.getUsername(), zoneId);

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        try {
            PrivacyZone zone = privacyZoneService.updatePrivacyZone(
                zoneId,
                user.getId(),
                request.getName(),
                request.getDescription(),
                request.getLatitude(),
                request.getLongitude(),
                request.getRadiusMeters()
            );

            return ResponseEntity.ok(PrivacyZoneDTO.fromEntity(zone));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Toggle a privacy zone's active status.
     *
     * @param zoneId the zone ID
     * @param body request body with isActive boolean
     * @param userDetails the authenticated user
     * @return updated privacy zone
     */
    @PatchMapping("/{zoneId}/toggle")
    public ResponseEntity<PrivacyZoneDTO> togglePrivacyZone(
        @PathVariable UUID zoneId,
        @RequestBody Map<String, Boolean> body,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} toggling privacy zone {}", userDetails.getUsername(), zoneId);

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Boolean isActive = body.get("isActive");
        if (isActive == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            PrivacyZone zone = privacyZoneService.togglePrivacyZone(zoneId, user.getId(), isActive);
            return ResponseEntity.ok(PrivacyZoneDTO.fromEntity(zone));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Delete a privacy zone.
     *
     * @param zoneId the zone ID
     * @param userDetails the authenticated user
     * @return no content on success
     */
    @DeleteMapping("/{zoneId}")
    public ResponseEntity<Void> deletePrivacyZone(
        @PathVariable UUID zoneId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} deleting privacy zone {}", userDetails.getUsername(), zoneId);

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        try {
            privacyZoneService.deletePrivacyZone(zoneId, user.getId());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
