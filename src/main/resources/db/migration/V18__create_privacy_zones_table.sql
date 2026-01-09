CREATE EXTENSION btree_gist;

-- Privacy Zones Table
-- Stores user-defined geographic zones where GPS data should be filtered
CREATE TABLE privacy_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,

    -- Center point of the privacy zone (PostGIS Point geometry)
    center_point geometry(Point, 4326) NOT NULL,

    -- Radius in meters
    radius_meters INTEGER NOT NULL,

    -- Whether this zone is active (allows temporary disabling)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_radius_positive CHECK (radius_meters > 0 AND radius_meters <= 10000)
);

-- Indexes for efficient spatial queries
CREATE INDEX idx_privacy_zones_user ON privacy_zones(user_id) WHERE is_active = TRUE;
CREATE INDEX idx_privacy_zones_center_point ON privacy_zones USING GIST(center_point);

-- Composite spatial index for user + geometry queries
CREATE INDEX idx_privacy_zones_user_spatial ON privacy_zones USING GIST(user_id, center_point) WHERE is_active = TRUE;

COMMENT ON TABLE privacy_zones IS 'User-defined geographic zones for GPS track privacy filtering';
COMMENT ON COLUMN privacy_zones.center_point IS 'True center of privacy zone (not the randomized display center)';
COMMENT ON COLUMN privacy_zones.radius_meters IS 'Radius of privacy zone in meters (max 10km for safety)';
