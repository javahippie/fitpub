-- Migration V8: Add remote_actor_uri column to follows table

-- Add the column
ALTER TABLE follows ADD COLUMN remote_actor_uri VARCHAR(512);

-- Add index for remote_actor_uri
CREATE INDEX idx_follows_remote_actor_uri ON follows(remote_actor_uri) WHERE remote_actor_uri IS NOT NULL;

-- Update the constraint to allow either follower_id OR remote_actor_uri
ALTER TABLE follows ALTER COLUMN follower_id DROP NOT NULL;

-- Add check constraint to ensure either follower_id or remote_actor_uri is set (but not both)
ALTER TABLE follows ADD CONSTRAINT chk_follows_actor CHECK (
    (follower_id IS NOT NULL AND remote_actor_uri IS NULL) OR
    (follower_id IS NULL AND remote_actor_uri IS NOT NULL)
);

-- Add comment for documentation
COMMENT ON COLUMN follows.remote_actor_uri IS 'Remote ActivityPub actor URI for remote-to-local follows (null if local follower)';
