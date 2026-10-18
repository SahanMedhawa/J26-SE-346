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

export type ADRStatus = "PROPOSED" | "ACCEPTED" | "DEPRECATED" | "SUPERSEDED";

export type ADR = {
  id: string;
  adrId: string;
  projectId: string;
  title: string;
  status: ADRStatus;
  context: string;
  decision: string;
  consequences: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  linkedRuleCount: number | null;
  linkedRuleIds: string[];
};

export type ADRInput = {
  title: string;
  status: ADRStatus;
  context: string;
  decision: string;
  consequences: string;
};

export type ArchitectureElementType = "SERVICE" | "DATABASE" | "CONTROLLER" | "SERVICE_LAYER" | "REPOSITORY" | "MESSAGE_BROKER" | "API_GATEWAY" | "EXTERNAL_SYSTEM";
export type RelationshipType = "CALLS" | "DEPENDS_ON" | "DIRECT_DATABASE_ACCESS" | "DATA_ACCESS" | "PUBLISHES_TO" | "CONSUMES_FROM" | "USES_API";
export type RuleDecision = "ALLOWED" | "FORBIDDEN" | "REQUIRED";
export type RuleSeverity = "HIGH" | "MEDIUM" | "LOW";

export type ArchitectureRule = {
  id: string;
  ruleId: string;
  projectId: string;
  adrId: string;
  adrDisplayId: string;
  sourceElement: string;
  sourceElementType: ArchitectureElementType;
  relationshipType: RelationshipType;
  targetElement: string;
  targetElementType: ArchitectureElementType;
  decision: RuleDecision;
  description: string;
  severity: RuleSeverity;
  createdAt: string;
  updatedAt: string;
};

export type ArchitectureRuleInput = Omit<ArchitectureRule, "id" | "ruleId" | "projectId" | "adrDisplayId" | "createdAt" | "updatedAt">;

export type RepositoryFiles = {
  projectId: string;
  repositoryUrl: string;
  branch: string | null;
  files: string[];
};

export type ImplementationMappingsInput = {
  sourceFiles: string[];
  targetFiles: string[];
};

export type ImplementationMappings = ImplementationMappingsInput & { ruleUuid: string };

export type ValidationArchitectureStyle = Exclude<ArchitectureStyle, "MICROSERVICES_AND_LAYERED">;
export type ValidateStructureRequest = { architectureStyle?: ValidationArchitectureStyle };
export type StructuralValidationResponse = {
  ruleId: string;
  ruleUuid: string;
  projectId: string;
  architectureStyle: ValidationArchitectureStyle;
  sourceElementType: ArchitectureElementType;
  relationshipType: RelationshipType;
  targetElementType: ArchitectureElementType;
  decision: RuleDecision;
  validationStatus: "SUPPORTED" | "UNSUPPORTED";
  matchedSignatureId: string | null;
  message: string;
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

export const adrsApi = {
  list: async (projectId: string, params?: { status?: ADRStatus | "all"; search?: string }) => {
    const query = new URLSearchParams();
    if (params?.status && params.status !== "all") query.set("status", params.status);
    if (params?.search?.trim()) query.set("search", params.search.trim());
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return (await request<ADR[] | null>(`/api/rules/projects/${projectId}/adrs${suffix}`)) ?? [];
  },
  get: (projectId: string, adrId: string) => request<ADR>(`/api/rules/projects/${projectId}/adrs/${adrId}`),
  create: (projectId: string, input: ADRInput) => request<ADR>(`/api/rules/projects/${projectId}/adrs`, {
    method: "POST",
    body: JSON.stringify(input),
  }),
  update: (projectId: string, adrId: string, input: ADRInput & { version: number }) => request<ADR>(`/api/rules/projects/${projectId}/adrs/${adrId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  }),
  remove: (projectId: string, adrId: string) => request<void>(`/api/rules/projects/${projectId}/adrs/${adrId}`, {
    method: "DELETE",
  }),
};

export const rulesApi = {
  repositoryFiles: (projectId: string) => request<RepositoryFiles>(`/api/rules/projects/${projectId}/repository/files`),
  implementationMappings: (projectId: string, ruleUuid: string) => request<ImplementationMappings>(`/api/rules/projects/${projectId}/rules/${ruleUuid}/implementation-mappings`),
  saveImplementationMappings: (projectId: string, ruleUuid: string, input: ImplementationMappingsInput) => request<ImplementationMappings>(`/api/rules/projects/${projectId}/rules/${ruleUuid}/implementation-mappings`, {
    method: "PUT",
    body: JSON.stringify(input),
  }),
  validateStructure: (projectId: string, ruleUuid: string, input: ValidateStructureRequest) => request<StructuralValidationResponse>(`/api/rules/projects/${projectId}/rules/${ruleUuid}/validate-structure`, {
    method: "POST",
    body: JSON.stringify(input),
  }),
  list: async (projectId: string) => (await request<ArchitectureRule[] | null>(`/api/rules/projects/${projectId}/rules`)) ?? [],
  get: (projectId: string, ruleUuid: string) => request<ArchitectureRule>(`/api/rules/projects/${projectId}/rules/${ruleUuid}`),
  create: (projectId: string, input: ArchitectureRuleInput) => request<ArchitectureRule>(`/api/rules/projects/${projectId}/rules`, {
    method: "POST",
    body: JSON.stringify(input),
  }),
  update: (projectId: string, ruleUuid: string, input: ArchitectureRuleInput) => request<ArchitectureRule>(`/api/rules/projects/${projectId}/rules/${ruleUuid}`, {
    method: "PUT",
    body: JSON.stringify(input),
  }),
  remove: (projectId: string, ruleUuid: string) => request<void>(`/api/rules/projects/${projectId}/rules/${ruleUuid}`, {
    method: "DELETE",
  }),
};
