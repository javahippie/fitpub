package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
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

    @Value("${fitpub.storage.images.path:${java.io.tmpdir}/fitpub/images}")
    private String imagesPath;

    @Value("${fitpub.base-url}")
    private String baseUrl;

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

            // Background
            g2d.setColor(new Color(30, 30, 30)); // Dark background
            g2d.fillRect(0, 0, width, height);

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
     * Draw the track outline from high-resolution track points.
     */
    private void drawTrack(Graphics2D g2d, Activity activity, int width, int height) {
        List<Map<String, Object>> trackPoints = parseTrackPoints(activity.getTrackPointsJson());
        if (trackPoints == null || trackPoints.isEmpty()) {
            return;
        }

        // Find bounds
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

        // Calculate scale (use left 60% of image for track, right 40% for metadata)
        int trackWidth = (int) (width * 0.6);
        int trackHeight = height;
        double scaleX = trackWidth / (maxLon - minLon);
        double scaleY = trackHeight / (maxLat - minLat);
        double scale = Math.min(scaleX, scaleY);

        // Create path
        Path2D.Double path = new Path2D.Double();
        boolean first = true;

        for (Map<String, Object> point : trackPoints) {
            Double lat = getDouble(point, "latitude");
            Double lon = getDouble(point, "longitude");
            if (lat != null && lon != null) {
                double x = (lon - minLon) * scale;
                double y = trackHeight - (lat - minLat) * scale; // Flip Y axis

                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
        }

        // Draw track
        g2d.setColor(new Color(0, 180, 216)); // Bright blue
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(path);

        // Draw start and end markers
        if (!trackPoints.isEmpty()) {
            Map<String, Object> firstPoint = trackPoints.get(0);
            Map<String, Object> lastPoint = trackPoints.get(trackPoints.size() - 1);

            drawMarker(g2d, firstPoint, minLat, minLon, scale, trackHeight, new Color(0, 255, 0)); // Green start
            drawMarker(g2d, lastPoint, minLat, minLon, scale, trackHeight, new Color(255, 0, 0)); // Red end
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
     * Draw a circular marker at a track point.
     */
    private void drawMarker(Graphics2D g2d, Map<String, Object> point, double minLat, double minLon,
                           double scale, int trackHeight, Color color) {
        Double lat = getDouble(point, "latitude");
        Double lon = getDouble(point, "longitude");
        if (lat != null && lon != null) {
            double x = (lon - minLon) * scale;
            double y = trackHeight - (lat - minLat) * scale;

            g2d.setColor(color);
            int markerSize = 12;
            g2d.fillOval((int) x - markerSize / 2, (int) y - markerSize / 2, markerSize, markerSize);

            // White outline
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawOval((int) x - markerSize / 2, (int) y - markerSize / 2, markerSize, markerSize);
        }
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
        g2d.drawString(activity.getActivityType().toString(), metadataX, y);
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
}
