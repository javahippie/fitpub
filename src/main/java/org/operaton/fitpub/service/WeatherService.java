package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.WeatherData;
import org.operaton.fitpub.repository.WeatherDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for fetching and managing weather data for activities.
 * Uses OpenWeatherMap API to retrieve historical weather data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherDataRepository weatherDataRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${fitpub.weather.api-key:}")
    private String apiKey;

    @Value("${fitpub.weather.enabled:false}")
    private boolean weatherEnabled;

    private static final String OPENWEATHERMAP_API_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String OPENWEATHERMAP_TIMEMACHINE_URL = "https://api.openweathermap.org/data/3.0/onecall/timemachine";

    /**
     * Fetch and store weather data for an activity.
     * Uses the activity's start location and timestamp to get weather conditions.
     *
     * @param activity the activity
     * @return the weather data, or empty if fetching failed
     */
    @Transactional
    public Optional<WeatherData> fetchWeatherForActivity(Activity activity) {
        log.info("=== Weather fetch requested for activity {} ===", activity.getId());
        log.info("Weather enabled: {}, API key configured: {}", weatherEnabled, (apiKey != null && !apiKey.isBlank()));

        if (!weatherEnabled) {
            log.warn("Weather fetching is DISABLED in configuration (fitpub.weather.enabled=false)");
            return Optional.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.error("Weather API key is NOT CONFIGURED (fitpub.weather.api-key is empty)");
            return Optional.empty();
        }

        log.debug("Weather API key present (length: {} chars, starts with: {}...)",
                  apiKey.length(), apiKey.length() > 4 ? apiKey.substring(0, 4) : "???");

        // Check if weather data already exists
        if (weatherDataRepository.existsByActivityId(activity.getId())) {
            log.info("Weather data already exists for activity {}, returning cached data", activity.getId());
            return weatherDataRepository.findByActivityId(activity.getId());
        }

        // Extract start location from track
        if (activity.getTrackPointsJson() == null || activity.getTrackPointsJson().isEmpty()) {
            log.warn("No track points available for activity {} - cannot fetch weather", activity.getId());
            return Optional.empty();
        }

        log.debug("Track points JSON length: {} chars", activity.getTrackPointsJson().length());

        try {
            // Get first track point for location
            JsonNode trackPoints = objectMapper.readTree(activity.getTrackPointsJson());
            log.debug("Parsed track points, is array: {}, size: {}",
                      trackPoints.isArray(), trackPoints.isArray() ? trackPoints.size() : "N/A");

            if (!trackPoints.isArray() || trackPoints.isEmpty()) {
                log.warn("Track points is not an array or is empty for activity {}", activity.getId());
                return Optional.empty();
            }

            JsonNode firstPoint = trackPoints.get(0);
            log.debug("First track point fields: {}", firstPoint.fieldNames().hasNext() ?
                      String.join(", ", () -> firstPoint.fieldNames()) : "none");

            // Check if lat/lon fields exist
            if (!firstPoint.has("lat") || !firstPoint.has("lon")) {
                log.error("First track point missing lat/lon fields for activity {}. Available fields: {}",
                          activity.getId(), String.join(", ", () -> firstPoint.fieldNames()));
                return Optional.empty();
            }

            double lat = firstPoint.get("lat").asDouble();
            double lon = firstPoint.get("lon").asDouble();
            log.info("Extracted location: lat={}, lon={}", lat, lon);

            // Check if activity is recent (within 5 days) - use current weather API
            // Otherwise use historical data API (requires paid plan)
            long activityTimestamp = activity.getStartedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
            long currentTimestamp = Instant.now().getEpochSecond();
            long daysDifference = (currentTimestamp - activityTimestamp) / 86400;

            log.info("Activity started at: {}, days ago: {}", activity.getStartedAt(), daysDifference);

            WeatherData weatherData;
            if (daysDifference <= 5) {
                log.info("Activity is recent (within 5 days), fetching current weather");
                weatherData = fetchCurrentWeather(lat, lon, activity.getId());
            } else {
                log.warn("Activity is {} days old (>5 days), historical weather data requires paid API plan. Skipping.", daysDifference);
                return Optional.empty();
            }

            if (weatherData != null) {
                log.info("Successfully fetched and parsed weather data, saving to database");
                WeatherData saved = weatherDataRepository.save(weatherData);
                log.info("Weather data saved with ID: {}", saved.getId());
                return Optional.of(saved);
            } else {
                log.error("Weather data fetch returned null");
            }

        } catch (Exception e) {
            log.error("EXCEPTION while fetching weather data for activity {}: {}",
                      activity.getId(), e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Fetch current weather data from OpenWeatherMap.
     */
    private WeatherData fetchCurrentWeather(double lat, double lon, UUID activityId) {
        try {
            String url = String.format("%s?lat=%f&lon=%f&appid=%s&units=metric",
                    OPENWEATHERMAP_API_URL, lat, lon, apiKey);

            String maskedUrl = url.replace(apiKey, "***API_KEY***");
            log.info("Making API request to OpenWeatherMap: {}", maskedUrl);
            log.debug("API URL: {}", OPENWEATHERMAP_API_URL);
            log.debug("Coordinates: lat={}, lon={}", lat, lon);

            long startTime = System.currentTimeMillis();
            String response = restTemplate.getForObject(URI.create(url), String.class);
            long duration = System.currentTimeMillis() - startTime;

            log.info("API request completed in {}ms", duration);

            if (response == null) {
                log.error("API response is NULL - no data returned from OpenWeatherMap");
                return null;
            }

            log.debug("API response length: {} chars", response.length());
            log.debug("API response preview: {}", response.length() > 200 ? response.substring(0, 200) + "..." : response);

            WeatherData weatherData = parseWeatherResponse(response, activityId);

            if (weatherData == null) {
                log.error("Failed to parse weather response");
            } else {
                log.info("Successfully parsed weather data: temp={}°C, condition={}",
                         weatherData.getTemperatureCelsius(), weatherData.getWeatherCondition());
            }

            return weatherData;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("HTTP CLIENT ERROR from OpenWeatherMap API: Status={}, Body={}",
                      e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("HTTP SERVER ERROR from OpenWeatherMap API: Status={}, Body={}",
                      e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("REST CLIENT EXCEPTION calling OpenWeatherMap API: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("UNEXPECTED EXCEPTION fetching current weather: {} - {}",
                      e.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parse OpenWeatherMap API response and create WeatherData entity.
     */
    private WeatherData parseWeatherResponse(String response, UUID activityId) {
        try {
            JsonNode root = objectMapper.readTree(response);

            WeatherData weatherData = new WeatherData();
            weatherData.setActivityId(activityId);

            // Main temperature data
            if (root.has("main")) {
                JsonNode main = root.get("main");
                weatherData.setTemperatureCelsius(getBigDecimal(main, "temp"));
                weatherData.setFeelsLikeCelsius(getBigDecimal(main, "feels_like"));
                weatherData.setHumidity(getInteger(main, "humidity"));
                weatherData.setPressure(getInteger(main, "pressure"));
            }

            // Wind data
            if (root.has("wind")) {
                JsonNode wind = root.get("wind");
                weatherData.setWindSpeedMps(getBigDecimal(wind, "speed"));
                weatherData.setWindDirection(getInteger(wind, "deg"));
            }

            // Weather condition
            if (root.has("weather") && root.get("weather").isArray() && !root.get("weather").isEmpty()) {
                JsonNode weather = root.get("weather").get(0);
                weatherData.setWeatherCondition(getString(weather, "main"));
                weatherData.setWeatherDescription(getString(weather, "description"));
                weatherData.setWeatherIcon(getString(weather, "icon"));
            }

            // Clouds
            if (root.has("clouds")) {
                weatherData.setCloudiness(getInteger(root.get("clouds"), "all"));
            }

            // Visibility
            if (root.has("visibility")) {
                weatherData.setVisibilityMeters(root.get("visibility").asInt());
            }

            // Rain
            if (root.has("rain")) {
                JsonNode rain = root.get("rain");
                if (rain.has("1h")) {
                    weatherData.setPrecipitationMm(BigDecimal.valueOf(rain.get("1h").asDouble()));
                }
            }

            // Snow
            if (root.has("snow")) {
                JsonNode snow = root.get("snow");
                if (snow.has("1h")) {
                    weatherData.setSnowMm(BigDecimal.valueOf(snow.get("1h").asDouble()));
                }
            }

            // Sun times
            if (root.has("sys")) {
                JsonNode sys = root.get("sys");
                if (sys.has("sunrise")) {
                    weatherData.setSunrise(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(sys.get("sunrise").asLong()), ZoneId.systemDefault()));
                }
                if (sys.has("sunset")) {
                    weatherData.setSunset(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(sys.get("sunset").asLong()), ZoneId.systemDefault()));
                }
            }

            weatherData.setFetchedAt(LocalDateTime.now());
            weatherData.setDataSource("openweathermap");

            return weatherData;

        } catch (Exception e) {
            log.error("Error parsing weather response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get weather data for an activity.
     *
     * @param activityId the activity ID
     * @return optional weather data
     */
    public Optional<WeatherData> getWeatherForActivity(UUID activityId) {
        return weatherDataRepository.findByActivityId(activityId);
    }

    /**
     * Delete weather data for an activity.
     *
     * @param activityId the activity ID
     */
    @Transactional
    public void deleteWeatherForActivity(UUID activityId) {
        weatherDataRepository.deleteByActivityId(activityId);
    }

    // Helper methods to safely extract values from JSON
    private BigDecimal getBigDecimal(JsonNode node, String field) {
        if (node.has(field)) {
            return BigDecimal.valueOf(node.get(field).asDouble());
        }
        return null;
    }

    private Integer getInteger(JsonNode node, String field) {
        if (node.has(field)) {
            return node.get(field).asInt();
        }
        return null;
    }

    private String getString(JsonNode node, String field) {
        if (node.has(field)) {
            return node.get(field).asText();
        }
        return null;
    }
}
