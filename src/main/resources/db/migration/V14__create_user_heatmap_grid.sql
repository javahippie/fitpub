-- Create user_heatmap_grid table for aggregated track density
CREATE TABLE user_heatmap_grid (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grid_cell GEOMETRY(Point, 4326) NOT NULL,
    point_count INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_grid_cell UNIQUE(user_id, grid_cell)
);

-- Index for user lookups
CREATE INDEX idx_user_heatmap_grid_user ON user_heatmap_grid(user_id);

-- Spatial index for grid cell lookups
CREATE INDEX idx_user_heatmap_grid_spatial ON user_heatmap_grid USING GIST(grid_cell);

-- Index for time-based queries
CREATE INDEX idx_user_heatmap_grid_updated ON user_heatmap_grid(last_updated);
