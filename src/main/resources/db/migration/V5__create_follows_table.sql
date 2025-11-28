-- V5: Create follows table
-- Stores follow relationships between local and remote actors

CREATE TABLE follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID REFERENCES users(id) ON DELETE CASCADE,
    following_actor_uri VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    activity_id VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_follow_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

-- Indexes for performance
CREATE INDEX idx_follower_id ON follows(follower_id);
CREATE INDEX idx_following_actor_uri ON follows(following_actor_uri);
CREATE INDEX idx_follow_status ON follows(status);
CREATE INDEX idx_follow_activity_id ON follows(activity_id);

-- Unique constraint to prevent duplicate follows
CREATE UNIQUE INDEX idx_unique_follow ON follows(follower_id, following_actor_uri)
WHERE follower_id IS NOT NULL;

-- Comments
COMMENT ON TABLE follows IS 'Follow relationships between local and remote actors for ActivityPub federation';
COMMENT ON COLUMN follows.follower_id IS 'Local user ID (null for remote followers)';
COMMENT ON COLUMN follows.following_actor_uri IS 'ActivityPub actor URI of the followed user';
COMMENT ON COLUMN follows.activity_id IS 'ActivityPub activity ID for the follow request';
COMMENT ON COLUMN follows.status IS 'Status of the follow relationship';
