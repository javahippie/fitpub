-- Create weather_data table for storing weather conditions during activities
CREATE TABLE weather_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL UNIQUE,
    temperature_celsius DECIMAL(5, 2),
    feels_like_celsius DECIMAL(5, 2),
    humidity INTEGER, -- percentage 0-100
    pressure INTEGER, -- hPa
    wind_speed_mps DECIMAL(5, 2), -- meters per second
    wind_direction INTEGER, -- degrees 0-360
    weather_condition VARCHAR(50), -- e.g., "Clear", "Clouds", "Rain"
    weather_description VARCHAR(100), -- e.g., "light rain", "overcast clouds"
    weather_icon VARCHAR(10), -- OpenWeatherMap icon code
    cloudiness INTEGER, -- percentage 0-100
    visibility_meters INTEGER,
    precipitation_mm DECIMAL(6, 2), -- rainfall/snowfall in mm
    snow_mm DECIMAL(6, 2), -- snowfall in mm (if applicable)
    sunrise TIMESTAMP,
    sunset TIMESTAMP,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_source VARCHAR(50) DEFAULT 'openweathermap',
    CONSTRAINT fk_weather_activity FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
);

-- Indexes for efficient queries
CREATE INDEX idx_weather_activity_id ON weather_data(activity_id);
CREATE INDEX idx_weather_temperature ON weather_data(temperature_celsius);
CREATE INDEX idx_weather_condition ON weather_data(weather_condition);
