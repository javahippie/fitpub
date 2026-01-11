-- Add indoor flag to activities table
-- Indoor activities (e.g., virtual rides, indoor trainer sessions) should be displayed in timeline
-- but excluded from heatmap generation to avoid polluting outdoor activity visualization

ALTER TABLE activities
ADD COLUMN indoor BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for efficient querying of outdoor activities for heatmap
CREATE INDEX idx_activity_indoor ON activities(indoor);

COMMENT ON COLUMN activities.indoor IS 'Indicates if this is an indoor activity (e.g., virtual rides, trainer sessions). Indoor activities are excluded from heatmap generation.';
