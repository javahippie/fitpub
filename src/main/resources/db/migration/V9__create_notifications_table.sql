-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    actor_uri VARCHAR(500),
    actor_display_name VARCHAR(255),
    actor_username VARCHAR(255),
    actor_avatar_url TEXT,
    activity_id UUID,
    activity_title VARCHAR(255),
    comment_id UUID,
    comment_text VARCHAR(200),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_activity FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE
);

-- Create indexes for efficient queries
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_read_status ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- Comments
COMMENT ON TABLE notifications IS 'User notifications for social interactions';
COMMENT ON COLUMN notifications.type IS 'Type of notification: ACTIVITY_LIKED, ACTIVITY_COMMENTED, USER_FOLLOWED, FOLLOW_ACCEPTED, ACTIVITY_SHARED, MENTIONED_IN_COMMENT';
COMMENT ON COLUMN notifications.actor_uri IS 'URI of the user who triggered the notification';
COMMENT ON COLUMN notifications.actor_display_name IS 'Cached display name of the actor';
COMMENT ON COLUMN notifications.actor_username IS 'Cached username of the actor';
COMMENT ON COLUMN notifications.actor_avatar_url IS 'Cached avatar URL of the actor';
COMMENT ON COLUMN notifications.activity_id IS 'Related activity ID (for likes, comments)';
COMMENT ON COLUMN notifications.activity_title IS 'Cached activity title';
COMMENT ON COLUMN notifications.comment_id IS 'Related comment ID';
COMMENT ON COLUMN notifications.comment_text IS 'Preview of comment text';
COMMENT ON COLUMN notifications.is_read IS 'Whether the notification has been read';
COMMENT ON COLUMN notifications.read_at IS 'Timestamp when notification was read';
