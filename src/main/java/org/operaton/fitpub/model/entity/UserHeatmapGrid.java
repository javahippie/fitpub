package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User heatmap grid entity representing aggregated track point density.
 * Each row represents a grid cell with the count of track points that fall within it.
 * Used to efficiently render user activity heatmaps without processing all activities.
 */
@Entity
@Table(name = "user_heatmap_grid",
       uniqueConstraints = @UniqueConstraint(name = "unique_user_grid_cell", columnNames = {"user_id", "grid_cell"}),
       indexes = {
           @Index(name = "idx_user_heatmap_grid_user", columnList = "user_id"),
           @Index(name = "idx_user_heatmap_grid_updated", columnList = "last_updated")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserHeatmapGrid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * Center point of the grid cell.
     * Grid cells are ~10m x 10m (0.0001 degrees).
     */
    @Column(name = "grid_cell", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point gridCell;

    /**
     * Number of track points that fall within this grid cell.
     * Higher counts indicate more frequently visited areas.
     */
    @Column(name = "point_count", nullable = false)
    @Builder.Default
    private Integer pointCount = 0;

    @Column(name = "last_updated", nullable = false)
    @UpdateTimestamp
    private LocalDateTime lastUpdated;
}
