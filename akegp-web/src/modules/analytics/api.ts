export const DEMO_PROJECT = "c4000000-0000-4000-8000-000000000001";
export type Run = { id: string; streamKey: string; externalRunId: string; sequence: number; analyzedAt: string;
  branchRef: string; repositoryId: string; commitRef: string; analysisStatus: string; gateOutcome: string | null;
  provenance: string; label: string; total: number | null; ruleSetVersion: string; detectorVersion: string;
  mappingVersion: string; scopeVersion: string; scope: string[]; dataset: string; pipelineOutcome: string };
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

export function runLabel(value: string): string {
  return value.replace(/^demo-main-/, 'Run ').replace(/^demo-refactor-/, 'Refactor run ');
}
export function comparableBaselines(runs: Run[], currentId: string): Run[] {
  const current = runs.find(r => r.id === currentId);
  if (!current || current.analysisStatus !== 'COMPLETE') return [];
  const earlier = runs.filter(r => r.streamKey === current.streamKey && r.sequence < current.sequence).sort((a,b) => b.sequence - a.sequence);
  const result: Run[] = [];
  for (const r of earlier) {
    if (r.analysisStatus !== 'COMPLETE' || r.ruleSetVersion !== current.ruleSetVersion || r.detectorVersion !== current.detectorVersion
      || r.mappingVersion !== current.mappingVersion || r.scopeVersion !== current.scopeVersion
      || JSON.stringify([...(r.scope || [])].sort()) !== JSON.stringify([...(current.scope || [])].sort())) break;
    result.push(r);
  }
  return result;
}
export function windowParams(current: string, window: { mode: string; runs: string; grouping: string }, filters: { from: string; to: string; service: string; rule: string; severity: string; search: string }): Record<string,string> {
  const params: Record<string,string> = { currentRunId: current, runs: window.runs, grouping: window.grouping };
  for (const key of ['service','rule','severity','search'] as const) if (filters[key]) params[key] = filters[key];
  if (window.mode === 'time') {
    if (!filters.from || !filters.to || filters.from >= filters.to) throw new Error('Choose a start date before the exclusive end date.');
    params.from = `${filters.from}T00:00:00Z`; params.to = `${filters.to}T00:00:00Z`;
  }
  return params;
}

export type HistoryPoint = { runId: string; run: string; at: string; status: string; gateOutcome: string | null;
  total: number | null; high: number | null; medium: number | null; low: number | null; added: number | null;
  resolved: number | null; persistent: number | null; density: number | null; reason: string; areas: Record<string, number> };
export type Lifecycle = { finding: Finding; firstSeen: string; lastSeen: string; occurrences: number;
  observedRuns: number; currentStreak: number; recurrence: number; active: boolean;
  timeline: { runId: string; run: string; at: string; state: string; severity: number | null }[] };
export type Explorer = { points: HistoryPoint[]; lifecycles: Lifecycle[]; totalLifecycles: number;
  completeRuns: number; incompleteRuns: number; services: string[]; rules: string[]; label: string;
  densityNote: string; offset: number; limit: number };
export type ExportedRule = { ruleId: string; adrId: string; sourceElement: string; targetElement: string;
  relationshipType: string; decision: string; severity: string; validationStatus: string; architectReviewStatus: string;
  sourceFiles: string[]; targetFiles: string[] };
export type ProjectContext = { project: import('../../shared/api/client').Project;
  ruleExport: { rules: ExportedRule[]; ruleCount: number }; demoEnabled: boolean; demoDataset?: string;
  metadataFingerprint?: string; warnings: string[];
  scans: { scanId: string; scannedAt: string; fileCount: number; dependencyCount: number }[];
  governance: { runId: number; at: string; commit: string; prNumber: number; state: string; classification: string;
    violations: number; projectMappingMatches: boolean; decision: { action: string; reviewer: string; comment: string | null; at: string } | null }[] };

export async function importProjectDemo(project: string): Promise<{ dataset: string; runs: number; replays: number; label: string }> {
  const response = await fetch(`/api/analytics/projects/${encodeURIComponent(project)}/demo-history`, { method: 'POST' });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message || `Import failed (${response.status})`);
  return body;
}
