package net.javahippie.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.javahippie.fitpub.model.entity.UserHeatmapGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for heatmap data in GeoJSON-compatible format.
 * Used by frontend to render heatmap with Leaflet.heat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapDataDTO {

    @Builder.Default
    private String type = "FeatureCollection";
    private List<Feature> features;
    private Integer maxIntensity;
    private Long activityCount;  // Total number of activities user has

    /**
     * GeoJSON Feature representing a heatmap grid cell.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Feature {
        @Builder.Default
        private String type = "Feature";
        private Geometry geometry;
        private Properties properties;
    }

    /**
     * GeoJSON Point geometry.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Geometry {
        @Builder.Default
        private String type = "Point";
        private double[] coordinates; // [lon, lat]
    }

    /**
     * Feature properties containing intensity.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Properties {
        private Integer intensity;
    }

    /**
     * Convert list of UserHeatmapGrid entities to HeatmapDataDTO.
     *
     * @param gridCells list of grid cells
     * @param maxIntensity maximum intensity for normalization
     * @return HeatmapDataDTO
     */
    public static HeatmapDataDTO fromGridCells(List<UserHeatmapGrid> gridCells, Integer maxIntensity) {
        List<Feature> features = new ArrayList<>();

        for (UserHeatmapGrid cell : gridCells) {
            // Round coordinates to 6 decimal places (~11cm precision)
            // This significantly reduces JSON size while maintaining visual accuracy
            double lon = round(cell.getGridCell().getX(), 6);
            double lat = round(cell.getGridCell().getY(), 6);

            Feature feature = Feature.builder()
                    .type("Feature")
                    .geometry(Geometry.builder()
                            .type("Point")
                            .coordinates(new double[]{lon, lat})
                            .build())
                    .properties(Properties.builder()
                            .intensity(cell.getPointCount())
                            .build())
                    .build();

            features.add(feature);
        }

        return HeatmapDataDTO.builder()
                .type("FeatureCollection")
                .features(features)
                .maxIntensity(maxIntensity)
                .build();
    }

    /**
     * Round a double value to specified number of decimal places.
     *
     * @param value the value to round
     * @param places number of decimal places
     * @return rounded value
     */
    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
