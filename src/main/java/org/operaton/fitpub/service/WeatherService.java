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
        if (!weatherEnabled || apiKey == null || apiKey.isBlank()) {
            log.debug("Weather fetching is disabled or API key is not configured");
            return Optional.empty();
        }

        // Check if weather data already exists
        if (weatherDataRepository.existsByActivityId(activity.getId())) {
            log.debug("Weather data already exists for activity {}", activity.getId());
            return weatherDataRepository.findByActivityId(activity.getId());
        }

        // Extract start location from track
        if (activity.getTrackPointsJson() == null || activity.getTrackPointsJson().isEmpty()) {
            log.debug("No track points available for activity {}", activity.getId());
            return Optional.empty();
        }

        try {
            // Get first track point for location
            JsonNode trackPoints = objectMapper.readTree(activity.getTrackPointsJson());
            if (!trackPoints.isArray() || trackPoints.isEmpty()) {
                return Optional.empty();
            }

            JsonNode firstPoint = trackPoints.get(0);
            double lat = firstPoint.get("lat").asDouble();
            double lon = firstPoint.get("lon").asDouble();

            // Check if activity is recent (within 5 days) - use current weather API
            // Otherwise use historical data API (requires paid plan)
            long activityTimestamp = activity.getStartedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
            long currentTimestamp = Instant.now().getEpochSecond();
            long daysDifference = (currentTimestamp - activityTimestamp) / 86400;

            WeatherData weatherData;
            if (daysDifference <= 5) {
                weatherData = fetchCurrentWeather(lat, lon, activity.getId());
            } else {
                log.debug("Activity is older than 5 days, historical weather data requires paid API plan");
                // For historical data, we would use the Time Machine API
                // weatherData = fetchHistoricalWeather(lat, lon, activityTimestamp, activity.getId());
                return Optional.empty();
            }

            if (weatherData != null) {
                return Optional.of(weatherDataRepository.save(weatherData));
            }

        } catch (Exception e) {
            log.error("Error fetching weather data for activity {}: {}", activity.getId(), e.getMessage());
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

            log.debug("Fetching current weather from: {}", url.replace(apiKey, "***"));

            String response = restTemplate.getForObject(URI.create(url), String.class);
            if (response == null) {
                return null;
            }

            return parseWeatherResponse(response, activityId);

        } catch (Exception e) {
            log.error("Error fetching current weather: {}", e.getMessage());
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
