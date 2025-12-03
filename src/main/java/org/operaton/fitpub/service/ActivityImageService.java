package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.util.ActivityFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating activity preview images for ActivityPub federation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityImageService {

    private final OsmTileRenderer osmTileRenderer;

    @Value("${fitpub.storage.images.path:${java.io.tmpdir}/fitpub/images}")
    private String imagesPath;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    @Value("${fitpub.image.osm-tiles.enabled:true}")
    private boolean osmTilesEnabled;

    /**
     * Generate a preview image for an activity showing the track outline and metadata.
     *
     * @param activity the activity to generate an image for
     * @return the URL of the generated image
     */
    public String generateActivityImage(Activity activity) {
        try {
            // Image dimensions
            int width = 1200;
            int height = 630; // Open Graph standard size

            // Create image
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // Enable antialiasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Calculate bounds once for both map tiles and track rendering
            TrackBounds trackBounds = null;

            // Render background - either OSM tiles or dark background
            if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
                trackBounds = calculateTrackBounds(activity);
            }

            if (osmTilesEnabled && trackBounds != null) {
                try {
                    // Render OSM tiles for left 60% of image (track area)
                    int trackWidth = (int) (width * 0.6);
                    BufferedImage mapTiles = osmTileRenderer.renderMapWithTiles(
                            trackBounds.minLat, trackBounds.maxLat,
                            trackBounds.minLon, trackBounds.maxLon,
                            trackWidth, height);
                    g2d.drawImage(mapTiles, 0, 0, null);

                    // Dark background for metadata area (right 40%)
                    g2d.setColor(new Color(30, 30, 30));
                    g2d.fillRect(trackWidth, 0, width - trackWidth, height);

                    log.debug("Rendered OSM tiles for activity {}", activity.getId());
                } catch (Exception e) {
                    log.warn("Failed to render OSM tiles, using dark background: {}", e.getMessage());
                    // Fallback to dark background
                    g2d.setColor(new Color(30, 30, 30));
                    g2d.fillRect(0, 0, width, height);
                }
            } else {
                // OSM tiles disabled or no track data - use dark background
                g2d.setColor(new Color(30, 30, 30));
                g2d.fillRect(0, 0, width, height);
            }

            // Draw track if available
            if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
                drawTrack(g2d, activity, width, height);
            } else if (activity.getSimplifiedTrack() != null) {
                drawSimplifiedTrack(g2d, activity, width, height);
            }

            // Draw metadata overlay
            drawMetadata(g2d, activity, width, height);

            g2d.dispose();

            // Save image
            File imagesDir = new File(imagesPath);
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            String filename = activity.getId() + ".png";
            File imageFile = new File(imagesDir, filename);
            ImageIO.write(image, "png", imageFile);

            log.info("Generated activity image: {}", imageFile.getAbsolutePath());

            // Return URL to the image
            return baseUrl + "/api/activities/" + activity.getId() + "/image";

        } catch (Exception e) {
            log.error("Failed to generate activity image for {}", activity.getId(), e);
            return null;
        }
    }

    /**
     * Draw the track outline from high-resolution track points with privacy protection.
     * Fades in/out the first and last 100 meters, completely hides first/last 100m.
     */
    private void drawTrack(Graphics2D g2d, Activity activity, int width, int height) {
        List<Map<String, Object>> trackPoints = parseTrackPoints(activity.getTrackPointsJson());
        if (trackPoints == null || trackPoints.isEmpty()) {
            return;
        }

        // Calculate cumulative distances along the track
        double[] cumulativeDistances = calculateCumulativeDistances(trackPoints);
        double totalDistance = cumulativeDistances[cumulativeDistances.length - 1];

        // Calculate bounds with padding (must match OSM tile rendering)
        TrackBounds bounds = calculateTrackBounds(activity);
        if (bounds == null) {
            return;
        }

        // Calculate scale (use left 60% of image for track, right 40% for metadata)
        int trackWidth = (int) (width * 0.6);
        int trackHeight = height;
        double scaleX = trackWidth / (bounds.maxLon - bounds.minLon);
        double scaleY = trackHeight / (bounds.maxLat - bounds.minLat);
        double scale = Math.min(scaleX, scaleY);

        // Draw track segments with privacy fade
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        final double HIDDEN_DISTANCE = 100.0; // First/last 100m completely hidden
        final double FADE_DISTANCE = 200.0;   // Fade zone from 100m to 200m

        for (int i = 0; i < trackPoints.size() - 1; i++) {
            Map<String, Object> point1 = trackPoints.get(i);
            Map<String, Object> point2 = trackPoints.get(i + 1);

            Double lat1 = getDouble(point1, "latitude");
            Double lon1 = getDouble(point1, "longitude");
            Double lat2 = getDouble(point2, "latitude");
            Double lon2 = getDouble(point2, "longitude");

            if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
                double x1 = (lon1 - bounds.minLon) * scale;
                double y1 = trackHeight - (lat1 - bounds.minLat) * scale;
                double x2 = (lon2 - bounds.minLon) * scale;
                double y2 = trackHeight - (lat2 - bounds.minLat) * scale;

                // Calculate opacity based on distance from start/end
                double distanceFromStart = cumulativeDistances[i];
                double distanceFromEnd = totalDistance - cumulativeDistances[i];

                // Calculate fade opacity (0.0 to 1.0)
                float opacity = 1.0f;

                // Hide first 100m completely, fade in from 100m to 200m
                if (distanceFromStart < HIDDEN_DISTANCE) {
                    opacity = 0.0f;
                } else if (distanceFromStart < FADE_DISTANCE) {
                    // Fade in from 100m to 200m
                    opacity = Math.min(opacity, (float) ((distanceFromStart - HIDDEN_DISTANCE) / (FADE_DISTANCE - HIDDEN_DISTANCE)));
                }

                // Hide last 100m completely, fade out from 200m to 100m before end
                if (distanceFromEnd < HIDDEN_DISTANCE) {
                    opacity = 0.0f;
                } else if (distanceFromEnd < FADE_DISTANCE) {
                    // Fade out from 200m to 100m before end
                    opacity = Math.min(opacity, (float) ((distanceFromEnd - HIDDEN_DISTANCE) / (FADE_DISTANCE - HIDDEN_DISTANCE)));
                }

                // Skip completely transparent segments
                if (opacity <= 0.0f) {
                    continue;
                }

                // Apply opacity to track color
                int alpha = Math.max(0, Math.min(255, (int) (opacity * 255)));
                g2d.setColor(new Color(0, 180, 216, alpha)); // Bright blue with alpha

                // Draw segment
                g2d.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            }
        }
    }

    /**
     * Draw the track from simplified track (LineString).
     */
    private void drawSimplifiedTrack(Graphics2D g2d, Activity activity, int width, int height) {
        // Similar logic but using simplified track coordinates
        // This is a fallback if high-res track points aren't available
        log.debug("Using simplified track for activity {}", activity.getId());
        // TODO: Implement if needed
    }

    /**
     * Calculate cumulative distances along the track using Haversine formula.
     * Returns an array where each element is the total distance from the start to that point.
     */
    private double[] calculateCumulativeDistances(List<Map<String, Object>> trackPoints) {
        double[] distances = new double[trackPoints.size()];
        distances[0] = 0.0;

        for (int i = 1; i < trackPoints.size(); i++) {
            Map<String, Object> point1 = trackPoints.get(i - 1);
            Map<String, Object> point2 = trackPoints.get(i);

            Double lat1 = getDouble(point1, "latitude");
            Double lon1 = getDouble(point1, "longitude");
            Double lat2 = getDouble(point2, "latitude");
            Double lon2 = getDouble(point2, "longitude");

            if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
                double segmentDistance = haversineDistance(lat1, lon1, lat2, lon2);
                distances[i] = distances[i - 1] + segmentDistance;
            } else {
                distances[i] = distances[i - 1];
            }
        }

        return distances;
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula.
     * Returns distance in meters.
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371000.0; // Earth radius in meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Draw metadata overlay on the right side of the image.
     */
    private void drawMetadata(Graphics2D g2d, Activity activity, int width, int height) {
        int metadataX = (int) (width * 0.62); // Start at 62% to leave some margin
        int y = 60;
        int lineHeight = 50;

        // Title
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        String title = activity.getTitle() != null ? activity.getTitle() : "Activity";
        if (title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        g2d.drawString(title, metadataX, y);
        y += lineHeight + 10;

        // Activity type
        g2d.setFont(new Font("Arial", Font.PLAIN, 24));
        g2d.setColor(new Color(200, 200, 200));
        String formattedType = ActivityFormatter.formatActivityType(activity.getActivityType());
        g2d.drawString(formattedType, metadataX, y);
        y += lineHeight;

        // Distance
        if (activity.getTotalDistance() != null) {
            g2d.setFont(new Font("Arial", Font.BOLD, 28));
            g2d.setColor(Color.WHITE);
            String distance = String.format("%.2f km", activity.getTotalDistance().doubleValue() / 1000.0);
            g2d.drawString(distance, metadataX, y);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.setColor(new Color(150, 150, 150));
            g2d.drawString("Distance", metadataX, y + 25);
            y += lineHeight + 30;
        }

        // Duration
        if (activity.getTotalDurationSeconds() != null) {
            long hours = activity.getTotalDurationSeconds() / 3600;
            long minutes = (activity.getTotalDurationSeconds() % 3600) / 60;
            long seconds = activity.getTotalDurationSeconds() % 60;

            g2d.setFont(new Font("Arial", Font.BOLD, 28));
            g2d.setColor(Color.WHITE);
            String duration;
            if (hours > 0) {
                duration = String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                duration = String.format("%d:%02d", minutes, seconds);
            }
            g2d.drawString(duration, metadataX, y);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.setColor(new Color(150, 150, 150));
            g2d.drawString("Duration", metadataX, y + 25);
            y += lineHeight + 30;
        }

        // Elevation gain
        if (activity.getElevationGain() != null) {
            g2d.setFont(new Font("Arial", Font.BOLD, 28));
            g2d.setColor(Color.WHITE);
            String elevation = String.format("%.0f m", activity.getElevationGain());
            g2d.drawString(elevation, metadataX, y);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.setColor(new Color(150, 150, 150));
            g2d.drawString("Elevation Gain", metadataX, y + 25);
            y += lineHeight + 30;
        }

        // Branding
        g2d.setFont(new Font("Arial", Font.PLAIN, 20));
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawString("FitPub", metadataX, height - 40);
    }

    /**
     * Helper to safely extract Double from Map.
     */
    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    /**
     * Get the file path for an activity image.
     */
    public File getActivityImageFile(UUID activityId) {
        return new File(imagesPath, activityId + ".png");
    }

    /**
     * Parses track points from JSONB string.
     */
    private List<Map<String, Object>> parseTrackPoints(String trackPointsJson) {
        if (trackPointsJson == null || trackPointsJson.isEmpty()) {
            return null;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(trackPointsJson);

            if (root.isArray()) {
                List<Map<String, Object>> trackPoints = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    Map<String, Object> point = new java.util.LinkedHashMap<>();

                    if (node.has("latitude")) point.put("latitude", node.get("latitude").asDouble());
                    if (node.has("longitude")) point.put("longitude", node.get("longitude").asDouble());
                    if (node.has("elevation")) point.put("elevation", node.get("elevation").asDouble());

                    trackPoints.add(point);
                }
                return trackPoints;
            }
        } catch (Exception e) {
            log.error("Error parsing track points JSON: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Calculate and cache track bounds with padding for consistent rendering.
     */
    private TrackBounds calculateTrackBounds(Activity activity) {
        List<Map<String, Object>> trackPoints = parseTrackPoints(activity.getTrackPointsJson());
        if (trackPoints == null || trackPoints.isEmpty()) {
            return null;
        }

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (Map<String, Object> point : trackPoints) {
            Double lat = getDouble(point, "latitude");
            Double lon = getDouble(point, "longitude");
            if (lat != null && lon != null) {
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
                minLon = Math.min(minLon, lon);
                maxLon = Math.max(maxLon, lon);
            }
        }

        // Add padding
        double latRange = maxLat - minLat;
        double lonRange = maxLon - minLon;
        double padding = 0.1; // 10% padding
        minLat -= latRange * padding;
        maxLat += latRange * padding;
        minLon -= lonRange * padding;
        maxLon += lonRange * padding;

        return new TrackBounds(minLat, maxLat, minLon, maxLon);
    }

    /**
     * Helper class to store track geographic bounds.
     */
    private static class TrackBounds {
        final double minLat;
        final double maxLat;
        final double minLon;
        final double maxLon;

        TrackBounds(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }
    }
}
