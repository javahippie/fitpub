-- Optimization for account deletion: add index for follow cleanup operations
-- This index improves performance when deleting follows where user is being followed
-- (followingActorUri string lookups during account deletion)

-- Create index on following_actor_uri for faster cleanup during account deletion
-- This only indexes rows where follower_id IS NULL (remote actors following local users)
CREATE INDEX IF NOT EXISTS idx_follows_following_actor_uri_cleanup
ON follows(following_actor_uri)
WHERE follower_id IS NULL;

-- Add comment explaining the index purpose
COMMENT ON INDEX idx_follows_following_actor_uri_cleanup IS
'Optimizes account deletion by speeding up cleanup of remote followers (followingActorUri lookups)';
