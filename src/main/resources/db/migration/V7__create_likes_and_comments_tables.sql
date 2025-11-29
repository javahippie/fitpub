-- Migration V7: Create likes and comments tables for activity interactions

-- Create likes table
CREATE TABLE likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    remote_actor_uri VARCHAR(500),
    display_name VARCHAR(200),
    avatar_url VARCHAR(500),
    activity_pub_id VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints: either user_id OR remote_actor_uri must be set, but not both
    CONSTRAINT chk_likes_actor CHECK (
        (user_id IS NOT NULL AND remote_actor_uri IS NULL) OR
        (user_id IS NULL AND remote_actor_uri IS NOT NULL)
    )
);

-- Create indexes for likes
CREATE INDEX idx_likes_activity_id ON likes(activity_id);
CREATE INDEX idx_likes_user_id ON likes(user_id);
CREATE INDEX idx_likes_created_at ON likes(created_at);
CREATE UNIQUE INDEX idx_likes_activity_user ON likes(activity_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX idx_likes_activity_actor ON likes(activity_id, remote_actor_uri) WHERE remote_actor_uri IS NOT NULL;
CREATE INDEX idx_likes_activity_pub_id ON likes(activity_pub_id) WHERE activity_pub_id IS NOT NULL;

-- Create comments table
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    remote_actor_uri VARCHAR(500),
    display_name VARCHAR(200),
    avatar_url VARCHAR(500),
    content TEXT NOT NULL,
    activity_pub_id VARCHAR(500),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    -- Constraints: either user_id OR remote_actor_uri must be set, but not both
    CONSTRAINT chk_comments_actor CHECK (
        (user_id IS NOT NULL AND remote_actor_uri IS NULL) OR
        (user_id IS NULL AND remote_actor_uri IS NOT NULL)
    )
);

-- Create indexes for comments
CREATE INDEX idx_comments_activity_id ON comments(activity_id);
CREATE INDEX idx_comments_user_id ON comments(user_id);
CREATE INDEX idx_comments_created_at ON comments(created_at);
CREATE INDEX idx_comments_activity_pub_id ON comments(activity_pub_id) WHERE activity_pub_id IS NOT NULL;
CREATE INDEX idx_comments_not_deleted ON comments(activity_id, deleted) WHERE deleted = false;

-- Add comments for documentation
COMMENT ON TABLE likes IS 'Likes on activities, supporting both local and federated likes';
COMMENT ON TABLE comments IS 'Comments on activities, supporting both local and federated comments';
COMMENT ON COLUMN likes.user_id IS 'Local user who liked (null if remote)';
COMMENT ON COLUMN likes.remote_actor_uri IS 'Remote ActivityPub actor URI (null if local)';
COMMENT ON COLUMN likes.activity_pub_id IS 'ActivityPub Like activity ID for federation';
COMMENT ON COLUMN comments.user_id IS 'Local user who commented (null if remote)';
COMMENT ON COLUMN comments.remote_actor_uri IS 'Remote ActivityPub actor URI (null if local)';
COMMENT ON COLUMN comments.activity_pub_id IS 'ActivityPub Note/Create activity ID for federation';
COMMENT ON COLUMN comments.deleted IS 'Soft delete flag for federation tracking';
