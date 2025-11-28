-- V2: Create users table
-- Stores local user accounts with ActivityPub Actor profile data

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name VARCHAR(100),
    bio TEXT,
    avatar_url TEXT,
    public_key TEXT NOT NULL,
    private_key TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    locked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_user_username ON users(username);
CREATE UNIQUE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_created_at ON users(created_at DESC);

-- Comment on table
COMMENT ON TABLE users IS 'Local user accounts with ActivityPub Actor profiles';
COMMENT ON COLUMN users.public_key IS 'RSA public key for ActivityPub HTTP Signature verification';
COMMENT ON COLUMN users.private_key IS 'RSA private key for signing ActivityPub requests (encrypted at rest)';
