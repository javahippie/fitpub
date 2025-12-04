package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing weather data associated with an activity.
 * Weather data is fetched from external APIs (e.g., OpenWeatherMap) based on
 * activity location and timestamp.
 */
@Entity
@Table(name = "weather_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "activity_id", nullable = false, unique = true)
    private UUID activityId;

    @Column(name = "temperature_celsius", precision = 5, scale = 2)
    private BigDecimal temperatureCelsius;

    @Column(name = "feels_like_celsius", precision = 5, scale = 2)
    private BigDecimal feelsLikeCelsius;

    @Column(name = "humidity")
    private Integer humidity; // percentage 0-100

    @Column(name = "pressure")
    private Integer pressure; // hPa

    @Column(name = "wind_speed_mps", precision = 5, scale = 2)
    private BigDecimal windSpeedMps; // meters per second

    @Column(name = "wind_direction")
    private Integer windDirection; // degrees 0-360

    @Column(name = "weather_condition", length = 50)
    private String weatherCondition; // e.g., "Clear", "Clouds", "Rain"

    @Column(name = "weather_description", length = 100)
    private String weatherDescription; // e.g., "light rain", "overcast clouds"

    @Column(name = "weather_icon", length = 10)
    private String weatherIcon; // OpenWeatherMap icon code (e.g., "10d")

    @Column(name = "cloudiness")
    private Integer cloudiness; // percentage 0-100

    @Column(name = "visibility_meters")
    private Integer visibilityMeters;

    @Column(name = "precipitation_mm", precision = 6, scale = 2)
    private BigDecimal precipitationMm; // rainfall/snowfall in mm

    @Column(name = "snow_mm", precision = 6, scale = 2)
    private BigDecimal snowMm; // snowfall in mm (if applicable)

    @Column(name = "sunrise")
    private LocalDateTime sunrise;

    @Column(name = "sunset")
    private LocalDateTime sunset;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    @Column(name = "data_source", length = 50)
    private String dataSource = "openweathermap";

    /**
     * Get wind direction as cardinal direction (N, NE, E, SE, S, SW, W, NW)
     */
    public String getWindDirectionCardinal() {
        if (windDirection == null) return null;

        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(((windDirection % 360) / 45.0)) % 8;
        return directions[index];
    }

    /**
     * Get wind speed in km/h
     */
    public BigDecimal getWindSpeedKmh() {
        if (windSpeedMps == null) return null;
        return windSpeedMps.multiply(BigDecimal.valueOf(3.6));
    }

    /**
     * Get emoji representation of weather condition
     */
    public String getWeatherEmoji() {
        if (weatherCondition == null) return "🌡️";

        return switch (weatherCondition.toLowerCase()) {
            case "clear" -> "☀️";
            case "clouds" -> "☁️";
            case "rain" -> "🌧️";
            case "drizzle" -> "🌦️";
            case "thunderstorm" -> "⛈️";
            case "snow" -> "❄️";
            case "mist", "fog" -> "🌫️";
            default -> "🌡️";
        };
    }
}
