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
            boolean isIndoorActivity = activity.getSimplifiedTrack() == null;

            // Render background - either OSM tiles or gradient background
            if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
                trackBounds = calculateTrackBounds(activity);
            }

            if (osmTilesEnabled && trackBounds != null && !isIndoorActivity) {
                try {
                    // Render OSM tiles for left 60% of image (track area)
                    int trackWidth = (int) (width * 0.6);
                    BufferedImage mapTiles = osmTileRenderer.renderMapWithTiles(
                            trackBounds.minLat, trackBounds.maxLat,
                            trackBounds.minLon, trackBounds.maxLon,
                            trackWidth, height);
                    g2d.drawImage(mapTiles, 0, 0, null);

                    // 80s Aerobic style gradient background for metadata area (right 40%)
                    int metadataX = trackWidth;
                    GradientPaint gradient = new GradientPaint(
                            metadataX, 0, new Color(26, 0, 51),  // Dark purple
                            width, height, new Color(45, 0, 82)   // Lighter purple
                    );
                    g2d.setPaint(gradient);
                    g2d.fillRect(metadataX, 0, width - metadataX, height);

                    log.debug("Rendered OSM tiles for activity {}", activity.getId());
                } catch (Exception e) {
                    log.warn("Failed to render OSM tiles, using gradient background: {}", e.getMessage());
                    // Fallback to gradient background
                    GradientPaint gradient = new GradientPaint(
                            0, 0, new Color(26, 0, 51),
                            width, height, new Color(45, 0, 82)
                    );
                    g2d.setPaint(gradient);
                    g2d.fillRect(0, 0, width, height);
                }
            } else {
                // OSM tiles disabled or no track data (indoor activity) - use gradient background
                GradientPaint gradient = new GradientPaint(
                        0, 0, new Color(26, 0, 51),
                        width, height, new Color(45, 0, 82)
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, width, height);

                // For indoor activities, draw a large emoji in the center-left area
                if (isIndoorActivity) {
                    drawIndoorActivityEmoji(g2d, activity, width, height);
                }
            }

            // Draw track if available (not for indoor activities)
            if (!isIndoorActivity) {
                if (activity.getTrackPointsJson() != null && !activity.getTrackPointsJson().isEmpty()) {
                    drawTrack(g2d, activity, width, height);
                } else if (activity.getSimplifiedTrack() != null) {
                    drawSimplifiedTrack(g2d, activity, width, height);
                }
            }

            // Draw metadata overlay
            drawMetadata(g2d, activity, width, height, isIndoorActivity);

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
     * Uses Web Mercator projection to match OSM tiles.
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

        // Get letterbox transformation from OSM renderer
        OsmTileRenderer.LetterboxTransform letterbox = osmTileRenderer.getLastLetterboxTransform();

        if (letterbox == null) {
            log.warn("No letterbox transform available, track overlay may be misaligned");
            return;
        }

        // Convert bounds to Web Mercator normalized coordinates (0-1)
        // This matches the projection used by OSM tiles
        double minX = longitudeToWebMercatorX(bounds.minLon);
        double maxX = longitudeToWebMercatorX(bounds.maxLon);
        double minY = latitudeToWebMercatorY(bounds.maxLat); // Note: maxLat -> minY (inverted)
        double maxY = latitudeToWebMercatorY(bounds.minLat); // Note: minLat -> maxY (inverted)

        // The letterbox transform gives us the actual rendered area within trackWidth x trackHeight
        // We need to map our mercator coordinates to fit within that rendered area

        // Calculate the mercator range that corresponds to the letterboxed (cropped/scaled) map
        double mercatorWidth = maxX - minX;
        double mercatorHeight = maxY - minY;

        // The scale factors tell us how the mercator coordinates map to the letterboxed area
        double pixelsPerMercatorX = letterbox.scaledWidth / mercatorWidth;
        double pixelsPerMercatorY = letterbox.scaledHeight / mercatorHeight;

        // Draw track segments with privacy fade - 80s neon glow style
        // First pass: draw glow effect (thicker, semi-transparent)
        g2d.setStroke(new BasicStroke(8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        final double HIDDEN_DISTANCE = 100.0; // First/last 100m completely hidden
        final double FADE_DISTANCE = 200.0;   // Fade zone from 100m to 200m

        // Draw glow pass
        for (int i = 0; i < trackPoints.size() - 1; i++) {
            Map<String, Object> point1 = trackPoints.get(i);
            Map<String, Object> point2 = trackPoints.get(i + 1);

            Double lat1 = getDouble(point1, "latitude");
            Double lon1 = getDouble(point1, "longitude");
            Double lat2 = getDouble(point2, "latitude");
            Double lon2 = getDouble(point2, "longitude");

            if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
                // Convert lat/lon to Web Mercator coordinates (same projection as OSM tiles)
                double mercatorX1 = longitudeToWebMercatorX(lon1);
                double mercatorY1 = latitudeToWebMercatorY(lat1);
                double mercatorX2 = longitudeToWebMercatorX(lon2);
                double mercatorY2 = latitudeToWebMercatorY(lat2);

                // Map Web Mercator coordinates to pixel coordinates within the letterbox
                double x1 = (mercatorX1 - minX) * pixelsPerMercatorX + letterbox.offsetX;
                double y1 = (mercatorY1 - minY) * pixelsPerMercatorY + letterbox.offsetY;
                double x2 = (mercatorX2 - minX) * pixelsPerMercatorX + letterbox.offsetX;
                double y2 = (mercatorY2 - minY) * pixelsPerMercatorY + letterbox.offsetY;

                // Calculate opacity based on distance from start/end
                double distanceFromStart = cumulativeDistances[i];
                double distanceFromEnd = totalDistance - cumulativeDistances[i];

                // Calculate fade opacity (0.0 to 1.0)
                float opacity = 1.0f;

                // Hide first 100m completely, fade in from 100m to 200m
                if (distanceFromStart < HIDDEN_DISTANCE) {
                    opacity = 0.0f;
                } else if (distanceFromStart < FADE_DISTANCE) {
                    opacity = Math.min(opacity, (float) ((distanceFromStart - HIDDEN_DISTANCE) / (FADE_DISTANCE - HIDDEN_DISTANCE)));
                }

                // Hide last 100m completely, fade out from 200m to 100m before end
                if (distanceFromEnd < HIDDEN_DISTANCE) {
                    opacity = 0.0f;
                } else if (distanceFromEnd < FADE_DISTANCE) {
                    opacity = Math.min(opacity, (float) ((distanceFromEnd - HIDDEN_DISTANCE) / (FADE_DISTANCE - HIDDEN_DISTANCE)));
                }

                // Skip completely transparent segments
                if (opacity <= 0.0f) {
                    continue;
                }

                // Apply opacity to glow color (semi-transparent cyan)
                int alpha = Math.max(0, Math.min(128, (int) (opacity * 128))); // Max 50% alpha for glow
                g2d.setColor(new Color(0, 255, 255, alpha));

                // Draw glow segment
                g2d.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            }
        }

        // Second pass: draw main track line (thinner, full opacity)
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < trackPoints.size() - 1; i++) {
            Map<String, Object> point1 = trackPoints.get(i);
            Map<String, Object> point2 = trackPoints.get(i + 1);

            Double lat1 = getDouble(point1, "latitude");
            Double lon1 = getDouble(point1, "longitude");
            Double lat2 = getDouble(point2, "latitude");
            Double lon2 = getDouble(point2, "longitude");

            if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
                // Convert lat/lon to Web Mercator coordinates (same projection as OSM tiles)
                double mercatorX1 = longitudeToWebMercatorX(lon1);
                double mercatorY1 = latitudeToWebMercatorY(lat1);
                double mercatorX2 = longitudeToWebMercatorX(lon2);
                double mercatorY2 = latitudeToWebMercatorY(lat2);

                // Map Web Mercator coordinates to pixel coordinates within the letterbox
                double x1 = (mercatorX1 - minX) * pixelsPerMercatorX + letterbox.offsetX;
                double y1 = (mercatorY1 - minY) * pixelsPerMercatorY + letterbox.offsetY;
                double x2 = (mercatorX2 - minX) * pixelsPerMercatorX + letterbox.offsetX;
                double y2 = (mercatorY2 - minY) * pixelsPerMercatorY + letterbox.offsetY;

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

                // Apply opacity to track color - neon cyan for 80s style
                int alpha = Math.max(0, Math.min(255, (int) (opacity * 255)));
                g2d.setColor(new Color(0, 255, 255, alpha)); // Neon cyan with alpha

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
     * Draw metadata overlay on the right side of the image in 80s Aerobic style.
     */
    private void drawMetadata(Graphics2D g2d, Activity activity, int width, int height, boolean isIndoorActivity) {
        int metadataX = (int) (width * 0.62); // Start at 62% to leave some margin
        int y = 80;
        int lineHeight = 50;

        // Neon colors
        Color neonPink = new Color(255, 20, 147);
        Color neonCyan = new Color(0, 255, 255);
        Color neonOrange = new Color(255, 102, 0);
        Color neonGreen = new Color(57, 255, 20);
        Color neonYellow = new Color(255, 255, 0);

        // Title with neon pink
        g2d.setColor(neonPink);
        g2d.setFont(new Font("Arial Black", Font.BOLD, 36));
        String title = activity.getTitle() != null ? activity.getTitle() : "ACTIVITY";
        title = title.toUpperCase();
        if (title.length() > 18) {
            title = title.substring(0, 18) + "...";
        }
        g2d.drawString(title, metadataX, y);
        y += lineHeight + 20;

        // Indoor activity label (if applicable)
        if (isIndoorActivity) {
            g2d.setFont(new Font("Arial Black", Font.BOLD, 16));
            g2d.setColor(neonYellow);
            g2d.drawString("INDOOR ACTIVITY", metadataX, y);
            y += 35;
        }

        // Activity type badge with border
        g2d.setFont(new Font("Arial Black", Font.BOLD, 20));
        String formattedType = ActivityFormatter.formatActivityType(activity.getActivityType()).toUpperCase();

        // Draw border box around type
        int typeWidth = g2d.getFontMetrics().stringWidth(formattedType);
        int boxPadding = 12;
        g2d.setColor(neonCyan);
        g2d.setStroke(new BasicStroke(3.0f));
        g2d.drawRect(metadataX - boxPadding, y - 25, typeWidth + boxPadding * 2, 35);
        g2d.drawString(formattedType, metadataX, y);
        y += lineHeight + 10;

        // Distance with neon orange value
        if (activity.getTotalDistance() != null) {
            g2d.setFont(new Font("Arial Black", Font.BOLD, 40));
            g2d.setColor(neonOrange);
            String distance = String.format("%.2f", activity.getTotalDistance().doubleValue() / 1000.0);
            g2d.drawString(distance, metadataX, y);

            g2d.setFont(new Font("Arial Black", Font.BOLD, 22));
            g2d.setColor(Color.WHITE);
            int distanceWidth = g2d.getFontMetrics(new Font("Arial Black", Font.BOLD, 40)).stringWidth(distance);
            g2d.drawString("KM", metadataX + distanceWidth + 10, y);

            g2d.setFont(new Font("Arial Black", Font.PLAIN, 16));
            g2d.setColor(new Color(180, 180, 180));
            g2d.drawString("DISTANCE", metadataX, y + 22);
            y += lineHeight + 35;
        }

        // Duration with neon cyan value
        if (activity.getTotalDurationSeconds() != null) {
            long hours = activity.getTotalDurationSeconds() / 3600;
            long minutes = (activity.getTotalDurationSeconds() % 3600) / 60;
            long seconds = activity.getTotalDurationSeconds() % 60;

            g2d.setFont(new Font("Arial Black", Font.BOLD, 40));
            g2d.setColor(neonCyan);
            String duration;
            if (hours > 0) {
                duration = String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                duration = String.format("%d:%02d", minutes, seconds);
            }
            g2d.drawString(duration, metadataX, y);
            g2d.setFont(new Font("Arial Black", Font.PLAIN, 16));
            g2d.setColor(new Color(180, 180, 180));
            g2d.drawString("DURATION", metadataX, y + 22);
            y += lineHeight + 35;
        }

        // Elevation gain with neon green value (only for outdoor activities)
        if (activity.getElevationGain() != null && !isIndoorActivity) {
            g2d.setFont(new Font("Arial Black", Font.BOLD, 40));
            g2d.setColor(neonGreen);
            String elevation = String.format("%.0f", activity.getElevationGain());
            g2d.drawString(elevation, metadataX, y);

            g2d.setFont(new Font("Arial Black", Font.BOLD, 22));
            g2d.setColor(Color.WHITE);
            int elevationWidth = g2d.getFontMetrics(new Font("Arial Black", Font.BOLD, 40)).stringWidth(elevation);
            g2d.drawString("M", metadataX + elevationWidth + 10, y);

            g2d.setFont(new Font("Arial Black", Font.PLAIN, 16));
            g2d.setColor(new Color(180, 180, 180));
            g2d.drawString("ELEVATION", metadataX, y + 22);
            y += lineHeight + 35;
        }

        // Heart Rate with neon orange value (for indoor activities)
        if (isIndoorActivity && activity.getMetrics() != null && activity.getMetrics().getAverageHeartRate() != null) {
            g2d.setFont(new Font("Arial Black", Font.BOLD, 40));
            g2d.setColor(neonOrange);
            String hr = String.format("%d", activity.getMetrics().getAverageHeartRate());
            g2d.drawString(hr, metadataX, y);

            g2d.setFont(new Font("Arial Black", Font.BOLD, 22));
            g2d.setColor(Color.WHITE);
            int hrWidth = g2d.getFontMetrics(new Font("Arial Black", Font.BOLD, 40)).stringWidth(hr);
            g2d.drawString("BPM", metadataX + hrWidth + 10, y);

            g2d.setFont(new Font("Arial Black", Font.PLAIN, 16));
            g2d.setColor(new Color(180, 180, 180));
            g2d.drawString("AVG HEART RATE", metadataX, y + 22);
            y += lineHeight + 35;
        }

        // Calories with neon green value (for indoor activities)
        if (isIndoorActivity && activity.getMetrics() != null && activity.getMetrics().getCalories() != null) {
            g2d.setFont(new Font("Arial Black", Font.BOLD, 40));
            g2d.setColor(neonGreen);
            String calories = String.format("%d", activity.getMetrics().getCalories());
            g2d.drawString(calories, metadataX, y);

            g2d.setFont(new Font("Arial Black", Font.BOLD, 22));
            g2d.setColor(Color.WHITE);
            int calWidth = g2d.getFontMetrics(new Font("Arial Black", Font.BOLD, 40)).stringWidth(calories);
            g2d.drawString("KCAL", metadataX + calWidth + 10, y);

            g2d.setFont(new Font("Arial Black", Font.PLAIN, 16));
            g2d.setColor(new Color(180, 180, 180));
            g2d.drawString("CALORIES", metadataX, y + 22);
            y += lineHeight + 35;
        }

        // Branding with neon pink gradient effect
        g2d.setFont(new Font("Arial Black", Font.BOLD, 28));
        g2d.setColor(neonPink);
        g2d.drawString("FITPUB", metadataX, height - 50);

        // Add decorative line above branding
        g2d.setColor(neonCyan);
        g2d.setStroke(new BasicStroke(3.0f));
        g2d.drawLine(metadataX, height - 75, metadataX + 150, height - 75);
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
     * Convert longitude to Web Mercator X coordinate (normalized 0-1).
     * This must match the projection used by OsmTileRenderer.
     */
    private double longitudeToWebMercatorX(double lon) {
        return (lon + 180.0) / 360.0;
    }

    /**
     * Convert latitude to Web Mercator Y coordinate (normalized 0-1).
     * This must match the projection used by OsmTileRenderer.
     * Uses the same logarithmic transformation as OSM tiles.
     */
    private double latitudeToWebMercatorY(double lat) {
        return (1.0 - Math.log(Math.tan(Math.toRadians(lat)) +
                1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0;
    }

    /**
     * Draw a large emoji for indoor activities in the center-left area.
     */
    private void drawIndoorActivityEmoji(Graphics2D g2d, Activity activity, int width, int height) {
        // Map activity types to emojis
        String emoji;
        switch (activity.getActivityType()) {
            case RUN:
                emoji = "🏃";
                break;
            case RIDE:
                emoji = "🚴";
                break;
            case SWIM:
                emoji = "🏊";
                break;
            case WORKOUT:
                emoji = "💪";
                break;
            case YOGA:
                emoji = "🧘";
                break;
            case ROWING:
                emoji = "🚣";
                break;
            case WALK:
                emoji = "🚶";
                break;
            case HIKE:
                emoji = "🥾";
                break;
            case ALPINE_SKI:
            case NORDIC_SKI:
            case BACKCOUNTRY_SKI:
                emoji = "⛷️";
                break;
            case SNOWBOARD:
                emoji = "🏂";
                break;
            case KAYAKING:
            case CANOEING:
                emoji = "🛶";
                break;
            case ROCK_CLIMBING:
            case MOUNTAINEERING:
                emoji = "🧗";
                break;
            case INLINE_SKATING:
                emoji = "🛼";
                break;
            default:
                emoji = "🏋️";
                break;
        }

        // Draw emoji in the center-left area (where the map would be)
        int emojiX = (int) (width * 0.3) - 100; // Center of left 60%
        int emojiY = height / 2;

        // Use a very large font for the emoji
        g2d.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 200));
        g2d.setColor(Color.WHITE);

        // Calculate emoji width to center it properly
        FontMetrics fm = g2d.getFontMetrics();
        int emojiWidth = fm.stringWidth(emoji);
        int emojiHeight = fm.getHeight();

        // Draw emoji centered in the left area
        g2d.drawString(emoji, emojiX - emojiWidth / 2, emojiY + emojiHeight / 3);
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
