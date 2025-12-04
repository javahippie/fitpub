-- V12: Add timezone column to activities table
-- This allows storing the timezone where the activity was recorded,
-- enabling proper display of activity times in the athlete's local timezone

-- Add timezone column (nullable for backward compatibility with existing activities)
ALTER TABLE activities
ADD COLUMN timezone VARCHAR(50);

-- Set default timezone to UTC for existing activities
UPDATE activities
SET timezone = 'UTC'
WHERE timezone IS NULL;

-- Add comment explaining the column
COMMENT ON COLUMN activities.timezone IS 'IANA timezone ID where the activity was recorded (e.g., Europe/Berlin, America/New_York). Used to display activity times in athlete''s local timezone.';
