-- Add home location fields to users table for heatmap default view
-- These fields allow users to configure their preferred map center and zoom level

ALTER TABLE users
ADD COLUMN home_latitude DOUBLE PRECISION,
ADD COLUMN home_longitude DOUBLE PRECISION,
ADD COLUMN home_zoom INTEGER;

-- Add comment for documentation
COMMENT ON COLUMN users.home_latitude IS 'Home location latitude for heatmap default view (-90 to 90)';
COMMENT ON COLUMN users.home_longitude IS 'Home location longitude for heatmap default view (-180 to 180)';
COMMENT ON COLUMN users.home_zoom IS 'Home location zoom level for heatmap default view (1-18, default 13)';

-- Create partial index for users with home location set (for potential queries)
CREATE INDEX idx_users_home_location ON users (home_latitude, home_longitude)
WHERE home_latitude IS NOT NULL AND home_longitude IS NOT NULL;
