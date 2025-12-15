-- Create remote_activities table for storing metadata from federated fitness activities
-- IMPORTANT: This table stores METADATA ONLY - no full track data
-- Maps and tracks are referenced via URLs pointing to the origin server

CREATE TABLE IF NOT EXISTS remote_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ActivityPub identifiers
    activity_uri VARCHAR(512) NOT NULL UNIQUE,
    remote_actor_uri VARCHAR(512) NOT NULL,

    -- Activity metadata
    activity_type VARCHAR(50),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    published_at TIMESTAMP NOT NULL,

    -- Fitness metrics
    total_distance BIGINT,                 -- meters
    total_duration_seconds BIGINT,         -- seconds
    elevation_gain INTEGER,                -- meters
    average_pace_seconds BIGINT,           -- seconds per km
    average_heart_rate INTEGER,            -- BPM
    max_speed DOUBLE PRECISION,            -- km/h
    average_speed DOUBLE PRECISION,        -- km/h
    calories INTEGER,

    -- Remote URLs (point to origin server)
    map_image_url VARCHAR(512),            -- URL to static map image
    track_geojson_url VARCHAR(512),        -- URL to GeoJSON track data (optional)

    -- Visibility
    visibility VARCHAR(20) NOT NULL,       -- PUBLIC, FOLLOWERS, PRIVATE

    -- Full ActivityPub object as JSONB
    activitypub_object JSONB,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_remote_actor FOREIGN KEY (remote_actor_uri)
        REFERENCES remote_actors(actor_uri) ON DELETE CASCADE
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_remote_activity_uri ON remote_activities(activity_uri);
CREATE INDEX idx_remote_activity_actor ON remote_activities(remote_actor_uri);
CREATE INDEX idx_remote_activity_published ON remote_activities(published_at DESC);
CREATE INDEX idx_remote_activity_visibility ON remote_activities(visibility);
CREATE INDEX idx_remote_activity_type ON remote_activities(activity_type);

-- Index for JSONB queries (if needed in the future)
CREATE INDEX idx_remote_activity_jsonb ON remote_activities USING gin(activitypub_object);

-- Comment on table
COMMENT ON TABLE remote_activities IS 'Stores metadata-only for remote fitness activities from federated instances';
COMMENT ON COLUMN remote_activities.activity_uri IS 'Globally unique ActivityPub activity URI';
COMMENT ON COLUMN remote_activities.remote_actor_uri IS 'ActivityPub actor URI of the activity creator';
COMMENT ON COLUMN remote_activities.map_image_url IS 'URL to map image on origin server (no local storage)';
COMMENT ON COLUMN remote_activities.track_geojson_url IS 'URL to GeoJSON on origin server (optional)';
COMMENT ON COLUMN remote_activities.activitypub_object IS 'Full ActivityPub object as JSONB for future re-parsing';
