import { request, Project, ProjectInput } from "./client";
export type User = { id: string; displayName: string; email: string; avatarUrl: string | null; bio: string | null };
export type Role = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";
export type Workspace = { id: string; name: string; slug: string; description: string | null; ownerId: string; createdAt?: string };
export type Member = { userId: string; displayName: string; email: string; role: Role; joinedAt: string };
export type Invitation = { id: string; inviteeEmail: string; role: Role; token: string; expiresAt: string };
export type Account = { id: string; provider: string; providerEmail: string };
const json = (method: string, body?: unknown): RequestInit => ({ method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
const ws = (id: string) => `/api/workspaces/${encodeURIComponent(id)}`;
export const usersApi = {
  me: () => request<User>("/api/users/me"),
  providers: () => request<string[]>("/api/users/providers"),
  logout: () => request<void>("/api/users/logout", json("POST")),
  update: (input: Pick<User,"displayName" | "bio" | "avatarUrl">) => request<User>("/api/users/me", json("PUT", input)),
  accounts: () => request<Account[]>("/api/users/me/linked-accounts"),
  link: (provider: string) => request<{url: string}>(`/api/users/me/link/${provider}`, json("POST")),
  unlink: (provider: string) => request<void>(`/api/users/me/link/${provider}`, json("DELETE")),
  workspaces: () => request<Workspace[]>("/api/workspaces"),
  createWorkspace: (name: string, description: string) => request<Workspace>("/api/workspaces", json("POST",{name,description})),
  workspace: (id: string) => request<Workspace>(ws(id)),
  updateWorkspace: (id: string, name: string, description: string) => request<Workspace>(ws(id), json("PUT",{name,description})),
  deleteWorkspace: (id: string) => request<void>(ws(id), json("DELETE")),
  members: (id: string) => request<Member[]>(ws(id)+"/members"),
  role: (id: string, user: string, role: Role) => request<void>(ws(id)+`/members/${user}/role`,json("PUT",{role})),
  removeMember: (id: string, user: string) => request<void>(ws(id)+`/members/${user}`,json("DELETE")),
  invitations: (id: string) => request<Invitation[]>(ws(id)+"/invitations"),
  invite: (id: string, email: string, role: Role) => request<Invitation>(ws(id)+"/invitations",json("POST",{email,role})),
  revoke: (id: string, invite: string) => request<void>(ws(id)+`/invitations/${invite}`,json("DELETE")),
  respond: (token: string, accept: boolean) => request<void>(`/api/invitations/${encodeURIComponent(token)}/${accept?"accept":"decline"}`,json("POST")),
  projects: (id: string) => request<Project[]>(ws(id)+"/projects"),
  createProject: (id: string, input: ProjectInput) => request<Project>(ws(id)+"/projects",json("POST",input)),
  removeProject: (id: string, project: string) => request<void>(ws(id)+`/projects/${project}`,json("DELETE")),
};
