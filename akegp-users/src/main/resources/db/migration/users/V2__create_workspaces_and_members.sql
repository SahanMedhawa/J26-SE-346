-- Workspaces: containers for projects and collaboration
CREATE TABLE IF NOT EXISTS users_workspaces (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    description     VARCHAR(2000),
    owner_id        UUID NOT NULL REFERENCES users_users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_workspaces_owner_id ON users_workspaces(owner_id);

-- Workspace members: who belongs to which workspace and with what role
CREATE TABLE IF NOT EXISTS users_workspace_members (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES users_workspaces(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users_users(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, user_id),
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'))
);

CREATE INDEX idx_workspace_members_user_id ON users_workspace_members(user_id);
