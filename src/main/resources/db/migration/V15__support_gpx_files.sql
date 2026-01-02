-- Migration to support GPX files in addition to FIT files
-- Renames raw_fit_file column and adds source_file_format tracking

-- Rename raw_fit_file column to raw_activity_file (more generic name)
ALTER TABLE activities RENAME COLUMN raw_fit_file TO raw_activity_file;

-- Add source_file_format column to track the original file format
ALTER TABLE activities ADD COLUMN source_file_format VARCHAR(10) DEFAULT 'FIT';

-- Backfill existing records (all existing activities are from FIT files)
UPDATE activities SET source_file_format = 'FIT' WHERE source_file_format IS NULL;

-- Make source_file_format NOT NULL now that all records are backfilled
ALTER TABLE activities ALTER COLUMN source_file_format SET NOT NULL;

-- Add check constraint to ensure only valid formats are stored
ALTER TABLE activities ADD CONSTRAINT chk_source_file_format
    CHECK (source_file_format IN ('FIT', 'GPX'));

-- Add index for faster filtering by format (optional but helpful for analytics)
CREATE INDEX idx_activities_source_format ON activities(source_file_format);

-- Add comment for documentation
COMMENT ON COLUMN activities.source_file_format IS 'Original file format: FIT (Garmin/Wahoo devices) or GPX (GPS Exchange Format)';
COMMENT ON COLUMN activities.raw_activity_file IS 'Raw activity file bytes for re-processing with updated algorithms';
