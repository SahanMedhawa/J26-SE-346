CREATE UNIQUE INDEX users_workspace_project_owner ON users_workspace_projects(project_id);
CREATE UNIQUE INDEX users_oauth_provider_per_user ON users_oauth_accounts(user_id, provider);
