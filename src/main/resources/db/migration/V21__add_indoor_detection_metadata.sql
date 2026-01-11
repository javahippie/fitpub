-- Add SubSport and indoor detection method columns to activities table
-- These columns provide metadata about how indoor activities were detected

ALTER TABLE activities
ADD COLUMN sub_sport VARCHAR(50),
ADD COLUMN indoor_detection_method VARCHAR(20);

COMMENT ON COLUMN activities.sub_sport IS 'SubSport from FIT file (e.g., INDOOR_CYCLING, TREADMILL, ROAD, MOUNTAIN, TRAIL). NULL for GPX files or if not available.';
COMMENT ON COLUMN activities.indoor_detection_method IS 'How the indoor flag was determined: FIT_SUBSPORT, GPX_EXTENSION, HEURISTIC_NO_GPS, HEURISTIC_STATIONARY, MANUAL, or NULL for legacy activities.';
