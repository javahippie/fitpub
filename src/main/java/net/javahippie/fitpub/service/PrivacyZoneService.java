package net.javahippie.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import net.javahippie.fitpub.model.entity.PrivacyZone;
import net.javahippie.fitpub.repository.PrivacyZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing GPS privacy zones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrivacyZoneService {

    private final PrivacyZoneRepository privacyZoneRepository;

    private static final int SRID = 4326; // WGS84
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    /**
     * Create a new privacy zone for a user.
     *
     * @param userId user ID
     * @param name zone name
     * @param description zone description (optional)
     * @param latitude center latitude
     * @param longitude center longitude
     * @param radiusMeters radius in meters
     * @return created privacy zone
     */
    @Transactional
    public PrivacyZone createPrivacyZone(
        UUID userId,
        String name,
        String description,
        double latitude,
        double longitude,
        int radiusMeters
    ) {
        log.info("Creating privacy zone for user {} at ({}, {}) with radius {}m",
                 userId, latitude, longitude, radiusMeters);

        // Validate inputs
        if (radiusMeters <= 0 || radiusMeters > 10000) {
            throw new IllegalArgumentException("Radius must be between 1 and 10000 meters");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Invalid latitude");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude");
        }

        Point centerPoint = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        PrivacyZone zone = PrivacyZone.builder()
            .userId(userId)
            .name(name)
            .description(description)
            .centerPoint(centerPoint)
            .radiusMeters(radiusMeters)
            .isActive(true)
            .build();

        return privacyZoneRepository.save(zone);
    }

    /**
     * Get all privacy zones for a user (active and inactive).
     *
     * @param userId user ID
     * @return list of privacy zones
     */
    @Transactional(readOnly = true)
    public List<PrivacyZone> getUserPrivacyZones(UUID userId) {
        return privacyZoneRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get all active privacy zones for a user.
     *
     * @param userId user ID
     * @return list of active privacy zones
     */
    @Transactional(readOnly = true)
    public List<PrivacyZone> getActivePrivacyZones(UUID userId) {
        return privacyZoneRepository.findByUserIdAndIsActiveTrue(userId);
    }

    /**
     * Update a privacy zone.
     *
     * @param zoneId zone ID
     * @param userId user ID (for authorization)
     * @param name new name
     * @param description new description
     * @param latitude new latitude
     * @param longitude new longitude
     * @param radiusMeters new radius
     * @return updated privacy zone
     */
    @Transactional
    public PrivacyZone updatePrivacyZone(
        UUID zoneId,
        UUID userId,
        String name,
        String description,
        double latitude,
        double longitude,
        int radiusMeters
    ) {
        PrivacyZone zone = privacyZoneRepository.findById(zoneId)
            .orElseThrow(() -> new IllegalArgumentException("Privacy zone not found"));

        if (!zone.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to update this privacy zone");
        }

        // Validate
        if (radiusMeters <= 0 || radiusMeters > 10000) {
            throw new IllegalArgumentException("Radius must be between 1 and 10000 meters");
        }

        Point centerPoint = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        zone.setName(name);
        zone.setDescription(description);
        zone.setCenterPoint(centerPoint);
        zone.setRadiusMeters(radiusMeters);

        return privacyZoneRepository.save(zone);
    }

    /**
     * Toggle a privacy zone's active status.
     *
     * @param zoneId zone ID
     * @param userId user ID (for authorization)
     * @param isActive new active status
     * @return updated privacy zone
     */
    @Transactional
    public PrivacyZone togglePrivacyZone(UUID zoneId, UUID userId, boolean isActive) {
        PrivacyZone zone = privacyZoneRepository.findById(zoneId)
            .orElseThrow(() -> new IllegalArgumentException("Privacy zone not found"));

        if (!zone.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to update this privacy zone");
        }

        zone.setIsActive(isActive);
        return privacyZoneRepository.save(zone);
    }

    /**
     * Delete a privacy zone.
     *
     * @param zoneId zone ID
     * @param userId user ID (for authorization)
     */
    @Transactional
    public void deletePrivacyZone(UUID zoneId, UUID userId) {
        PrivacyZone zone = privacyZoneRepository.findById(zoneId)
            .orElseThrow(() -> new IllegalArgumentException("Privacy zone not found"));

        if (!zone.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to delete this privacy zone");
        }

        privacyZoneRepository.delete(zone);
        log.info("Deleted privacy zone {} for user {}", zoneId, userId);
    }

    /**
     * Check if a GPS point falls within any of the user's active privacy zones.
     *
     * @param userId user ID
     * @param latitude point latitude
     * @param longitude point longitude
     * @return true if point is within any active privacy zone
     */
    @Transactional(readOnly = true)
    public boolean isPointInPrivacyZone(UUID userId, double latitude, double longitude) {
        return privacyZoneRepository.isPointInAnyZone(userId, latitude, longitude);
    }
}
