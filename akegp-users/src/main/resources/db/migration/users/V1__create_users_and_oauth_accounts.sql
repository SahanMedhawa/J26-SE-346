-- Users table: core identity, populated on first OAuth login
CREATE TABLE IF NOT EXISTS users_users (
    id              UUID PRIMARY KEY,
    display_name    VARCHAR(255) NOT NULL,
    email           VARCHAR(320) NOT NULL UNIQUE,
    avatar_url      VARCHAR(2048),
    bio             VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- OAuth linked accounts: one user can have multiple providers
CREATE TABLE IF NOT EXISTS users_oauth_accounts (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users_users(id) ON DELETE CASCADE,
    provider            VARCHAR(50)  NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    provider_email      VARCHAR(320),
    linked_at           TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_account_id)
);

CREATE INDEX idx_oauth_accounts_user_id ON users_oauth_accounts(user_id);
