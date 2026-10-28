-- Preserve user IDs while enforcing one canonical account per case-insensitive address.
CREATE UNIQUE INDEX users_users_email_normalized ON users_users (lower(email));

-- Record the evidence used for linking, so an untrusted email on an existing identity cannot enable takeover.
ALTER TABLE users_oauth_accounts ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users_oauth_accounts ADD COLUMN email_authoritative BOOLEAN NOT NULL DEFAULT FALSE;

-- Previous callback code required verification for every persisted Google/GitHub identity.
-- Google hosted-domain evidence was not persisted, so only Gmail is authoritative for historical Google rows.
UPDATE users_oauth_accounts
SET email_verified = TRUE,
    email_authoritative = (provider = 'github' OR lower(provider_email) LIKE '%@gmail.com')
WHERE provider IN ('google', 'github') AND provider_email IS NOT NULL;
