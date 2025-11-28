-- V4: Create activity_metrics table
-- Stores calculated metrics and statistics for activities

CREATE TABLE activity_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL UNIQUE REFERENCES activities(id) ON DELETE CASCADE,

    -- Speed metrics
    average_speed NUMERIC(8, 2),
    max_speed NUMERIC(8, 2),
    average_pace_seconds BIGINT,

    -- Heart rate metrics
    average_heart_rate INTEGER,
    max_heart_rate INTEGER,

    -- Cadence metrics
    average_cadence INTEGER,
    max_cadence INTEGER,

    -- Power metrics
    average_power INTEGER,
    max_power INTEGER,
    normalized_power INTEGER,

    -- Other metrics
    calories INTEGER,
    average_temperature NUMERIC(5, 2),

    -- Elevation metrics
    max_elevation NUMERIC(8, 2),
    min_elevation NUMERIC(8, 2),
    total_ascent NUMERIC(8, 2),
    total_descent NUMERIC(8, 2),

    -- Time metrics
    moving_time_seconds BIGINT,
    stopped_time_seconds BIGINT,

    -- Step counter
    total_steps INTEGER,

    -- Training metrics
    training_stress_score NUMERIC(8, 2)
);

-- Index on activity_id for fast lookup
CREATE UNIQUE INDEX idx_activity_metrics_activity_id ON activity_metrics(activity_id);

-- Comments
COMMENT ON TABLE activity_metrics IS 'Calculated metrics and statistics for activities';
COMMENT ON COLUMN activity_metrics.average_pace_seconds IS 'Average pace in seconds per kilometer';
COMMENT ON COLUMN activity_metrics.normalized_power IS 'Normalized Power (NP) for cycling power analysis';
COMMENT ON COLUMN activity_metrics.training_stress_score IS 'TSS - Training Stress Score';
