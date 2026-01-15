package net.javahippie.fitpub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.TrackPoint;
import net.javahippie.fitpub.model.entity.WeatherData;
import net.javahippie.fitpub.repository.WeatherDataRepository;
import org.locationtech.jts.geom.Point;
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
        log.info("Weather configuration: enabled={}, API key configured={}, API key length={}",
                weatherEnabled, (apiKey != null && !apiKey.isBlank()),
                (apiKey != null ? apiKey.length() : 0));

        if (!weatherEnabled) {
            log.warn("Weather fetching is DISABLED in configuration (fitpub.weather.enabled=false). " +
                     "Set fitpub.weather.enabled=true in application properties to enable.");
            return Optional.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.error("Weather API key is NOT CONFIGURED (fitpub.weather.api-key is empty). " +
                      "Please set fitpub.weather.api-key in application properties.");
            return Optional.empty();
        }

        log.debug("Weather API key present: length={} chars, first 4 chars={}...",
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
            Optional<TrackPoint> trackPoint = activity.findFirstTrackpoint();

            if (trackPoint.isEmpty()) {
                return Optional.empty();
            } else {
                var resolvedTrackPoint = trackPoint.get();
                // Check if activity is recent (within 5 days) because it's free to use. Don't call other timeframes, expensive.
                long activityTimestamp = activity.getStartedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
                long currentTimestamp = Instant.now().getEpochSecond();
                long daysDifference = (currentTimestamp - activityTimestamp) / 86400;

                log.info("Activity started at: {}, days ago: {}", activity.getStartedAt(), daysDifference);

                WeatherData weatherData;
                if (daysDifference <= 5) {
                    log.info("Activity is RECENT ({} days old, within 5 day threshold), fetching current weather from OpenWeatherMap", daysDifference);
                    weatherData = fetchCurrentWeather(resolvedTrackPoint.lat(), resolvedTrackPoint.lon(), activity.getId());
                } else {
                    log.warn("Activity is {} days old (exceeds 5 day threshold). Historical weather data requires OpenWeatherMap paid API plan. Skipping weather fetch.", daysDifference);
                    return Optional.empty();
                }

                if (weatherData != null) {
                    log.info("Successfully fetched and parsed weather data. Attempting to save to database...");
                    try {
                        WeatherData saved = weatherDataRepository.save(weatherData);
                        log.info("Weather data SUCCESSFULLY SAVED to database with ID: {}", saved.getId());
                        return Optional.of(saved);
                    } catch (Exception e) {
                        log.error("FAILED to save weather data to database: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                } else {
                    log.error("Weather data fetch returned NULL - check API errors above");
                }

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
        log.info("=== fetchCurrentWeather START === activityId={}, lat={}, lon={}", activityId, lat, lon);
        try {
            String url = String.format("%s?lat=%f&lon=%f&appid=%s&units=metric",
                    OPENWEATHERMAP_API_URL, lat, lon, apiKey);

            String maskedUrl = url.replace(apiKey, "***API_KEY***");
            log.info("Constructed OpenWeatherMap API URL: {}", maskedUrl);
            log.info("Request parameters: lat={}, lon={}, units=metric", lat, lon);

            long startTime = System.currentTimeMillis();
            log.info("Sending HTTP GET request to OpenWeatherMap...");
            String response = restTemplate.getForObject(URI.create(url), String.class);
            long duration = System.currentTimeMillis() - startTime;

            log.info("HTTP request completed in {}ms, response received", duration);

            if (response == null) {
                log.error("API response is NULL - RestTemplate returned null, no data from OpenWeatherMap");
                return null;
            }

            log.info("API response received: {} characters", response.length());
            log.info("API response (first 300 chars): {}",
                    response.length() > 300 ? response.substring(0, 300) + "..." : response);

            log.info("Parsing weather response JSON...");
            WeatherData weatherData = parseWeatherResponse(response, activityId);

            if (weatherData == null) {
                log.error("FAILED to parse weather response - see parsing errors above");
            } else {
                log.info("Successfully parsed weather data: temp={}°C, feels_like={}°C, condition='{}', description='{}', humidity={}%, pressure={} hPa, wind={} m/s",
                        weatherData.getTemperatureCelsius(),
                        weatherData.getFeelsLikeCelsius(),
                        weatherData.getWeatherCondition(),
                        weatherData.getWeatherDescription(),
                        weatherData.getHumidity(),
                        weatherData.getPressure(),
                        weatherData.getWindSpeedMps());
            }

            log.info("=== fetchCurrentWeather END === success={}", (weatherData != null));
            return weatherData;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("=== HTTP CLIENT ERROR (4xx) from OpenWeatherMap API ===");
            log.error("Status Code: {}", e.getStatusCode());
            log.error("Status Text: {}", e.getStatusText());
            log.error("Response Body: {}", e.getResponseBodyAsString());
            log.error("Request URL (masked): {}", OPENWEATHERMAP_API_URL + "?lat=" + lat + "&lon=" + lon + "&appid=***&units=metric");
            if (e.getStatusCode().value() == 401) {
                log.error("AUTHENTICATION FAILED - Check your OpenWeatherMap API key is valid and active");
            } else if (e.getStatusCode().value() == 404) {
                log.error("API ENDPOINT NOT FOUND - Check coordinates are valid: lat={}, lon={}", lat, lon);
            } else if (e.getStatusCode().value() == 429) {
                log.error("RATE LIMIT EXCEEDED - Too many API requests. Check your OpenWeatherMap plan limits.");
            }
            log.error("Exception details:", e);
            return null;
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("=== HTTP SERVER ERROR (5xx) from OpenWeatherMap API ===");
            log.error("Status Code: {}", e.getStatusCode());
            log.error("Status Text: {}", e.getStatusText());
            log.error("Response Body: {}", e.getResponseBodyAsString());
            log.error("OpenWeatherMap service may be experiencing issues. Try again later.");
            log.error("Exception details:", e);
            return null;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("=== NETWORK/CONNECTION ERROR accessing OpenWeatherMap API ===");
            log.error("Error message: {}", e.getMessage());
            log.error("This could indicate: DNS resolution failure, network connectivity issues, firewall blocking, or SSL certificate problems");
            log.error("API URL attempted: {}", OPENWEATHERMAP_API_URL);
            log.error("Exception details:", e);
            return null;
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("=== REST CLIENT EXCEPTION calling OpenWeatherMap API ===");
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Exception details:", e);
            return null;
        } catch (Exception e) {
            log.error("=== UNEXPECTED EXCEPTION fetching current weather ===");
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Activity ID: {}", activityId);
            log.error("Coordinates: lat={}, lon={}", lat, lon);
            log.error("Full stack trace:", e);
            return null;
        }
    }

    /**
     * Parse OpenWeatherMap API response and create WeatherData entity.
     */
    private WeatherData parseWeatherResponse(String response, UUID activityId) {
        log.debug("=== parseWeatherResponse START === activityId={}", activityId);
        try {
            JsonNode root = objectMapper.readTree(response);
            log.debug("JSON parsed successfully, root node present: {}", root != null);

            WeatherData weatherData = new WeatherData();
            weatherData.setActivityId(activityId);

            // Main temperature data
            if (root.has("main")) {
                JsonNode main = root.get("main");
                log.debug("Parsing 'main' section: {}", main);
                weatherData.setTemperatureCelsius(getBigDecimal(main, "temp"));
                weatherData.setFeelsLikeCelsius(getBigDecimal(main, "feels_like"));
                weatherData.setHumidity(getInteger(main, "humidity"));
                weatherData.setPressure(getInteger(main, "pressure"));
                log.debug("Extracted main data: temp={}, feels_like={}, humidity={}, pressure={}",
                        weatherData.getTemperatureCelsius(), weatherData.getFeelsLikeCelsius(),
                        weatherData.getHumidity(), weatherData.getPressure());
            } else {
                log.warn("Response JSON does not contain 'main' section");
            }

            // Wind data
            if (root.has("wind")) {
                JsonNode wind = root.get("wind");
                log.debug("Parsing 'wind' section: {}", wind);
                weatherData.setWindSpeedMps(getBigDecimal(wind, "speed"));
                weatherData.setWindDirection(getInteger(wind, "deg"));
                log.debug("Extracted wind data: speed={} m/s, direction={} degrees",
                        weatherData.getWindSpeedMps(), weatherData.getWindDirection());
            } else {
                log.debug("Response JSON does not contain 'wind' section");
            }

            // Weather condition
            if (root.has("weather") && root.get("weather").isArray() && !root.get("weather").isEmpty()) {
                JsonNode weather = root.get("weather").get(0);
                log.debug("Parsing 'weather' array (first element): {}", weather);
                weatherData.setWeatherCondition(getString(weather, "main"));
                weatherData.setWeatherDescription(getString(weather, "description"));
                weatherData.setWeatherIcon(getString(weather, "icon"));
                log.debug("Extracted weather condition: main='{}', description='{}', icon='{}'",
                        weatherData.getWeatherCondition(), weatherData.getWeatherDescription(),
                        weatherData.getWeatherIcon());
            } else {
                log.warn("Response JSON does not contain valid 'weather' array");
            }

            // Clouds
            if (root.has("clouds")) {
                weatherData.setCloudiness(getInteger(root.get("clouds"), "all"));
                log.debug("Extracted cloudiness: {}%", weatherData.getCloudiness());
            }

            // Visibility
            if (root.has("visibility")) {
                weatherData.setVisibilityMeters(root.get("visibility").asInt());
                log.debug("Extracted visibility: {} meters", weatherData.getVisibilityMeters());
            }

            // Rain
            if (root.has("rain")) {
                JsonNode rain = root.get("rain");
                if (rain.has("1h")) {
                    weatherData.setPrecipitationMm(BigDecimal.valueOf(rain.get("1h").asDouble()));
                    log.debug("Extracted rain: {} mm/h", weatherData.getPrecipitationMm());
                }
            }

            // Snow
            if (root.has("snow")) {
                JsonNode snow = root.get("snow");
                if (snow.has("1h")) {
                    weatherData.setSnowMm(BigDecimal.valueOf(snow.get("1h").asDouble()));
                    log.debug("Extracted snow: {} mm/h", weatherData.getSnowMm());
                }
            }

            // Sun times
            if (root.has("sys")) {
                JsonNode sys = root.get("sys");
                if (sys.has("sunrise")) {
                    weatherData.setSunrise(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(sys.get("sunrise").asLong()), ZoneId.systemDefault()));
                    log.debug("Extracted sunrise: {}", weatherData.getSunrise());
                }
                if (sys.has("sunset")) {
                    weatherData.setSunset(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(sys.get("sunset").asLong()), ZoneId.systemDefault()));
                    log.debug("Extracted sunset: {}", weatherData.getSunset());
                }
            }

            weatherData.setFetchedAt(LocalDateTime.now());
            weatherData.setDataSource("openweathermap");

            log.info("Successfully parsed complete weather data");
            log.debug("=== parseWeatherResponse END === success=true");
            return weatherData;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("=== JSON PARSING ERROR ===");
            log.error("Failed to parse weather response as JSON");
            log.error("Response content: {}", response);
            log.error("Parse error: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("=== UNEXPECTED ERROR parsing weather response ===");
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Response content: {}", response);
            log.error("Full stack trace:", e);
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
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            try {
                return BigDecimal.valueOf(node.get(field).asDouble());
            } catch (Exception e) {
                log.warn("Failed to extract BigDecimal from field '{}': {}", field, e.getMessage());
                return null;
            }
        }
        log.debug("Field '{}' not found or is null in node", field);
        return null;
    }

    private Integer getInteger(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            try {
                return node.get(field).asInt();
            } catch (Exception e) {
                log.warn("Failed to extract Integer from field '{}': {}", field, e.getMessage());
                return null;
            }
        }
        log.debug("Field '{}' not found or is null in node", field);
        return null;
    }

    private String getString(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            try {
                return node.get(field).asText();
            } catch (Exception e) {
                log.warn("Failed to extract String from field '{}': {}", field, e.getMessage());
                return null;
            }
        }
        log.debug("Field '{}' not found or is null in node", field);
        return null;
    }
}
