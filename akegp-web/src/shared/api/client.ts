export async function getHealth(moduleName: string): Promise<string> {
  const response = await fetch(`/api/${moduleName}/health`);
  return response.text();
}

export type ArchitectureStyle = "MICROSERVICES" | "LAYERED" | "MICROSERVICES_AND_LAYERED";
export type ProjectStatus = "ACTIVE" | "INACTIVE";

export type Project = {
  id: string;
  name: string;
  description: string | null;
  repositoryUrl: string;
  architectureStyles: ArchitectureStyle[];
  registeredAt: string;
  status: ProjectStatus;
};

export type ProjectInput = {
  name: string;
  description: string;
  repositoryUrl: string;
  architectureStyles: ArchitectureStyle[];
};

export type ApiErrorResponse = {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
};

export class ApiClientError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(response: ApiErrorResponse) {
    super(response.message);
    this.name = "ApiClientError";
    this.status = response.status;
    this.fieldErrors = response.fieldErrors ?? {};
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...options?.headers },
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const error = body as ApiErrorResponse | null;
    throw new ApiClientError(error ?? {
      status: response.status,
      error: response.statusText,
      message: "The request could not be completed.",
    });
  }
  return body as T;
}

export const projectsApi = {
  list: async () => (await request<Project[] | null>("/api/rules/projects")) ?? [],
  get: (id: string) => request<Project>(`/api/rules/projects/${id}`),
  create: (input: ProjectInput) => request<Project>("/api/rules/projects", {
    method: "POST",
    body: JSON.stringify(input),
  }),
  update: (id: string, input: ProjectInput) => request<Project>(`/api/rules/projects/${id}`, {
    method: "PUT",
    body: JSON.stringify(input),
  }),
  updateStatus: (id: string, status: ProjectStatus) => request<Project>(`/api/rules/projects/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  }),
};
