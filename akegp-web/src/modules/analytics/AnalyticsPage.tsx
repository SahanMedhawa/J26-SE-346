import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { useSelectedProject } from "../../shared/projects/SelectedProject";
import { analyticsRequest, Comparison, DEMO_PROJECT, displayCount, Ranking, Run } from "./api";
import "./analytics.css";

export function AnalyticsPage() {
  const [query] = useSearchParams();
  const { selectedProject } = useSelectedProject();
  const project = query.get("projectId") || selectedProject?.id || "";
  return <main className="analytics-page"><header className="analytics-heading"><div><p className="section-kicker">COMPONENT 04 / GOVERNANCE ANALYTICS</p>
    <h1>Architecture <span>over time.</span></h1><p>Follow violation changes and explain what deserves attention.</p></div><Link to="/projects">Project library ↗</Link></header>
    {!project ? <section className="analytics-panel"><h2>Choose a project to begin</h2><p>Open Analytics from a project dashboard, or explore the constructed reference dataset.</p>
      <Link className="analytics-action" to={`/analytics/dashboard?projectId=${DEMO_PROJECT}`}>Open labelled demo</Link></section>
      : <AnalyticsWorkspace key={project} project={project} />}</main>;
}

export function AnalyticsWorkspace({ project }: { project: string }) {
  const location = useLocation();
  const tab = location.pathname.endsWith("/hotspots") ? "hotspots" : location.pathname.endsWith("/lifecycle") ? "lifecycle" : "dashboard";
  const [runs, setRuns] = useState<Run[]>([]);
  const [current, setCurrent] = useState("");
  const [baseline, setBaseline] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [runLoading, setRunLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [comparison, setComparison] = useState<Comparison | null>(null);
  const [trend, setTrend] = useState<Comparison[]>([]);
  const [ranking, setRanking] = useState<Ranking | null>(null);
  const [state, setState] = useState("");
  const [offset, setOffset] = useState(0);
  const [rankOffset, setRankOffset] = useState(0);
  const [window, setWindow] = useState({ mode: "runs", runs: "5", grouping: "SERVICE", from: "2026-01-01", to: "2026-01-06" });

  useEffect(() => {
    const abort = new AbortController();
    setRunLoading(true); setError(""); setRuns([]); setCurrent("");
    analyticsRequest<Run[]>(project, "runs", {}, abort.signal).then(value => {
      if (abort.signal.aborted) return;
      setRuns(value); setCurrent(value[value.length - 1]?.id || "");
    }).catch(e => { if (!abort.signal.aborted) setError(e.message); })
      .finally(() => { if (!abort.signal.aborted) setRunLoading(false); });
    return () => abort.abort();
  }, [project, retry]);

  useEffect(() => {
    if (!current) { setComparison(null); setRanking(null); setTrend([]); setLoading(false); return; }
    const abort = new AbortController();
    setLoading(true); setError(""); setComparison(null); setRanking(null); setTrend([]);
    const compareParams: Record<string, string> = { currentRunId: current, offset: String(offset), limit: "20" };
    if (baseline && tab !== "hotspots") compareParams.baselineRunId = baseline;
    if (state) compareParams.state = state;
    const rankParams: Record<string, string> = { currentRunId: current, runs: window.runs, grouping: window.grouping, offset: String(rankOffset), limit: "20" };
    if (window.mode === "time") { rankParams.from = `${window.from}T00:00:00Z`; rankParams.to = `${window.to}T00:00:00Z`; }
    Promise.all([
      analyticsRequest<Comparison>(project, "comparisons", compareParams, abort.signal),
      analyticsRequest<Comparison[]>(project, "trends", { currentRunId: current }, abort.signal),
      analyticsRequest<Ranking>(project, "hotspots", rankParams, abort.signal),
    ]).then(([c, t, r]) => { if (!abort.signal.aborted) { setComparison(c); setTrend(t); setRanking(r); } })
      .catch(e => { if (!abort.signal.aborted) setError(e.message); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [project, current, baseline, state, offset, rankOffset, window, tab]);

  const selected = runs.find(r => r.id === current);
  const runName = (id: string | null) => runs.find(r => r.id === id)?.externalRunId || "None";
  return <>
    <nav className="analytics-tabs" aria-label="Analytics views">{["dashboard", "lifecycle", "hotspots"].map(t =>
      <Link key={t} aria-current={tab === t ? "page" : undefined} to={`/analytics/${t}?projectId=${encodeURIComponent(project)}`}>{t}</Link>)}</nav>
    <div className="analytics-notice">{project === DEMO_PROJECT ? "Demo snapshot - not upstream analysis. All finding metadata is constructed." : `Project ${project}`}</div>
    <section className="analytics-controls">
      <label>Current run<select value={current} onChange={e => { setCurrent(e.target.value); setBaseline(""); setOffset(0); setRankOffset(0); }} disabled={!runs.length}>
        {!runs.length && <option value="">No runs</option>}{runs.map(r => <option key={r.id} value={r.id}>{r.externalRunId} · {r.branchRef} · {r.provenance} · {r.analysisStatus}</option>)}</select></label>
      {tab !== "hotspots" && <label>Compare with<select value={baseline} onChange={e => { setBaseline(e.target.value); setOffset(0); }}>
        <option value="">Immediate predecessor / automatic baseline</option>{runs.filter(r => selected && r.streamKey === selected.streamKey && r.sequence < selected.sequence && r.analysisStatus === "COMPLETE").map(r => <option key={r.id} value={r.id}>{r.externalRunId}</option>)}</select></label>}
      <button onClick={() => { setBaseline(""); setRetry(x => x + 1); }}>Refresh</button>
    </section>
    {selected && <p className="analytics-meta">{selected.label} · {selected.analyzedAt} · Analysis: {selected.analysisStatus} · Gate: {selected.gateOutcome || "unavailable"}<br />Commit: {selected.commitRef} · Rules: {selected.ruleSetVersion} · Detector: {selected.detectorVersion} · Scope: {selected.scopeVersion}</p>}
    {tab === "hotspots" && <form className="analytics-controls" onSubmit={e => { e.preventDefault(); const data = new FormData(e.currentTarget); setRankOffset(0); setWindow({ mode: String(data.get("mode")), runs: String(data.get("runs")), grouping: String(data.get("grouping")), from: String(data.get("from")), to: String(data.get("to")) }); }}>
      <label>Window<select name="mode" defaultValue={window.mode}><option value="runs">Latest comparable runs</option><option value="time">UTC date range</option></select></label>
      <label>Requested runs<input name="runs" type="number" min="1" max="1000" required defaultValue={window.runs} /></label>
      <label>From (inclusive UTC)<input name="from" type="date" required defaultValue={window.from} /></label>
      <label>To (exclusive UTC)<input name="to" type="date" required defaultValue={window.to} /></label>
      <label>Cohort<select name="grouping" defaultValue={window.grouping}><option>SERVICE</option><option>MODULE</option><option>RULE</option></select></label><button type="submit">Apply window</button>
    </form>}
    {error ? <div role="alert" className="analytics-error"><h2>Analytics could not be loaded</h2><p>{error}</p><p>No demo fallback was used.</p></div>
      : runLoading || loading ? <p role="status" className="analytics-panel">Loading analytics…</p>
      : !runs.length ? <section className="analytics-panel"><h2>No snapshots yet</h2><p>This project has no Analytics snapshots yet. Completed analysis snapshots must be ingested before comparisons and rankings are available.</p><Link className="analytics-action" to={`/analytics/dashboard?projectId=${DEMO_PROJECT}`}>Open labelled demo</Link></section>
      : <>
        {comparison && tab !== "hotspots" && <>
          <div className="analytics-meta">{comparison.baseline ? "Initial snapshot / new baseline" : comparison.comparable ? `Compared with ${runName(comparison.predecessorRunId)}` : "Comparison unavailable"} · {comparison.reason}{comparison.nonAdjacent && " · Explicit non-adjacent comparison"}</div>
          <ComparisonCards comparison={comparison} />
          {tab === "lifecycle" ? <section className="analytics-panel"><div className="analytics-section-title"><h2>Violation lifecycle</h2><label>State <select value={state} onChange={e => { setState(e.target.value); setOffset(0); }}><option value="">All states</option>{["NEW", "RESOLVED", "PERSISTING"].map(s => <option key={s}>{s}</option>)}</select></label></div>
            {!comparison.comparable ? <p>{comparison.reason}. Predecessor-based classification is unavailable.</p> : <>
              <div className="analytics-table-wrap"><table><thead><tr><th>State</th><th>Rule</th><th>Entity</th><th>Severity</th><th>Evidence</th></tr></thead><tbody>{comparison.violations.map(v => <tr key={v.finding.identity}><td><span className={`analytics-state ${v.state.toLowerCase()}`}>{v.state}</span></td><td>{v.finding.ruleName}</td><td>{v.finding.entityName}</td><td>{v.finding.severity}</td><td>{v.finding.location || v.finding.message || "No display evidence"}</td></tr>)}</tbody></table></div>
              {!comparison.totalResults && <p>No violations match this state.</p>}<Pagination offset={offset} total={comparison.totalResults} onChange={setOffset} />
            </>}</section> : <section className="analytics-panel"><h2>Violation count trend</h2><p>Counts describe observed violations. Zero net change can still contain new and resolved findings.</p>
              <div className="analytics-table-wrap"><table><thead><tr><th>Run</th><th>Active total</th><th>New</th><th>Resolved</th><th>Persisting</th><th>Net change</th><th>Context</th></tr></thead><tbody>{trend.map(t => <tr key={t.currentRunId}><td>{runName(t.currentRunId)}</td><td>{displayCount(t.currentTotal)}</td><td>{displayCount(t.newCount)}</td><td>{displayCount(t.resolvedCount)}</td><td>{displayCount(t.persistingCount)}</td><td>{displayCount(t.netChange)}</td><td>{t.reason}</td></tr>)}</tbody></table></div></section>}
        </>}
        {tab === "hotspots" && <>
          {ranking && <HotspotTable ranking={ranking} currentName={runName(ranking.window.currentRunId)} onPage={setRankOffset} />}
        </>}
      </>}
  </>;
}

export function ComparisonCards({ comparison: c }: { comparison: Comparison }) {
  return <div className="analytics-cards">{[["Active violations", c.currentTotal], ["New", c.newCount], ["Resolved", c.resolvedCount], ["Persisting", c.persistingCount], ["Net violation change", c.netChange]].map(([label, value]) =>
    <article key={String(label)}><span>{label}</span><strong>{displayCount(value as number | null)}</strong></article>)}</div>;
}

export function HotspotTable({ ranking: r, currentName, onPage }: { ranking: Ranking; currentName: string; onPage: (offset: number) => void }) {
  return <section className="analytics-panel"><h2>Explainable hotspot ranking</h2><p>Relative prioritization on a 0–1 scale. This is not architecture health or failure probability.</p>
    <p className="analytics-meta">{r.label} · W = {r.window.actualRuns} · Current: {currentName} · Cohort: {r.cohortSize} {r.grouping.toLowerCase()} entities<br />{r.window.boundaryReason}{r.window.limitedHistory && " · Limited history: one run"}</p>
    {r.status === "INSUFFICIENT_DATA" ? <p>Insufficient data for this window.</p> : !r.cohortSize ? <p>No entities have violations in the selected cohort/window.</p> : <>
      <p className="analytics-meta">Weights: frequency 2/6, severity 3/6, persistence 1/6. A zero normalized factor does not necessarily mean no violations.</p>
      <div className="analytics-table-wrap"><table><thead><tr><th>Entity</th><th>Occurrences</th><th>Active</th><th>Recurrence</th><th>Score / 1</th><th>Explanation</th></tr></thead><tbody>{r.rows.map(row => <tr key={row.entityId}>
        <td>{row.entityName}<small>{row.entityId}</small></td><td>{row.rawFrequency}</td><td>{row.activeCount}{!row.activeCount && <small>Inactive now</small>}</td><td>{(row.recurrence * 100).toFixed(1)}%</td>
        <td><strong>{row.score.toFixed(3)}</strong><meter min="0" max="1" value={row.score} aria-label={`${row.entityName} hotspot score`} /></td>
        <td><details><summary>Show factors</summary><dl><dt>Frequency F (cohort min {r.frequencyMin}, max {r.frequencyMax})</dt><dd>{row.normalizedFrequency.toFixed(4)} × {r.weights.frequency.toFixed(4)} = {row.contributions.frequency.toFixed(4)}</dd><dt>Average severity / normalized S</dt><dd>{row.averageSeverity.toFixed(4)} / {row.normalizedSeverity.toFixed(4)}; contribution {row.contributions.severity.toFixed(4)}</dd><dt>Active persistence P</dt><dd>{row.persistence.toFixed(4)}; contribution {row.contributions.persistence.toFixed(4)}</dd></dl><p>Recurrence is a separate diagnostic and is not added to the score.</p>{row.violations.map(v => <p key={v.identity}><code title={v.identity}>{v.identity.slice(0, 12)}</code>: streak {v.currentStreak}/{r.window.actualRuns}; recurrence {(v.recurrence * 100).toFixed(1)}%</p>)}</details></td>
      </tr>)}</tbody></table></div><Pagination offset={r.offset} total={r.cohortSize} onChange={onPage} />
    </>}
  </section>;
}

function Pagination({ offset, total, onChange }: { offset: number; total: number; onChange: (offset: number) => void }) {
  return <div className="analytics-pagination"><button disabled={offset === 0} onClick={() => onChange(Math.max(0, offset - 20))}>Previous</button><span>{total ? offset + 1 : 0}–{Math.min(offset + 20, total)} of {total}</span><button disabled={offset + 20 >= total} onClick={() => onChange(offset + 20)}>Next</button></div>;
}
