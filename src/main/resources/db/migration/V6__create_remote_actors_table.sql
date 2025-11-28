-- V6: Create remote_actors table
-- Caches remote ActivityPub actor information for federation

CREATE TABLE remote_actors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_uri VARCHAR(512) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    inbox_url VARCHAR(512) NOT NULL,
    outbox_url VARCHAR(512),
    shared_inbox_url VARCHAR(512),
    public_key TEXT NOT NULL,
    public_key_id VARCHAR(512),
    display_name VARCHAR(255),
    avatar_url VARCHAR(512),
    summary TEXT,
    last_fetched_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_actor_uri ON remote_actors(actor_uri);
CREATE INDEX idx_domain ON remote_actors(domain);
CREATE INDEX idx_username_domain ON remote_actors(username, domain);
CREATE INDEX idx_last_fetched_at ON remote_actors(last_fetched_at);

-- Comments
COMMENT ON TABLE remote_actors IS 'Cache of remote ActivityPub actor profiles for federation';
COMMENT ON COLUMN remote_actors.actor_uri IS 'Full ActivityPub actor URI (e.g., https://mastodon.social/users/username)';
COMMENT ON COLUMN remote_actors.shared_inbox_url IS 'Shared inbox URL for efficient server-to-server communication';
COMMENT ON COLUMN remote_actors.public_key IS 'RSA public key for HTTP Signature verification';
COMMENT ON COLUMN remote_actors.last_fetched_at IS 'Timestamp of last actor profile fetch for cache invalidation';
