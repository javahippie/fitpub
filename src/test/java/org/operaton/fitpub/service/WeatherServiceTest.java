package org.operaton.fitpub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.WeatherData;
import org.operaton.fitpub.repository.WeatherDataRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WeatherService.
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private WeatherDataRepository weatherDataRepository;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WeatherService weatherService;

    private Activity testActivity;
    private UUID activityId;

    // Sample OpenWeatherMap API response
    private static final String SAMPLE_WEATHER_RESPONSE = """
        {
          "coord": {"lon": 8.2552, "lat": 49.9894},
          "weather": [
            {
              "id": 800,
              "main": "Clear",
              "description": "clear sky",
              "icon": "01d"
            }
          ],
          "base": "stations",
          "main": {
            "temp": 15.5,
            "feels_like": 14.2,
            "temp_min": 13.0,
            "temp_max": 17.0,
            "pressure": 1013,
            "humidity": 65
          },
          "visibility": 10000,
          "wind": {
            "speed": 3.5,
            "deg": 180
          },
          "clouds": {
            "all": 20
          },
          "dt": 1700758089,
          "sys": {
            "type": 2,
            "id": 2012516,
            "country": "DE",
            "sunrise": 1700721600,
            "sunset": 1700757600
          },
          "timezone": 3600,
          "id": 2873891,
          "name": "Mannheim",
          "cod": 200
        }
        """;

    @BeforeEach
    void setUp() {
        activityId = UUID.randomUUID();
        testActivity = new Activity();
        testActivity.setId(activityId);
        testActivity.setStartedAt(LocalDateTime.now().minusDays(1)); // Recent activity

        // Inject the real RestTemplate mock and set config values
        ReflectionTestUtils.setField(weatherService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(weatherService, "weatherEnabled", true);
        ReflectionTestUtils.setField(weatherService, "apiKey", "test-api-key-12345678901234567890");
    }

    @Test
    @DisplayName("Should successfully fetch weather with SHORT field names (lat/lon)")
    void testFetchWeather_ShortFieldNames() {
        // Track points with SHORT field names
        String trackPointsJson = """
            [
              {
                "timestamp": "2025-11-23T18:08:09",
                "lat": 49.98939173296094,
                "lon": 8.255225038155913,
                "elevation": 100.5,
                "heartRate": 116
              }
            ]
            """;
        testActivity.setTrackPointsJson(trackPointsJson);

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(SAMPLE_WEATHER_RESPONSE);
        when(weatherDataRepository.save(any(WeatherData.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isPresent());
        WeatherData weatherData = result.get();
        assertEquals(activityId, weatherData.getActivityId());
        assertEquals(new BigDecimal("15.5"), weatherData.getTemperatureCelsius());
        assertEquals("Clear", weatherData.getWeatherCondition());

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
        verify(weatherDataRepository, times(1)).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should successfully fetch weather with LONG field names (latitude/longitude)")
    void testFetchWeather_LongFieldNames() {
        // Track points with LONG field names (as used in production)
        String trackPointsJson = """
            [
              {
                "timestamp": "2025-11-23T18:08:09",
                "latitude": 49.98939173296094,
                "longitude": 8.255225038155913,
                "elevation": null,
                "heartRate": 116,
                "cadence": null,
                "power": null,
                "speed": null,
                "temperature": null,
                "distance": null
              }
            ]
            """;
        testActivity.setTrackPointsJson(trackPointsJson);

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(SAMPLE_WEATHER_RESPONSE);
        when(weatherDataRepository.save(any(WeatherData.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isPresent());
        WeatherData weatherData = result.get();
        assertEquals(activityId, weatherData.getActivityId());
        assertEquals(new BigDecimal("15.5"), weatherData.getTemperatureCelsius());
        assertEquals(new BigDecimal("14.2"), weatherData.getFeelsLikeCelsius());
        assertEquals(65, weatherData.getHumidity());
        assertEquals(1013, weatherData.getPressure());
        assertEquals(new BigDecimal("3.5"), weatherData.getWindSpeedMps());
        assertEquals("clear sky", weatherData.getWeatherDescription());

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
        verify(weatherDataRepository, times(1)).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should return empty when weather is disabled in config")
    void testFetchWeather_Disabled() {
        ReflectionTestUtils.setField(weatherService, "weatherEnabled", false);
        testActivity.setTrackPointsJson("[{\"lat\":50.0,\"lon\":8.0}]");

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
        verify(restTemplate, never()).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    @DisplayName("Should return empty when API key is not configured")
    void testFetchWeather_NoApiKey() {
        ReflectionTestUtils.setField(weatherService, "apiKey", "");
        testActivity.setTrackPointsJson("[{\"lat\":50.0,\"lon\":8.0}]");

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
        verify(restTemplate, never()).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    @DisplayName("Should return cached weather if it already exists")
    void testFetchWeather_Cached() {
        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        WeatherData cachedWeather = new WeatherData();
        cachedWeather.setActivityId(activityId);
        cachedWeather.setTemperatureCelsius(new BigDecimal("20.0"));

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(true);
        when(weatherDataRepository.findByActivityId(activityId)).thenReturn(Optional.of(cachedWeather));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("20.0"), result.get().getTemperatureCelsius());
        verify(restTemplate, never()).getForObject(any(URI.class), eq(String.class));
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should return empty when track points JSON is null")
    void testFetchWeather_NoTrackPoints() {
        testActivity.setTrackPointsJson(null);

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should return empty when track points JSON is empty")
    void testFetchWeather_EmptyTrackPoints() {
        testActivity.setTrackPointsJson("");

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should return empty when track points array is empty")
    void testFetchWeather_EmptyArray() {
        testActivity.setTrackPointsJson("[]");

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should return empty when track points missing lat/lon fields")
    void testFetchWeather_MissingCoordinates() {
        String trackPointsJson = """
            [
              {
                "timestamp": "2025-11-23T18:08:09",
                "elevation": 100.5,
                "heartRate": 116
              }
            ]
            """;
        testActivity.setTrackPointsJson(trackPointsJson);

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
        verify(restTemplate, never()).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    @DisplayName("Should return empty for old activities (>5 days)")
    void testFetchWeather_OldActivity() {
        testActivity.setStartedAt(LocalDateTime.now().minusDays(10)); // Old activity
        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(restTemplate, never()).getForObject(any(URI.class), eq(String.class));
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should handle 401 authentication error from API")
    void testFetchWeather_AuthenticationError() {
        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenThrow(new HttpClientErrorException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "{\"cod\":401,\"message\":\"Invalid API key\"}".getBytes(),
                null
            ));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should handle network/connection errors")
    void testFetchWeather_NetworkError() {
        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("Connection timeout"));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should handle malformed JSON response from API")
    void testFetchWeather_MalformedResponse() {
        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("this is not valid JSON");

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isEmpty());
        verify(weatherDataRepository, never()).save(any(WeatherData.class));
    }

    @Test
    @DisplayName("Should parse all weather fields correctly")
    void testParseWeatherResponse_AllFields() {
        String responseWithRain = """
            {
              "main": {
                "temp": 10.0,
                "feels_like": 8.5,
                "pressure": 1015,
                "humidity": 80
              },
              "weather": [{"main": "Rain", "description": "light rain", "icon": "10d"}],
              "wind": {"speed": 5.5, "deg": 270},
              "clouds": {"all": 75},
              "visibility": 8000,
              "rain": {"1h": 2.5},
              "sys": {"sunrise": 1700721600, "sunset": 1700757600}
            }
            """;

        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(responseWithRain);
        when(weatherDataRepository.save(any(WeatherData.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isPresent());
        WeatherData weatherData = result.get();
        assertEquals(new BigDecimal("10.0"), weatherData.getTemperatureCelsius());
        assertEquals(new BigDecimal("8.5"), weatherData.getFeelsLikeCelsius());
        assertEquals("Rain", weatherData.getWeatherCondition());
        assertEquals("light rain", weatherData.getWeatherDescription());
        assertEquals(new BigDecimal("5.5"), weatherData.getWindSpeedMps());
        assertEquals(270, weatherData.getWindDirection());
        assertEquals(75, weatherData.getCloudiness());
        assertEquals(8000, weatherData.getVisibilityMeters());
        assertEquals(new BigDecimal("2.5"), weatherData.getPrecipitationMm());
        assertNotNull(weatherData.getSunrise());
        assertNotNull(weatherData.getSunset());
        assertEquals("openweathermap", weatherData.getDataSource());
        assertNotNull(weatherData.getFetchedAt());
    }

    @Test
    @DisplayName("Should handle response with missing optional fields")
    void testParseWeatherResponse_MinimalFields() {
        String minimalResponse = """
            {
              "main": {
                "temp": 15.0,
                "feels_like": 14.0,
                "pressure": 1010,
                "humidity": 60
              },
              "weather": [{"main": "Clouds", "description": "few clouds", "icon": "02d"}]
            }
            """;

        testActivity.setTrackPointsJson("[{\"latitude\":50.0,\"longitude\":8.0}]");

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(minimalResponse);
        when(weatherDataRepository.save(any(WeatherData.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isPresent());
        WeatherData weatherData = result.get();
        assertEquals(new BigDecimal("15.0"), weatherData.getTemperatureCelsius());
        assertEquals("Clouds", weatherData.getWeatherCondition());
        assertNull(weatherData.getWindSpeedMps());
        assertNull(weatherData.getPrecipitationMm());
    }

    @Test
    @DisplayName("Should get existing weather data by activity ID")
    void testGetWeatherForActivity() {
        WeatherData weatherData = new WeatherData();
        weatherData.setActivityId(activityId);

        when(weatherDataRepository.findByActivityId(activityId)).thenReturn(Optional.of(weatherData));

        Optional<WeatherData> result = weatherService.getWeatherForActivity(activityId);

        assertTrue(result.isPresent());
        assertEquals(activityId, result.get().getActivityId());
    }

    @Test
    @DisplayName("Should delete weather data by activity ID")
    void testDeleteWeatherForActivity() {
        doNothing().when(weatherDataRepository).deleteByActivityId(activityId);

        weatherService.deleteWeatherForActivity(activityId);

        verify(weatherDataRepository, times(1)).deleteByActivityId(activityId);
    }

    @Test
    @DisplayName("Should handle mixed field names (lat + longitude)")
    void testFetchWeather_MixedFieldNames() {
        // Edge case: one coordinate uses short form, other uses long form
        String trackPointsJson = """
            [
              {
                "timestamp": "2025-11-23T18:08:09",
                "lat": 49.98939173296094,
                "longitude": 8.255225038155913,
                "elevation": 100.5
              }
            ]
            """;
        testActivity.setTrackPointsJson(trackPointsJson);

        when(weatherDataRepository.existsByActivityId(activityId)).thenReturn(false);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(SAMPLE_WEATHER_RESPONSE);
        when(weatherDataRepository.save(any(WeatherData.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<WeatherData> result = weatherService.fetchWeatherForActivity(testActivity);

        assertTrue(result.isPresent());
        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
    }
}
