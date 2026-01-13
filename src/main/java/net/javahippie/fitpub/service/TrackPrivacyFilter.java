package net.javahippie.fitpub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import net.javahippie.fitpub.model.entity.PrivacyZone;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Service for filtering GPS tracks through privacy zones.
 * Removes coordinates that fall within user-defined privacy zones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackPrivacyFilter {

    private static final int SRID = 4326; // WGS84
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);
    private final ObjectMapper objectMapper;

    /**
     * Filters a LineString track by removing coordinates within privacy zones.
     *
     * @param track the simplified track LineString
     * @param zones list of active privacy zones
     * @return filtered LineString, or null if <2 points remain
     */
    public LineString filterLineString(LineString track, List<PrivacyZone> zones) {
        if (track == null || zones == null || zones.isEmpty()) {
            return track;
        }

        List<Coordinate> filteredCoords = new ArrayList<>();
        Coordinate[] coordinates = track.getCoordinates();
        int originalCount = coordinates.length;
        int filteredCount = 0;

        for (Coordinate coord : coordinates) {
            double latitude = coord.y;
            double longitude = coord.x;

            if (!isPointInAnyZone(latitude, longitude, zones)) {
                filteredCoords.add(coord);
            } else {
                filteredCount++;
            }
        }

        log.info("Privacy filter: {} zones active, {}/{} points filtered out",
                 zones.size(), filteredCount, originalCount);

        // Return null if track is completely filtered or too few points remain
        if (filteredCoords.size() < 2) {
            log.warn("Track completely filtered by privacy zones (or <2 points remain)");
            return null;
        }

        return geometryFactory.createLineString(filteredCoords.toArray(new Coordinate[0]));
    }

    /**
     * Filters track points JSON by removing points within privacy zones.
     *
     * @param trackPointsJson JSONB string containing track points
     * @param zones list of active privacy zones
     * @return filtered JSON string, or null if <2 points remain
     */
    public String filterTrackPointsJson(String trackPointsJson, List<PrivacyZone> zones) {
        if (trackPointsJson == null || zones == null || zones.isEmpty()) {
            return trackPointsJson;
        }

        try {
            JsonNode root = objectMapper.readTree(trackPointsJson);

            if (!root.isArray()) {
                log.warn("Track points JSON is not an array");
                return trackPointsJson;
            }

            ArrayNode filteredArray = objectMapper.createArrayNode();

            for (JsonNode node : root) {
                if (!node.has("latitude") || !node.has("longitude")) {
                    continue; // Skip points without coordinates
                }

                double latitude = node.get("latitude").asDouble();
                double longitude = node.get("longitude").asDouble();

                if (!isPointInAnyZone(latitude, longitude, zones)) {
                    filteredArray.add(node);
                }
            }

            // Return null if track is completely filtered or too few points remain
            if (filteredArray.size() < 2) {
                log.debug("Track points completely filtered by privacy zones (or <2 points remain)");
                return null;
            }

            return objectMapper.writeValueAsString(filteredArray);

        } catch (Exception e) {
            log.error("Error filtering track points JSON", e);
            return trackPointsJson; // Return original on error (fail-open for owner)
        }
    }

    /**
     * Check if a point falls within any of the given privacy zones.
     *
     * @param latitude point latitude
     * @param longitude point longitude
     * @param zones list of privacy zones
     * @return true if point is within any zone
     */
    private boolean isPointInAnyZone(double latitude, double longitude, List<PrivacyZone> zones) {
        for (PrivacyZone zone : zones) {
            Point center = zone.getCenterPoint();
            double centerLat = center.getY();
            double centerLon = center.getX();
            int radiusMeters = zone.getRadiusMeters();

            double distanceMeters = haversineDistance(latitude, longitude, centerLat, centerLon);

            if (distanceMeters <= radiusMeters) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate Haversine distance between two GPS coordinates.
     * Returns distance in meters.
     *
     * @param lat1 first point latitude
     * @param lon1 first point longitude
     * @param lat2 second point latitude
     * @param lon2 second point longitude
     * @return distance in meters
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_METERS = 6371000.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Get a randomized display center for a privacy zone.
     * The randomization is consistent per activity (seeded by activity ID).
     * Offset is within 10-15% of the radius from the true center.
     *
     * @param zone the privacy zone
     * @param activityId the activity ID (for consistent seeding)
     * @return randomized Point for display
     */
    public Point getRandomizedDisplayCenter(PrivacyZone zone, UUID activityId) {
        Point trueCenter = zone.getCenterPoint();
        int radiusMeters = zone.getRadiusMeters();

        // Seed Random with activity ID for consistency
        Random random = new Random(activityId.getMostSignificantBits() ^ activityId.getLeastSignificantBits());

        // Random offset within 10-15% of radius
        double offsetPercent = 0.10 + random.nextDouble() * 0.05; // 10-15%
        double offsetMeters = radiusMeters * offsetPercent;

        // Random angle in radians
        double angle = random.nextDouble() * 2 * Math.PI;

        // Convert offset to degrees (approximate)
        double degreesPerMeter = 1.0 / 111320.0; // At equator
        double offsetDegrees = offsetMeters * degreesPerMeter;

        // Calculate randomized coordinates
        double randomLat = trueCenter.getY() + (offsetDegrees * Math.sin(angle));
        double randomLon = trueCenter.getX() + (offsetDegrees * Math.cos(angle) / Math.cos(Math.toRadians(trueCenter.getY())));

        return geometryFactory.createPoint(new Coordinate(randomLon, randomLat));
    }

    /**
     * Get randomized display center as lat/lon coordinates.
     *
     * @param zone the privacy zone
     * @param activityId the activity ID (for consistent seeding)
     * @return array [latitude, longitude]
     */
    public double[] getRandomizedDisplayCenterCoordinates(PrivacyZone zone, UUID activityId) {
        Point randomized = getRandomizedDisplayCenter(zone, activityId);
        return new double[]{randomized.getY(), randomized.getX()};
    }
}
