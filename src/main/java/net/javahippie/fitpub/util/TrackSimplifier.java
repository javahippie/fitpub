package net.javahippie.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplifies GPS tracks using the Douglas-Peucker algorithm.
 * Reduces the number of points while maintaining the overall shape of the track.
 * This is critical for scalability - we don't want to render 1000+ points on a map.
 */
@Component
@Slf4j
public class TrackSimplifier {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    // Default epsilon: ~10 meters tolerance (in degrees, roughly 0.0001 degrees)
    private static final double DEFAULT_EPSILON = 0.0001;

    // Target: 50-200 points for map rendering
    private static final int TARGET_POINTS_MIN = 50;
    private static final int TARGET_POINTS_MAX = 200;

    /**
     * Simplifies a track to a target number of points.
     * Automatically adjusts epsilon to achieve the desired point count.
     *
     * @param coordinates original track coordinates
     * @return simplified LineString
     */
    public LineString simplify(Coordinate[] coordinates) {
        if (coordinates == null || coordinates.length == 0) {
            return GEOMETRY_FACTORY.createLineString(new Coordinate[0]);
        }

        if (coordinates.length <= TARGET_POINTS_MAX) {
            // Already small enough, no simplification needed
            log.debug("Track has {} points, no simplification needed", coordinates.length);
            return GEOMETRY_FACTORY.createLineString(coordinates);
        }

        // Try to find epsilon that gives us TARGET_POINTS_MIN to TARGET_POINTS_MAX points
        double epsilon = DEFAULT_EPSILON;
        List<Coordinate> simplified = douglasPeucker(coordinates, epsilon);

        // Adjust epsilon if needed
        int iterations = 0;
        while (simplified.size() > TARGET_POINTS_MAX && iterations < 10) {
            epsilon *= 1.5; // Increase tolerance
            simplified = douglasPeucker(coordinates, epsilon);
            iterations++;
        }

        while (simplified.size() < TARGET_POINTS_MIN && epsilon > 0.00001 && iterations < 10) {
            epsilon *= 0.7; // Decrease tolerance
            simplified = douglasPeucker(coordinates, epsilon);
            iterations++;
        }

        log.info("Simplified track from {} to {} points (epsilon: {})",
            coordinates.length, simplified.size(), epsilon);

        return GEOMETRY_FACTORY.createLineString(simplified.toArray(new Coordinate[0]));
    }

    /**
     * Douglas-Peucker algorithm implementation.
     * Recursively removes points that deviate less than epsilon from the line.
     *
     * @param coordinates input coordinates
     * @param epsilon tolerance (maximum distance from line)
     * @return simplified list of coordinates
     */
    private List<Coordinate> douglasPeucker(Coordinate[] coordinates, double epsilon) {
        if (coordinates.length < 3) {
            List<Coordinate> result = new ArrayList<>();
            for (Coordinate coord : coordinates) {
                result.add(coord);
            }
            return result;
        }

        // Find point with maximum distance from line between first and last point
        double maxDistance = 0;
        int maxIndex = 0;

        Coordinate start = coordinates[0];
        Coordinate end = coordinates[coordinates.length - 1];

        for (int i = 1; i < coordinates.length - 1; i++) {
            double distance = perpendicularDistance(coordinates[i], start, end);
            if (distance > maxDistance) {
                maxDistance = distance;
                maxIndex = i;
            }
        }

        List<Coordinate> result = new ArrayList<>();

        // If max distance is greater than epsilon, recursively simplify
        if (maxDistance > epsilon) {
            // Recursive call for first part
            Coordinate[] firstPart = new Coordinate[maxIndex + 1];
            System.arraycopy(coordinates, 0, firstPart, 0, maxIndex + 1);
            List<Coordinate> left = douglasPeucker(firstPart, epsilon);

            // Recursive call for second part
            Coordinate[] secondPart = new Coordinate[coordinates.length - maxIndex];
            System.arraycopy(coordinates, maxIndex, secondPart, 0, coordinates.length - maxIndex);
            List<Coordinate> right = douglasPeucker(secondPart, epsilon);

            // Combine results (remove duplicate point at junction)
            result.addAll(left.subList(0, left.size() - 1));
            result.addAll(right);
        } else {
            // All points can be removed except endpoints
            result.add(start);
            result.add(end);
        }

        return result;
    }

    /**
     * Calculates perpendicular distance from a point to a line segment.
     *
     * @param point the point
     * @param lineStart start of line segment
     * @param lineEnd end of line segment
     * @return perpendicular distance
     */
    private double perpendicularDistance(Coordinate point, Coordinate lineStart, Coordinate lineEnd) {
        double x = point.x;
        double y = point.y;
        double x1 = lineStart.x;
        double y1 = lineStart.y;
        double x2 = lineEnd.x;
        double y2 = lineEnd.y;

        double A = x - x1;
        double B = y - y1;
        double C = x2 - x1;
        double D = y2 - y1;

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;

        if (lenSq == 0) {
            // Line segment is actually a point
            return Math.sqrt(A * A + B * B);
        }

        double param = dot / lenSq;

        double xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        double dx = x - xx;
        double dy = y - yy;

        return Math.sqrt(dx * dx + dy * dy);
    }
}
