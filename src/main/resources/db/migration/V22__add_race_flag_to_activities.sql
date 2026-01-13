-- Add race/competition flag to activities
-- Race activities use total time for pace calculation instead of moving time

ALTER TABLE activities
ADD COLUMN race BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for filtering race activities
CREATE INDEX idx_activity_race ON activities(race);

COMMENT ON COLUMN activities.race IS 'True if activity is a race/competition. Race activities use total time for pace calculation.';
