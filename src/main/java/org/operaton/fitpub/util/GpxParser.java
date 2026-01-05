package org.operaton.fitpub.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.operaton.fitpub.exception.GpxFileProcessingException;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.util.ParsedActivityData.ActivityMetricsData;
import org.operaton.fitpub.util.ParsedActivityData.TrackPointData;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parser for GPX (GPS Exchange Format) files.
 * Extracts GPS coordinates, activity metrics from track points.
 * Since GPX files lack session summaries, metrics are calculated from track points.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GpxParser {

    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";
    private static final String GPXTPX_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1";
    private static final double ELEVATION_NOISE_THRESHOLD = 2.0; // Ignore elevation changes < 2m
    private static final double STOPPED_SPEED_THRESHOLD = 0.5; // km/h - below this is considered stopped
    private static final long STOPPED_TIME_THRESHOLD = 30; // seconds - must be stopped this long to count

    // Lazy-loaded timezone engine (expensive to initialize)
    private static TimeZoneEngine timezoneEngine = null;

    private final SpeedSmoother speedSmoother;

    /**
     * Parses a GPX file and returns the extracted data.
     *
     * @param fileData the GPX file data
     * @return ParsedActivityData containing activity information
     * @throws GpxFileProcessingException if parsing fails
     */
    public ParsedActivityData parse(byte[] fileData) {
        try {
            ParsedActivityData parsedData = new ParsedActivityData();
            parsedData.setSourceFormat("GPX");

            // Parse XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(fileData));

            // Extract track points
            extractTrackPoints(doc, parsedData);

            if (parsedData.getTrackPoints().isEmpty()) {
                throw new GpxFileProcessingException("No GPS track points found in GPX file");
            }

            // Set start and end times from first and last track points
            TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
            TrackPointData lastPoint = parsedData.getTrackPoints().get(parsedData.getTrackPoints().size() - 1);
            parsedData.setStartTime(firstPoint.getTimestamp());
            parsedData.setEndTime(lastPoint.getTimestamp());

            // Calculate duration
            parsedData.setTotalDuration(Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp()));

            // Extract activity type from metadata
            extractActivityType(doc, parsedData);

            // Determine timezone from first GPS coordinate
            determineTimezone(parsedData);

            // Calculate metrics from track points
            calculateMetrics(parsedData);

            // Apply speed smoothing
            smoothSpeedData(parsedData);

            log.info("Successfully parsed GPX file: {} track points, activity type: {}, timezone: {}",
                parsedData.getTrackPoints().size(), parsedData.getActivityType(), parsedData.getTimezone());

            return parsedData;
        } catch (GpxFileProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new GpxFileProcessingException("Failed to parse GPX file", e);
        }
    }

    /**
     * Extracts track points from GPX document.
     */
    private void extractTrackPoints(Document doc, ParsedActivityData parsedData) {
        NodeList tracks = doc.getElementsByTagName("trk");
        if (tracks.getLength() == 0) {
            tracks = doc.getElementsByTagNameNS("*", "trk");
        }

        for (int i = 0; i < tracks.getLength(); i++) {
            Element track = (Element) tracks.item(i);

            // Get track segments
            NodeList segments = track.getElementsByTagName("trkseg");
            if (segments.getLength() == 0) {
                segments = track.getElementsByTagNameNS("*", "trkseg");
            }

            for (int j = 0; j < segments.getLength(); j++) {
                Element segment = (Element) segments.item(j);

                // Get track points
                NodeList trkpts = segment.getElementsByTagName("trkpt");
                if (trkpts.getLength() == 0) {
                    trkpts = segment.getElementsByTagNameNS("*", "trkpt");
                }

                for (int k = 0; k < trkpts.getLength(); k++) {
                    Element trkptElement = (Element) trkpts.item(k);
                    TrackPointData trackPoint = extractTrackPoint(trkptElement);
                    if (trackPoint != null) {
                        parsedData.getTrackPoints().add(trackPoint);
                    }
                }
            }
        }
    }

    /**
     * Extracts a single track point from a <trkpt> element.
     */
    private TrackPointData extractTrackPoint(Element trkptElement) {
        try {
            TrackPointData point = new TrackPointData();

            // Extract latitude and longitude from attributes
            String latStr = trkptElement.getAttribute("lat");
            String lonStr = trkptElement.getAttribute("lon");

            if (latStr.isEmpty() || lonStr.isEmpty()) {
                log.warn("Track point missing lat/lon attributes");
                return null;
            }

            point.setLatitude(Double.parseDouble(latStr));
            point.setLongitude(Double.parseDouble(lonStr));

            // Extract elevation
            String elevation = getElementText(trkptElement, "ele");
            if (elevation != null) {
                point.setElevation(new BigDecimal(elevation).setScale(2, RoundingMode.HALF_UP));
            }

            // Extract time
            String time = getElementText(trkptElement, "time");
            if (time != null) {
                point.setTimestamp(parseIso8601DateTime(time));
            }

            // Extract extensions (heart rate, cadence, power, temperature)
            extractExtensions(trkptElement, point);

            return point;
        } catch (Exception e) {
            log.warn("Failed to extract track point: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts Garmin/TrainingPeaks extensions from track point.
     */
    private void extractExtensions(Element trkptElement, TrackPointData point) {
        NodeList extensions = trkptElement.getElementsByTagName("extensions");
        if (extensions.getLength() == 0) {
            extensions = trkptElement.getElementsByTagNameNS("*", "extensions");
        }

        if (extensions.getLength() == 0) {
            return; // No extensions
        }

        Element extensionsElement = (Element) extensions.item(0);

        // Try Garmin TrackPointExtension namespace
        NodeList tpx = extensionsElement.getElementsByTagNameNS(GPXTPX_NS, "TrackPointExtension");
        if (tpx.getLength() == 0) {
            // Try without namespace
            tpx = extensionsElement.getElementsByTagName("TrackPointExtension");
        }

        if (tpx.getLength() > 0) {
            Element tpxElement = (Element) tpx.item(0);

            // Heart rate
            String hr = getElementTextNS(tpxElement, GPXTPX_NS, "hr");
            if (hr == null) hr = getElementText(tpxElement, "hr");
            if (hr != null) {
                point.setHeartRate(Integer.parseInt(hr));
            }

            // Cadence
            String cad = getElementTextNS(tpxElement, GPXTPX_NS, "cad");
            if (cad == null) cad = getElementText(tpxElement, "cad");
            if (cad != null) {
                point.setCadence(Integer.parseInt(cad));
            }

            // Temperature
            String atemp = getElementTextNS(tpxElement, GPXTPX_NS, "atemp");
            if (atemp == null) atemp = getElementText(tpxElement, "atemp");
            if (atemp != null) {
                point.setTemperature(new BigDecimal(atemp).setScale(2, RoundingMode.HALF_UP));
            }
        }

        // Check for power extension (different namespace)
        String power = getElementTextNS(extensionsElement, "*", "power");
        if (power == null) power = getElementText(extensionsElement, "power");
        if (power != null) {
            point.setPower(Integer.parseInt(power));
        }
    }

    /**
     * Extracts activity type from GPX metadata.
     */
    private void extractActivityType(Document doc, ParsedActivityData parsedData) {
        NodeList tracks = doc.getElementsByTagName("trk");
        if (tracks.getLength() == 0) {
            tracks = doc.getElementsByTagNameNS("*", "trk");
        }

        if (tracks.getLength() > 0) {
            Element track = (Element) tracks.item(0);
            String type = getElementText(track, "type");
            if (type != null) {
                parsedData.setActivityType(mapGpxTypeToActivityType(type));
            }
        }
    }

    /**
     * Maps GPX activity type string to ActivityType enum.
     * GPX type field is not standardized, so we support common values.
     */
    private Activity.ActivityType mapGpxTypeToActivityType(String gpxType) {
        if (gpxType == null || gpxType.isEmpty()) {
            return Activity.ActivityType.OTHER;
        }

        String type = gpxType.toLowerCase().trim();

        // Running variations
        if (type.contains("run") || type.equals("9")) {
            return Activity.ActivityType.RUN;
        }

        // Cycling variations
        if (type.contains("cycl") || type.contains("bik") || type.equals("1")) {
            return Activity.ActivityType.RIDE;
        }

        // Hiking
        if (type.contains("hik")) {
            return Activity.ActivityType.HIKE;
        }

        // Walking
        if (type.contains("walk")) {
            return Activity.ActivityType.WALK;
        }

        // Swimming
        if (type.contains("swim")) {
            return Activity.ActivityType.SWIM;
        }

        // Rowing
        if (type.contains("row")) {
            return Activity.ActivityType.ROWING;
        }

        log.debug("Unknown GPX activity type '{}', defaulting to OTHER", gpxType);
        return Activity.ActivityType.OTHER;
    }

    /**
     * Calculates all metrics from track points.
     * GPX files don't include session summaries, so we must calculate everything.
     */
    private void calculateMetrics(ParsedActivityData parsedData) {
        List<TrackPointData> points = parsedData.getTrackPoints();
        if (points.isEmpty()) {
            return;
        }

        ActivityMetricsData metrics = new ActivityMetricsData();

        double totalDistance = 0;
        double elevationGain = 0;
        double elevationLoss = 0;
        BigDecimal previousElevation = points.get(0).getElevation();
        BigDecimal maxElevation = previousElevation;
        BigDecimal minElevation = previousElevation;

        List<BigDecimal> speeds = new ArrayList<>();
        List<Integer> heartRates = new ArrayList<>();
        List<Integer> cadences = new ArrayList<>();
        List<Integer> powers = new ArrayList<>();
        List<BigDecimal> temperatures = new ArrayList<>();

        LocalDateTime lastStoppedTime = null;
        Duration stoppedTime = Duration.ZERO;
        Duration movingTime = Duration.ZERO;

        // Iterate through consecutive pairs of points
        for (int i = 1; i < points.size(); i++) {
            TrackPointData prev = points.get(i - 1);
            TrackPointData curr = points.get(i);

            // Calculate distance between points (Haversine formula)
            double distance = calculateDistance(prev, curr);
            totalDistance += distance;

            // Calculate speed (km/h)
            if (prev.getTimestamp() != null && curr.getTimestamp() != null) {
                Duration timeDelta = Duration.between(prev.getTimestamp(), curr.getTimestamp());
                long seconds = timeDelta.getSeconds();

                if (seconds > 0) {
                    double speedKmh = (distance / 1000.0) / (seconds / 3600.0);
                    BigDecimal speed = BigDecimal.valueOf(speedKmh).setScale(2, RoundingMode.HALF_UP);
                    curr.setSpeed(speed);
                    speeds.add(speed);

                    // Track moving vs stopped time
                    if (speedKmh < STOPPED_SPEED_THRESHOLD) {
                        if (lastStoppedTime == null) {
                            lastStoppedTime = prev.getTimestamp();
                        }
                        Duration currentStopDuration = Duration.between(lastStoppedTime, curr.getTimestamp());
                        if (currentStopDuration.getSeconds() > STOPPED_TIME_THRESHOLD) {
                            stoppedTime = stoppedTime.plus(timeDelta);
                        }
                    } else {
                        lastStoppedTime = null;
                        movingTime = movingTime.plus(timeDelta);
                    }
                }
            }

            // Set cumulative distance
            curr.setDistance(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP));

            // Calculate elevation gain/loss
            if (curr.getElevation() != null && previousElevation != null) {
                double elevDelta = curr.getElevation().subtract(previousElevation).doubleValue();

                // Ignore noise (GPS elevation is noisy)
                if (Math.abs(elevDelta) > ELEVATION_NOISE_THRESHOLD) {
                    if (elevDelta > 0) {
                        elevationGain += elevDelta;
                    } else {
                        elevationLoss += Math.abs(elevDelta);
                    }
                }

                // Track min/max elevation
                if (maxElevation == null || curr.getElevation().compareTo(maxElevation) > 0) {
                    maxElevation = curr.getElevation();
                }
                if (minElevation == null || curr.getElevation().compareTo(minElevation) < 0) {
                    minElevation = curr.getElevation();
                }

                previousElevation = curr.getElevation();
            }

            // Collect sensor data
            if (curr.getHeartRate() != null) {
                heartRates.add(curr.getHeartRate());
            }
            if (curr.getCadence() != null) {
                cadences.add(curr.getCadence());
            }
            if (curr.getPower() != null) {
                powers.add(curr.getPower());
            }
            if (curr.getTemperature() != null) {
                temperatures.add(curr.getTemperature());
            }
        }

        // Set calculated values in parsedData
        parsedData.setTotalDistance(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP));
        parsedData.setElevationGain(BigDecimal.valueOf(elevationGain).setScale(2, RoundingMode.HALF_UP));
        parsedData.setElevationLoss(BigDecimal.valueOf(elevationLoss).setScale(2, RoundingMode.HALF_UP));

        // Calculate average and max values
        // Calculate average speed from total distance and moving time (not from all speed values)
        if (totalDistance > 0 && movingTime.getSeconds() > 0) {
            // Convert: distance (meters) / time (seconds) = m/s, then * 3.6 = km/h
            double avgSpeedKmh = (totalDistance / movingTime.getSeconds()) * 3.6;
            metrics.setAverageSpeed(BigDecimal.valueOf(avgSpeedKmh).setScale(2, RoundingMode.HALF_UP));

            // Calculate average pace (min/km) for running activities
            // pace = time (seconds) / distance (km)
            double distanceKm = totalDistance / 1000.0;
            if (distanceKm > 0) {
                long paceSecondsPerKm = (long) (movingTime.getSeconds() / distanceKm);
                metrics.setAveragePace(Duration.ofSeconds(paceSecondsPerKm));
            }
        }

        if (!speeds.isEmpty()) {
            metrics.setMaxSpeed(speeds.stream().max(BigDecimal::compareTo).orElse(null));
        }

        if (!heartRates.isEmpty()) {
            metrics.setAverageHeartRate(calculateAverageInt(heartRates));
            metrics.setMaxHeartRate(heartRates.stream().max(Integer::compareTo).orElse(null));
        }

        if (!cadences.isEmpty()) {
            metrics.setAverageCadence(calculateAverageInt(cadences));
            metrics.setMaxCadence(cadences.stream().max(Integer::compareTo).orElse(null));
        }

        if (!powers.isEmpty()) {
            metrics.setAveragePower(calculateAverageInt(powers));
            metrics.setMaxPower(powers.stream().max(Integer::compareTo).orElse(null));
        }

        if (!temperatures.isEmpty()) {
            metrics.setAverageTemperature(calculateAverage(temperatures));
        }

        metrics.setMaxElevation(maxElevation);
        metrics.setMinElevation(minElevation);
        metrics.setTotalAscent(BigDecimal.valueOf(elevationGain).setScale(2, RoundingMode.HALF_UP));
        metrics.setTotalDescent(BigDecimal.valueOf(elevationLoss).setScale(2, RoundingMode.HALF_UP));
        metrics.setMovingTime(movingTime);
        metrics.setStoppedTime(stoppedTime);

        parsedData.setMetrics(metrics);
    }

    /**
     * Calculates distance between two GPS points using Haversine formula.
     * Returns distance in meters.
     */
    private double calculateDistance(TrackPointData p1, TrackPointData p2) {
        final double EARTH_RADIUS = 6371000; // meters

        double lat1 = Math.toRadians(p1.getLatitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double deltaLat = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double deltaLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Determines the timezone based on the first GPS coordinate.
     */
    private void determineTimezone(ParsedActivityData parsedData) {
        if (parsedData.getTrackPoints().isEmpty()) {
            parsedData.setTimezone("UTC");
            return;
        }

        TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
        double latitude = firstPoint.getLatitude();
        double longitude = firstPoint.getLongitude();

        try {
            // Lazy-load timezone engine (expensive initialization ~200ms first time)
            if (timezoneEngine == null) {
                log.info("Initializing TimeZoneEngine for timezone lookup...");
                timezoneEngine = TimeZoneEngine.initialize();
            }

            Optional<ZoneId> zoneId = timezoneEngine.query(latitude, longitude);
            if (zoneId.isPresent()) {
                parsedData.setTimezone(zoneId.get().getId());
                log.debug("Determined timezone: {} from coordinates ({}, {})",
                    zoneId.get().getId(), latitude, longitude);
            } else {
                log.warn("Could not determine timezone for coordinates ({}, {}), defaulting to UTC",
                    latitude, longitude);
                parsedData.setTimezone("UTC");
            }
        } catch (Exception e) {
            log.error("Error determining timezone, defaulting to UTC", e);
            parsedData.setTimezone("UTC");
        }
    }

    /**
     * Applies speed smoothing to track points and updates max speed in metrics.
     */
    private void smoothSpeedData(ParsedActivityData parsedData) {
        if (parsedData.getTrackPoints().isEmpty() || parsedData.getMetrics() == null) {
            return;
        }

        // Smooth speed data and get recalculated max speed
        BigDecimal smoothedMaxSpeed = speedSmoother.smoothAndCalculateMaxSpeed(
            parsedData.getTrackPoints(),
            parsedData.getActivityType()
        );

        // Update max speed in metrics if we got a valid smoothed value
        if (smoothedMaxSpeed != null) {
            BigDecimal originalMaxSpeed = parsedData.getMetrics().getMaxSpeed();
            parsedData.getMetrics().setMaxSpeed(smoothedMaxSpeed);

            if (originalMaxSpeed != null && smoothedMaxSpeed.compareTo(originalMaxSpeed) < 0) {
                log.info("Smoothed max speed from {} km/h to {} km/h (removed GPS artifacts)",
                    originalMaxSpeed, smoothedMaxSpeed);
            }
        }
    }

    /**
     * Parses ISO 8601 datetime string (GPX standard).
     */
    private LocalDateTime parseIso8601DateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    /**
     * Gets text content of child element by tag name.
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagNameNS("*", tagName);
        }
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            String text = node.getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    /**
     * Gets text content of child element by namespace and tag name.
     */
    private String getElementTextNS(Element parent, String namespace, String tagName) {
        NodeList nodes = "*".equals(namespace)
            ? parent.getElementsByTagNameNS("*", tagName)
            : parent.getElementsByTagNameNS(namespace, tagName);

        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            String text = node.getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    /**
     * Calculates average of BigDecimal list.
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates average of Integer list.
     */
    private Integer calculateAverageInt(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = values.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
        return (int) Math.round(sum);
    }
}
