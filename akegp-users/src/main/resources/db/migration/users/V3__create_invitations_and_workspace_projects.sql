-- Invitations: invite users to workspaces via unique token link
CREATE TABLE IF NOT EXISTS users_invitations (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES users_workspaces(id) ON DELETE CASCADE,
    invitee_email   VARCHAR(320) NOT NULL,
    invited_by      UUID NOT NULL REFERENCES users_users(id),
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    token           VARCHAR(128) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP NOT NULL,
    CHECK (role IN ('ADMIN', 'MEMBER', 'VIEWER')),
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'))
);

CREATE INDEX idx_invitations_workspace_id ON users_invitations(workspace_id);
CREATE INDEX idx_invitations_invitee_email ON users_invitations(invitee_email);

-- Workspace projects: links a workspace to projects in the rules module (no FK)
CREATE TABLE IF NOT EXISTS users_workspace_projects (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES users_workspaces(id) ON DELETE CASCADE,
    project_id      VARCHAR(255) NOT NULL,
    added_at        TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, project_id)
);
