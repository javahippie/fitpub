package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a user-defined privacy zone for GPS track filtering.
 * When activities pass through privacy zones, GPS coordinates within the zone are removed.
 */
@Entity
@Table(name = "privacy_zones", indexes = {
    @Index(name = "idx_privacy_zones_user", columnList = "user_id, is_active"),
    @Index(name = "idx_privacy_zones_center_point", columnList = "center_point")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivacyZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * True center point of the privacy zone (PostGIS Point geometry).
     * This is NOT the randomized display center shown in the UI.
     */
    @Column(name = "center_point", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point centerPoint;

    /**
     * Radius of the privacy zone in meters.
     * All GPS points within this distance from centerPoint will be filtered.
     */
    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    /**
     * Whether this privacy zone is currently active.
     * Inactive zones are ignored during filtering.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
