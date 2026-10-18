// Component 3 (enforcement) API helpers. Kept inside this module so no shared file is touched.

export type RunSummary = {
  runId: number;
  pr: number;
  changeType: string;
  result: string;
  repo: string;
  files: number;
  at: string;
};

export type EnforcementStats = {
  runs: number;
  analysisTriggered: number;
  skipped: number;
  violations: number;
  recent: RunSummary[];
};

export type ViolationDetail = {
  ruleId: string;
  severity: string;
  file: string;
  line: number;
  message: string;
  tag: string;
};

/** Latest run of one pull request, from GET /api/enforcement/prs. */
export type RunDetail = {
  runId: number;
  repo: string;
  prNumber: number;
  commitSha: string;
  projectId: string | null;
  classification: string;
  state: string;
  holdReason: string | null;
  files: number;
  violations: ViolationDetail[];
};

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { cache: "no-store", signal });
  if (!response.ok) {
    throw new Error(`Request to ${path} failed (${response.status})`);
  }
  return (await response.json()) as T;
}

export const getEnforcementStats = (signal?: AbortSignal) =>
  getJson<EnforcementStats>("/api/enforcement/stats", signal);

export const getRunDetails = (signal?: AbortSignal) =>
  getJson<RunDetail[]>("/api/enforcement/prs", signal);