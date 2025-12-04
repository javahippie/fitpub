-- Personal Records Table
CREATE TABLE personal_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_type VARCHAR(50) NOT NULL,
    record_type VARCHAR(50) NOT NULL, -- FASTEST_1K, FASTEST_5K, FASTEST_10K, FASTEST_HALF_MARATHON, FASTEST_MARATHON, LONGEST_DISTANCE, LONGEST_DURATION, HIGHEST_ELEVATION_GAIN, MAX_SPEED
    value DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(20) NOT NULL, -- seconds, meters, meters/second
    activity_id UUID REFERENCES activities(id) ON DELETE SET NULL,
    achieved_at TIMESTAMP NOT NULL,
    previous_value DECIMAL(10, 2),
    previous_achieved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, activity_type, record_type)
);

CREATE INDEX idx_personal_records_user ON personal_records(user_id);
CREATE INDEX idx_personal_records_type ON personal_records(activity_type, record_type);
CREATE INDEX idx_personal_records_achieved ON personal_records(achieved_at DESC);

-- Achievements/Badges Table
CREATE TABLE achievements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_type VARCHAR(100) NOT NULL, -- FIRST_ACTIVITY, DISTANCE_10K, DISTANCE_100K, DISTANCE_1000K, STREAK_7, STREAK_30, EARLY_BIRD, NIGHT_OWL, MOUNTAINEER, EXPLORER, CONSISTENT_WEEK, etc.
    name VARCHAR(100) NOT NULL,
    description TEXT,
    badge_icon VARCHAR(50), -- emoji or icon class
    badge_color VARCHAR(20),
    earned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    activity_id UUID REFERENCES activities(id) ON DELETE SET NULL,
    metadata JSONB, -- Additional data like distance reached, streak count, etc.
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, achievement_type)
);

CREATE INDEX idx_achievements_user ON achievements(user_id);
CREATE INDEX idx_achievements_earned ON achievements(earned_at DESC);
CREATE INDEX idx_achievements_type ON achievements(achievement_type);

-- Training Load Table (for calculating training stress and recovery)
CREATE TABLE training_load (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    activity_count INTEGER DEFAULT 0,
    total_duration_seconds BIGINT DEFAULT 0,
    total_distance_meters DECIMAL(10, 2) DEFAULT 0,
    total_elevation_gain_meters DECIMAL(10, 2) DEFAULT 0,
    training_stress_score DECIMAL(6, 2), -- Calculated training load
    acute_training_load DECIMAL(6, 2), -- 7-day rolling average
    chronic_training_load DECIMAL(6, 2), -- 28-day rolling average
    training_stress_balance DECIMAL(6, 2), -- ATL - CTL (positive = fresh, negative = fatigued)
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, date)
);

CREATE INDEX idx_training_load_user_date ON training_load(user_id, date DESC);
CREATE INDEX idx_training_load_date ON training_load(date DESC);

-- Weekly/Monthly Summaries Table
CREATE TABLE activity_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_type VARCHAR(20) NOT NULL, -- WEEK, MONTH, YEAR
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    activity_count INTEGER DEFAULT 0,
    total_duration_seconds BIGINT DEFAULT 0,
    total_distance_meters DECIMAL(10, 2) DEFAULT 0,
    total_elevation_gain_meters DECIMAL(10, 2) DEFAULT 0,
    avg_speed_mps DECIMAL(6, 2),
    max_speed_mps DECIMAL(6, 2),
    activity_type_breakdown JSONB, -- {"Run": 5, "Ride": 3, "Hike": 2}
    personal_records_set INTEGER DEFAULT 0,
    achievements_earned INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, period_type, period_start)
);

CREATE INDEX idx_activity_summaries_user ON activity_summaries(user_id);
CREATE INDEX idx_activity_summaries_period ON activity_summaries(user_id, period_type, period_start DESC);

COMMENT ON TABLE personal_records IS 'Tracks personal records (PRs) for various metrics across different activity types';
COMMENT ON TABLE achievements IS 'Gamification badges earned by users for various accomplishments';
COMMENT ON TABLE training_load IS 'Daily training load metrics for tracking fitness and fatigue';
COMMENT ON TABLE activity_summaries IS 'Pre-calculated weekly/monthly/yearly activity summaries for performance';
