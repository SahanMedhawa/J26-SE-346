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

/** A reviewer's decision on a held pull request (null fields when not given). */
export type DecisionView = {
  action: string;
  reviewer: string;
  justification: string | null;
  comment: string | null;
  at: string;
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
  changedFiles: string[];
  violations: ViolationDetail[];
  decision: DecisionView | null;
};

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { cache: "no-store", signal });
  if (!response.ok) {
    throw new Error(`Request to ${path} failed (${response.status})`);
  }
  return (await response.json()) as T;
}

export const getEnforcementStats = (signal?: AbortSignal, repo?: string) =>
  getJson<EnforcementStats>("/api/enforcement/stats" + (repo ? `?${new URLSearchParams({ repo })}` : ""), signal);

export const getRunDetails = (signal?: AbortSignal, repo?: string) =>
  getJson<RunDetail[]>("/api/enforcement/prs" + (repo ? `?${new URLSearchParams({ repo })}` : ""), signal);


export type DecisionAction = "approve_exception" | "request_changes" | "reject" | "apply_fix";

export type DecisionInput = {
  action: DecisionAction;
  reviewer: string;
  justification?: string;
  comment?: string;
};

/** The run as the server returns it after a decision (for a fix re-scan this is the new run). */
export interface DecisionOutcome {
  runId: number;
  state: string;
  commitSha: string;
  violations: unknown[];
}

/** Sends a reviewer's decision for a held pull request. Throws with the server's message when it is refused. */
export async function submitDecision(repo: string, prNumber: number, input: DecisionInput): Promise<DecisionOutcome> {
  const [owner, name] = repo.split("/");
  const path = `/api/enforcement/prs/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/${prNumber}/decision`;
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    let reason = `The decision was not saved (${response.status}).`;
    try {
      const body = (await response.json()) as { error?: string };
      if (body.error) reason = body.error;
    } catch {
      // not JSON: keep the generic message
    }
    throw new Error(reason);
  }
  return (await response.json()) as DecisionOutcome;
}

/** One scan of a pull request, from GET /api/enforcement/prs/{owner}/{name}/{pr}/history (newest first). */
export type RunHistoryItem = {
  runId: number;
  commitSha: string;
  classification: string;
  state: string;
  holdReason: string | null;
  files: number;
  changedFiles: string[];
  violations: ViolationDetail[];
  decision: DecisionView | null;
  at: string;
};

export async function fetchHistory(repo: string, prNumber: number): Promise<RunHistoryItem[]> {
  const [owner, name] = repo.split("/");
  const response = await fetch(`/api/enforcement/prs/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/${prNumber}/history`);
  if (!response.ok) throw new Error(`The scan history could not be loaded (${response.status}).`);
  return (await response.json()) as RunHistoryItem[];
}
