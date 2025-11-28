-- V3: Create activities table
-- Stores fitness activities with geospatial track data and metrics

CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',

    -- Geospatial data
    simplified_track geometry(LineString, 4326),

    -- Full track data as JSONB
    track_points_json JSONB,

    -- Calculated metrics
    total_distance NUMERIC(10, 2),
    total_duration_seconds BIGINT,
    elevation_gain NUMERIC(8, 2),
    elevation_loss NUMERIC(8, 2),

    -- Original FIT file (using OID for @Lob compatibility)
    raw_fit_file OID,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_activity_type CHECK (activity_type IN (
        'RUN', 'RIDE', 'HIKE', 'WALK', 'SWIM',
        'ALPINE_SKI', 'BACKCOUNTRY_SKI', 'NORDIC_SKI', 'SNOWBOARD',
        'ROWING', 'KAYAKING', 'CANOEING', 'INLINE_SKATING',
        'ROCK_CLIMBING', 'MOUNTAINEERING', 'YOGA', 'WORKOUT', 'OTHER'
    )),
    CONSTRAINT chk_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
    CONSTRAINT chk_time_range CHECK (ended_at > started_at)
);

-- Indexes for performance
CREATE INDEX idx_activity_user_id ON activities(user_id);
CREATE INDEX idx_activity_started_at ON activities(started_at DESC);
CREATE INDEX idx_activity_type ON activities(activity_type);
CREATE INDEX idx_activity_visibility ON activities(visibility);
CREATE INDEX idx_activity_user_started ON activities(user_id, started_at DESC);

-- Spatial index for geospatial queries
CREATE INDEX idx_activity_simplified_track ON activities USING GIST(simplified_track);

-- JSONB GIN index for fast JSON queries
CREATE INDEX idx_activity_track_points_json ON activities USING GIN(track_points_json);

-- Comments
COMMENT ON TABLE activities IS 'Fitness activities with GPS track data and metrics';
COMMENT ON COLUMN activities.simplified_track IS 'Simplified LineString (50-200 points) for map rendering';
COMMENT ON COLUMN activities.track_points_json IS 'Full track data with all sensors stored as JSONB';
COMMENT ON COLUMN activities.raw_fit_file IS 'Original FIT file for re-processing';
