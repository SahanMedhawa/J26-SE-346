export const DEMO_PROJECT = "c4000000-0000-4000-8000-000000000001";
export type Run = { id: string; streamKey: string; externalRunId: string; sequence: number; analyzedAt: string;
  branchRef: string; repositoryId: string; commitRef: string; analysisStatus: string; gateOutcome: string | null;
  provenance: string; label: string; total: number | null; ruleSetVersion: string; detectorVersion: string;
  mappingVersion: string; scopeVersion: string; scope: string[] };
export type Finding = { identity: string; ruleKey: string; ruleName: string; entityType: string;
  entityKey: string; entityName: string; severity: number; location: string | null; message: string | null };
export type Comparison = { currentRunId: string; predecessorRunId: string | null; baseline: boolean; comparable: boolean;
  reason: string; nonAdjacent: boolean; currentTotal: number | null; previousTotal: number | null;
  newCount: number | null; resolvedCount: number | null; persistingCount: number | null; netChange: number | null;
  direction: string | null; densityPerKloc: number | null; label: string;
  violations: { state: string; finding: Finding }[]; totalResults: number; offset: number; limit: number };
export type Factors = { frequency: number; severity: number; persistence: number };
export type Ranking = { window: { requestedRuns: number; actualRuns: number; runIds: string[]; currentRunId: string | null;
  fromInclusive: string | null; toExclusive: string | null; timezone: string; limitedHistory: boolean; boundaryReason: string };
  grouping: string; cohortSize: number; frequencyMin: number; frequencyMax: number; weights: Factors;
  rows: { entityId: string; entityType: string; entityName: string; rawFrequency: number; normalizedFrequency: number;
    averageSeverity: number; normalizedSeverity: number; persistence: number; activeCount: number;
    recurrence: number; score: number; contributions: Factors;
    violations: { identity: string; recurrence: number; currentStreak: number }[] }[];
  offset: number; limit: number; label: string; status: string };

export async function analyticsRequest<T>(project: string, endpoint: string, params: Record<string, string>, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/analytics/projects/${encodeURIComponent(project)}/${endpoint}?${new URLSearchParams(params)}`, { signal });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message || `Analytics request failed (${response.status}).`);
  if (body == null) throw new Error("Analytics returned an empty response.");
  return body as T;
}

export function displayCount(value: number | null): string { return value == null ? "Unavailable" : String(value); }
