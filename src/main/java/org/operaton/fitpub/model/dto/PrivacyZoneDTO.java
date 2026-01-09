package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.PrivacyZone;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for PrivacyZone data transfer.
 * Returns lat/lon as separate fields extracted from PostGIS Point geometry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyZoneDTO {

    private UUID id;
    private UUID userId;
    private String name;
    private String description;
    private Double latitude;
    private Double longitude;
    private Integer radiusMeters;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Creates a DTO from a PrivacyZone entity.
     * Extracts latitude and longitude from PostGIS Point geometry.
     */
    public static PrivacyZoneDTO fromEntity(PrivacyZone zone) {
        return PrivacyZoneDTO.builder()
            .id(zone.getId())
            .userId(zone.getUserId())
            .name(zone.getName())
            .description(zone.getDescription())
            .latitude(zone.getCenterPoint().getY())
            .longitude(zone.getCenterPoint().getX())
            .radiusMeters(zone.getRadiusMeters())
            .isActive(zone.getIsActive())
            .createdAt(zone.getCreatedAt())
            .updatedAt(zone.getUpdatedAt())
            .build();
    }
}
