import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useSelectedProject } from "../../shared/projects/SelectedProject";
import { analyticsRequest, Comparison, DEMO_PROJECT, displayCount, Ranking, Run, Explorer, ProjectContext, importProjectDemo, runLabel, windowParams, comparableBaselines } from "./api";
import { ProjectWorkspaceLayout } from "../../shared/projects/ProjectWorkspaceLayout";
import { HistoryChart, AreaHeatmap } from "./HistoryCharts";
import "./analytics.css";

export function AnalyticsPage() {
  const [query] = useSearchParams();
  const { projectId } = useParams();
  const { selectedProject } = useSelectedProject();
  const project = projectId || query.get("projectId") || selectedProject?.id || "";
  const [context, setContext] = useState<ProjectContext | null>(null);
  const [contextError, setContextError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    setContext(null); setContextError("");
    if (!project || project === DEMO_PROJECT) return;
    const abort = new AbortController();
    analyticsRequest<ProjectContext>(project, "context", {}, abort.signal).then(c => { if (!abort.signal.aborted) setContext(c); })
      .catch(e => { if (!abort.signal.aborted) setContextError(e.message); });
    return () => abort.abort();
  }, [project, revision]);
  const base = projectId ? `/projects/${encodeURIComponent(project)}/analytics` : "/analytics";
  const views = [{ path: "dashboard", label: "Dashboard" }, { path: "lifecycle", label: "Violation lifecycle" },
    { path: "hotspots", label: "Hotspot analytics" }, { path: "history", label: "Run & governance history" }];
  return <ProjectWorkspaceLayout component="04" title="Architecture Analytics" subtitle="Drift, Trends & Governance" status="Historical analysis"
    projectId={project} projectName={context?.project.name || (selectedProject?.id === project ? selectedProject.name : "Project workspace")}
    modules={views.map(v => ({ label: v.label, to: `${base}/${v.path}?projectId=${encodeURIComponent(project)}`, icon: <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19h16M6 15V9M12 15V5M18 15v-4" /></svg> }))}>
    <main className="analytics-page rules-content">
      <div className="analytics-heading"><div><p className="content-kicker">{context?.project.name || "Project"} / Architecture governance</p>
      <h2>Violation trends and priorities</h2><p>One project, one analysis window, connected lifecycle evidence.</p></div></div>
      {contextError && <p role="alert" className="analytics-error">Project context could not be loaded: {contextError}</p>}
      {project ? <AnalyticsWorkspace key={project} project={project} context={context} onImported={() => setRevision(n => n + 1)} />
        : <section className="analytics-panel"><h2>Choose a project</h2><p>Open Analytics from the project library or find a repository below.</p><Link to="/projects">Browse projects</Link><RepositoryLookup /></section>}
    </main>
  </ProjectWorkspaceLayout>;

}

export function RepositoryLookup() {
  const navigate = useNavigate();
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [unregistered, setUnregistered] = useState(false);
  const [preview, setPreview] = useState("");
  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setError(""); setUnregistered(false); setPreview("");
    try {
      const response = await fetch(`/api/analytics/repository?${new URLSearchParams({ url: url.trim() })}`);
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.detail || "Repository lookup failed");
      if (body.registered) navigate(`/projects/${encodeURIComponent(body.project.id)}/analytics/history`);
      else { setUnregistered(true); setError(body.message); setPreview(url.trim()); }
    } catch (e) { setError(e instanceof Error ? e.message : "Repository lookup failed"); }
    finally { setBusy(false); }
  }
  return <section className="analytics-repository"><form onSubmit={submit}><label htmlFor="analytics-repo">Repository history</label>
    <input id="analytics-repo" type="url" required placeholder="https://github.com/owner/repository" value={url} onChange={e => setUrl(e.target.value)} />
    <button disabled={busy}>{busy ? "Finding project…" : "Open history →"}</button></form>
    {error && <p role="status">{error} {unregistered && <Link to="/projects/new">Register project ↗</Link>}</p>}
    {preview && <PublicActivity url={preview} />}
  </section>;
}

export function AnalyticsWorkspace({ project, context = null, onImported }: { project: string; context?: ProjectContext | null; onImported?: () => void }) {
  const location = useLocation();
  const tab = location.pathname.endsWith("/hotspots") ? "hotspots" : location.pathname.endsWith("/lifecycle") ? "lifecycle" : location.pathname.endsWith("/history") ? "history" : "dashboard";
  const base = location.pathname.startsWith("/projects/") ? `/projects/${encodeURIComponent(project)}/analytics` : "/analytics";
  const link = (view: string) => `${base}/${view}?projectId=${encodeURIComponent(project)}`;
  const [runs, setRuns] = useState<Run[]>([]);
  const [dataset, setDataset] = useState("");
  const [branch, setBranch] = useState("");
  const [current, setCurrent] = useState("");
  const [baseline, setBaseline] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [runLoading, setRunLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [comparison, setComparison] = useState<Comparison | null>(null);
  const [ranking, setRanking] = useState<Ranking | null>(null);
  const [explorer, setExplorer] = useState<Explorer | null>(null);
  const [state, setState] = useState("");
  const [offset, setOffset] = useState(0);
  const [rankOffset, setRankOffset] = useState(0);
  const [lifeOffset, setLifeOffset] = useState(0);
  const [window, setWindow] = useState({ mode: "runs", runs: "20", grouping: "SERVICE" });
  const [filters, setFilters] = useState({ service: "", rule: "", severity: "", search: "", from: "", to: "" });
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState("");
  const [expanded, setExpanded] = useState("");
  const [showTable, setShowTable] = useState(false);
  const [drilldown, setDrilldown] = useState("");

  useEffect(() => {
    const abort = new AbortController(); setRunLoading(true); setError("");
    analyticsRequest<Run[]>(project, "runs", {}, abort.signal).then(value => {
      if (abort.signal.aborted) return; setRuns(value);
    }).catch(e => { if (!abort.signal.aborted) setError(e.message); })
      .finally(() => { if (!abort.signal.aborted) setRunLoading(false); });
    return () => abort.abort();
  }, [project, retry]);
  const datasets = [...new Set(runs.map(r => r.provenance === "UPSTREAM" ? "recorded" : r.dataset || "legacy-demo"))];
  const chosenDataset = dataset || (datasets.includes("recorded") ? "recorded" : context?.demoDataset && datasets.includes(context.demoDataset) ? context.demoDataset : [...datasets].sort().slice(-1)[0] || "");
  const datasetRuns = runs.filter(r => (r.provenance === "UPSTREAM" ? "recorded" : r.dataset || "legacy-demo") === chosenDataset);
  const branches = [...new Set(datasetRuns.map(r => r.branchRef))];
  const chosenBranch = branch && branches.includes(branch) ? branch : branches.find(b => b === "refs/heads/main") || branches[0] || "";
  const visibleRuns = datasetRuns.filter(r => r.branchRef === chosenBranch).sort((a, b) => a.sequence - b.sequence);
  useEffect(() => {
    if (!visibleRuns.some(r => r.id === current)) { setCurrent(visibleRuns[visibleRuns.length - 1]?.id || ""); setBaseline(""); }
  }, [runs, chosenDataset, chosenBranch, current]);
  useEffect(() => {
    if (!current) { setComparison(null); setRanking(null); setExplorer(null); setLoading(false); return; }
    const abort = new AbortController(); setLoading(true); setError("");
    setComparison(null); setRanking(null);
    async function load() {
      const params = windowParams(current, window, filters);
      const r = await analyticsRequest<Ranking>(project, "hotspots", { ...params, offset: String(rankOffset), limit: "20" }, abort.signal);
      if (abort.signal.aborted) return;
      const effective = r.window.currentRunId;
      if (!effective) {
        setExplorer(null);
        setRanking(r);
        if (window.mode === "runs" && runs.find(run => run.id === current)?.analysisStatus !== "COMPLETE") {
          const unavailable = await analyticsRequest<Comparison>(project, "comparisons", { currentRunId: current }, abort.signal);
          if (!abort.signal.aborted) setComparison(unavailable);
        }
        return;
      }
      const compareParams: Record<string, string> = { currentRunId: effective, offset: String(offset), limit: "20" };
      for (const key of ["service", "rule", "severity", "search"] as const) if (filters[key]) compareParams[key] = filters[key];
      if (baseline && comparableBaselines(runs, effective).some(run => run.id === baseline)) compareParams.baselineRunId = baseline;
      else if (baseline) setBaseline("");
      if (state && tab === "lifecycle") compareParams.state = state;
      const [c, e] = await Promise.all([
        analyticsRequest<Comparison>(project, "comparisons", compareParams, abort.signal),
        analyticsRequest<Explorer>(project, "explorer", { ...params, offset: String(lifeOffset), limit: "20" }, abort.signal),
      ]);
      if (!abort.signal.aborted) { setComparison(c); setExplorer(e); setRanking(r); }
    }
    load().catch(e => { if (!abort.signal.aborted) setError(e.message); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });

    return () => abort.abort();
  }, [project, current, baseline, state, offset, rankOffset, lifeOffset, window, filters, tab, retry]);
  async function seed() {
    setImporting(true); setNotice(""); setError("");
    try { const result = await importProjectDemo(project); setDataset(result.dataset); setBranch("refs/heads/main"); setCurrent("");
      setNotice(`${result.runs} historical snapshots prepared; ${result.replays} unchanged snapshots reused.`); setRetry(n => n + 1); onImported?.(); }
    catch (e) { setError(e instanceof Error ? e.message : "History import failed"); }
    finally { setImporting(false); }
  }
  const selected = runs.find(r => r.id === current);
  const runName = (id: string | null) => runs.find(r => r.id === id)?.externalRunId ? runLabel(runs.find(r => r.id === id)!.externalRunId) : "None";
  const points = explorer?.points || [];
  function inspect(id: string) { setDrilldown(id); }
  const inspected = points.find(p => p.runId === drilldown);
  return <>
    <details className="analytics-provenance"><summary>Data sources & repository</summary>
      <p>{selected?.provenance === "DEMO" || project === DEMO_PROJECT ? "Constructed historical snapshots based on project rule metadata. Stored separately from recorded analysis." : "Recorded architecture snapshots."}</p>
      {context && <div className="analytics-source-strip"><span><b>{context.ruleExport.ruleCount}</b> exported rules</span><span><b>{context.scans.length}</b> repository graph scans</span><span><b>{context.governance.length}</b> pipeline records</span><Link to={`/projects/${project}/rules`}>Inspect rule evidence</Link>
        {context.demoEnabled && <button onClick={seed} disabled={importing || !context.ruleExport.ruleCount}>{importing ? "Preparing history…" : datasets.includes(context.demoDataset || "") ? "Refresh project history" : "Load project history"}</button>}</div>}
      <RepositoryLookup />
    </details>
    {notice && <p role="status" className="analytics-notice">{notice}</p>}
    <section className="analytics-controls analytics-primary-controls" aria-label="Analysis selection">
      <label>Dataset<select value={chosenDataset} onChange={e => { setDataset(e.target.value); setBranch(""); setBaseline(""); setLifeOffset(0); setRankOffset(0); setOffset(0); }}>
        {!datasets.length && <option value="">No history</option>}{datasets.map((d, i) => <option key={d} value={d}>{d === "recorded" ? "Recorded snapshots" : d === context?.demoDataset ? "Project history" : `History version ${d.match(/^project-history-v(\d+)-/)?.[1] || i + 1}`}</option>)}</select></label>
      <label>Branch<select value={chosenBranch} onChange={e => { setBranch(e.target.value); setBaseline(""); setLifeOffset(0); setRankOffset(0); setOffset(0); }}>{!branches.length && <option value="">No branches</option>}{branches.map(b => <option key={b} value={b}>{b.replace(/^refs\/heads\/(?:demo\/)?/, "")}</option>)}</select></label>
      <label>Analyze through<select value={current} onChange={e => { setCurrent(e.target.value); setBaseline(""); setLifeOffset(0); setOffset(0); setRankOffset(0); }} disabled={!visibleRuns.length}>{!visibleRuns.length && <option value="">No runs</option>}{visibleRuns.map(r => <option key={r.id} value={r.id}>{runLabel(r.externalRunId)} · {r.analysisStatus}</option>)}</select></label>
      <label>Compare with<select value={baseline} onChange={e => { setBaseline(e.target.value); setOffset(0); }} disabled={!current}><option value="">Previous run (automatic)</option>{comparableBaselines(runs, comparison?.currentRunId || ranking?.window.currentRunId || current).map(r => <option key={r.id} value={r.id}>{runLabel(r.externalRunId)}</option>)}</select></label>
      <button onClick={() => setRetry(n => n + 1)}>Refresh</button>
    </section>
    <section className="analytics-controls analytics-window-controls" aria-label="Analysis window">
      <label>Analysis window<select value={window.mode} onChange={e => { setWindow(w => ({ ...w, mode: e.target.value })); setLifeOffset(0); setRankOffset(0); setOffset(0); setDrilldown(""); if (e.target.value === "time" && (!filters.from || !filters.to) && visibleRuns.length) setFilters(f => ({ ...f, from: visibleRuns[0].analyzedAt.slice(0, 10), to: new Date(Date.parse(visibleRuns[visibleRuns.length - 1].analyzedAt) + 86400000).toISOString().slice(0, 10) })); }}><option value="runs">Latest comparable runs</option><option value="time">Date range</option></select></label>
      {window.mode === "runs" ? <label>Number of runs<select value={window.runs} onChange={e => { setWindow(w => ({ ...w, runs: e.target.value })); setLifeOffset(0); setRankOffset(0); setDrilldown(""); }}>{[5,10,20,50,1000].map(n => <option key={n} value={n}>{n === 1000 ? "All comparable runs" : `Latest ${n}`}</option>)}</select></label> : <>
        <label>From (inclusive UTC)<input type="date" value={filters.from} onChange={e => { setFilters(f => ({ ...f, from: e.target.value })); setLifeOffset(0); setRankOffset(0); }} /></label>
        <label>To (exclusive UTC)<input type="date" value={filters.to} onChange={e => { setFilters(f => ({ ...f, to: e.target.value })); setLifeOffset(0); setRankOffset(0); }} /></label>
      </>}
      {tab === "hotspots" && <label>Rank by<select value={window.grouping} onChange={e => { setWindow(w => ({ ...w, grouping: e.target.value })); setRankOffset(0); }}><option value="SERVICE">Services</option><option value="MODULE">Modules (source directories)</option><option value="RULE">Rules</option></select></label>}
      {ranking && <span className="analytics-window-summary">{ranking.window.actualRuns} comparable runs{ranking.window.currentRunId && ` · ends at ${runName(ranking.window.currentRunId)}`}<small>{ranking.window.boundaryReason}</small></span>}
    </section>
    {runs.length > 0 && <form key={JSON.stringify([filters.service,filters.rule,filters.severity,filters.search])} className="analytics-controls analytics-filterbar" onSubmit={e => { e.preventDefault(); const data = new FormData(e.currentTarget); setLifeOffset(0); setOffset(0); setRankOffset(0); setFilters(f => ({ ...f, service: String(data.get("service")), rule: String(data.get("rule")), severity: String(data.get("severity")), search: String(data.get("search")) })); }}>
      <label>Service<select name="service" defaultValue={filters.service}><option value="">All services</option>{explorer?.services.map(s => <option key={s}>{s}</option>)}</select></label>
      <label>Rule<select name="rule" defaultValue={filters.rule}><option value="">All rules</option>{explorer?.rules.map(s => <option key={s}>{s}</option>)}</select></label>
      <label>Severity<select name="severity" defaultValue={filters.severity}><option value="">All severities</option><option value="3">High</option><option value="2">Medium</option><option value="1">Low</option></select></label>
      <label>Evidence<input name="search" defaultValue={filters.search} placeholder="Rule, file, identity…" /></label><button>Apply filters</button>
      <button type="button" onClick={() => { setFilters(f => ({ ...f, service: "", rule: "", severity: "", search: "" })); setLifeOffset(0); setOffset(0); setRankOffset(0); }}>Clear filters</button>
    </form>}
    {selected && <details className="analytics-run-details"><summary>Run metadata · {selected.analysisStatus} · {selected.analyzedAt.slice(0, 10)}</summary><p className="analytics-meta">{selected.analyzedAt} · {selected.branchRef} · Pipeline {selected.pipelineOutcome || "unavailable"} · Gate {selected.gateOutcome || "unavailable"}<br />Commit {selected.commitRef} · Dataset {selected.dataset || "recorded"}<br />Rules {selected.ruleSetVersion} · Detector {selected.detectorVersion}</p></details>}
    {error ? <div role="alert" className="analytics-error"><h2>Analytics could not be loaded</h2><p>{error}</p><p>The requested history was not substituted with another dataset.</p></div>
      : runLoading || loading ? <p role="status" className="analytics-panel">Loading analytics…</p>
      : !runs.length ? <section className="analytics-panel"><h2>No snapshots yet</h2><p>This project has no analytics snapshots. Load project history under Data sources, or ingest complete conformance snapshots.</p>{!context && <Link to={`/analytics/dashboard?projectId=${DEMO_PROJECT}`}>Open reference fixture</Link>}</section>
      : <>
        {!comparison && !explorer && <section className="analytics-panel"><h2>No comparable snapshots in this window</h2><p>{ranking?.window.boundaryReason || "Select a complete analysis run and a valid period."}</p></section>}
        {tab === "dashboard" && comparison && !explorer && <ComparisonSummary comparison={comparison} runName={runName} />}
        {tab === "dashboard" && explorer && <>
          <div className="analytics-section-title"><h2>Architecture drift overview</h2><span>{explorer.completeRuns} complete / {explorer.incompleteRuns} incomplete runs in range</span></div>
          <p className="analytics-meta">Metrics, charts and hotspot cohorts share the selected analysis window. Lifecycle changes compare its latest run with the selected baseline.</p>
          {comparison && <ComparisonSummary comparison={comparison} runName={runName} />}
          {comparison?.comparable && <details className="analytics-panel analytics-transition-preview"><summary>Inspect changes in this comparison</summary><p>{comparison.newCount} new · {comparison.persistingCount} persistent · {comparison.resolvedCount} resolved</p>
            <div className="analytics-table-wrap"><table><thead><tr><th>Lifecycle</th><th>Rule</th><th>Service</th><th>Evidence</th></tr></thead><tbody>{comparison.violations.slice(0, 6).map(v => <tr key={v.finding.identity}><td>{v.state === "PERSISTING" ? "PERSISTENT" : v.state}</td><td>{v.finding.ruleKey}</td><td>{v.finding.entityName}</td><td>{v.finding.location}</td></tr>)}</tbody></table></div><Link to={link("lifecycle")}>Inspect all {comparison.totalResults} transitions →</Link>
          </details>}
          <HistoryChart title="Active violations over time" points={points} series={[{ key: "total", label: "Active violations" }]} onSelect={inspect} />
          <div className="analytics-chart-grid"><HistoryChart title="Severity trend" points={points} series={[{ key: "high", label: "High" }, { key: "medium", label: "Medium" }, { key: "low", label: "Low" }]} onSelect={inspect} />
          <HistoryChart title="Lifecycle movement" points={points} series={[{ key: "added", label: "New" }, { key: "resolved", label: "Resolved" }, { key: "persistent", label: "Persistent" }]} onSelect={inspect} /></div>
          <HistoryChart title="Violation density" unit="Violations / 1,000 LOC" points={points} series={[{ key: "density", label: "Density" }]} onSelect={inspect} /><p className="analytics-meta">{explorer.densityNote}</p>
          <AreaHeatmap points={points} onSelect={inspect} />
          {inspected && <section className="analytics-panel" aria-live="polite"><h2>{inspected.run}</h2><p>{inspected.at} · {inspected.status} · Gate {inspected.gateOutcome}</p><p>Active {displayCount(inspected.total)} · New {displayCount(inspected.added)} · Resolved {displayCount(inspected.resolved)} · Persisting {displayCount(inspected.persistent)}</p><p>{inspected.reason}</p><button onClick={() => { setCurrent(inspected.runId); setDrilldown(""); }}>Analyze through this run</button> <Link to={link("lifecycle")} onClick={() => setCurrent(inspected.runId)}>Inspect lifecycle →</Link></section>}
          <section className="analytics-panel"><button aria-expanded={showTable} onClick={() => setShowTable(v => !v)}>{showTable ? "Hide" : "Show"} chart data</button>{showTable && <TrendTable points={points} onSelect={setCurrent} />}</section>
        </>}
        {tab === "lifecycle" && <>
          {comparison && <><ComparisonSummary comparison={comparison} runName={runName} />
          <section className="analytics-panel"><div className="analytics-section-title"><h2>Selected run transitions</h2><label>State<select value={state} onChange={e => { setState(e.target.value); setOffset(0); }}><option value="">All states</option>{["NEW", "RESOLVED", "PERSISTING"].map(s => <option key={s}>{s}</option>)}</select></label></div>
          {!comparison.comparable ? <p>{comparison.reason}. Predecessor classification is unavailable.</p> : <><div className="analytics-table-wrap"><table><thead><tr><th>State</th><th>Rule</th><th>Service</th><th>Severity</th><th>Evidence</th></tr></thead><tbody>{comparison.violations.map(v => <tr key={v.finding.identity}><td>{v.state}</td><td>{v.finding.ruleName}</td><td>{v.finding.entityName}</td><td>{v.finding.severity}</td><td>{v.finding.location}<small>{v.finding.message}</small></td></tr>)}</tbody></table></div><Pagination offset={offset} total={comparison.totalResults} onChange={setOffset} /></>}</section></>}
          {explorer && <section className="analytics-panel"><h2>Historical violation identities</h2><p>Filtered observations in the date range. Streak ends at the selected run and stops at incomplete/version boundaries. A returning violation is new in that comparison and retains its historical identity.</p>
            <div className="analytics-table-wrap"><table><thead><tr><th>Finding / evidence</th><th>First / last observed (UTC)</th><th>Occurrences</th><th>Current streak</th><th>Recurrence</th><th>Timeline</th></tr></thead><tbody>{explorer.lifecycles.map(l => <tr key={l.finding.identity}><td>{l.finding.ruleName}<small>{l.finding.location}</small><small>{l.active ? "Active at selected run" : "Absent at selected run"}</small></td><td>{l.firstSeen.slice(0, 10)}<br />{l.lastSeen.slice(0, 10)}</td><td>{l.occurrences} / {l.observedRuns} complete runs</td><td>{l.currentStreak}</td><td>{(l.recurrence * 100).toFixed(1)}%</td><td><button aria-expanded={expanded === l.finding.identity} onClick={() => setExpanded(expanded === l.finding.identity ? "" : l.finding.identity)}>Inspect</button>{expanded === l.finding.identity && <div><code>{l.finding.identity}</code><p>{l.finding.message}</p><div className="analytics-lifecycle-strip">{l.timeline.map(o => <button key={o.runId} className={`analytics-observation ${o.state.toLowerCase()}`} title={`${o.run} · ${o.state} · ${o.at}`} onClick={() => setCurrent(o.runId)}>{runLabel(o.run)}<small>{o.state}</small></button>)}</div></div>}</td></tr>)}</tbody></table></div>
            {!explorer.totalLifecycles && <p>No findings match these filters.</p>}<Pagination offset={lifeOffset} total={explorer.totalLifecycles} onChange={setLifeOffset} />
          </section>}
        </>}
        {tab === "hotspots" && <>
          <p className="analytics-meta">The selected window is shared with Dashboard and Lifecycle. Module rankings project the same findings onto mapped source directories; counts are not duplicated.</p>
          {explorer && <HistoryChart title="Active violations in the ranking window" points={points} series={[{ key: "total", label: "Active violations" }]} onSelect={setCurrent} />}
          {ranking && <><div className="analytics-panel"><h2>Hotspot priorities</h2>{ranking.rows.map(row => <button className="analytics-rank-bar" key={row.entityId} onClick={() => { setLifeOffset(0); setOffset(0); setRankOffset(0); setFilters(f => ({ ...f, ...(ranking.grouping === "RULE" ? { rule: row.entityId } : ranking.grouping === "MODULE" ? { search: row.entityId } : { service: row.entityId }) })); setNotice(`Finding filter set to ${row.entityName}. Open Lifecycle to inspect.`); }}><span>{row.entityName}</span><span className="analytics-rank-track"><i style={{ width: `${row.score * 100}%` }} /></span><b>{row.score.toFixed(3)}</b></button>)}</div><HotspotTable ranking={ranking} currentName={runName(ranking.window.currentRunId)} onPage={setRankOffset} /></>}
        </>}
      </>}
    {tab === "history" && <>
      <section className="analytics-panel"><h2>Full branch snapshot register</h2><p>{visibleRuns.length} runs · {chosenDataset === "recorded" ? "Recorded snapshots" : "Project history"} · {chosenBranch}</p><div className="analytics-table-wrap"><table><thead><tr><th>Run / UTC time</th><th>Commit</th><th>Analysis</th><th>Pipeline / gate</th><th>Active</th><th>Inspect</th></tr></thead><tbody>{[...visibleRuns].reverse().map(r => <tr key={r.id}><td>{runLabel(r.externalRunId)}<small>{r.analyzedAt}</small></td><td><code>{r.commitRef}</code></td><td>{r.analysisStatus}</td><td>{r.pipelineOutcome} / {r.gateOutcome}</td><td>{displayCount(r.total)}</td><td><Link to={link("lifecycle")} onClick={() => setCurrent(r.id)}>Compare →</Link></td></tr>)}</tbody></table></div></section>
      {context && <ProjectEvidence context={context} />}
    </>}
  </>;
}

export function TrendTable({ points, onSelect }: { points: import('./api').HistoryPoint[]; onSelect: (id: string) => void }) {
  return <div className="analytics-table-wrap"><table><caption>Filtered trend observations · unavailable metrics are not zero</caption><thead><tr><th>Run</th><th>Active</th><th>New</th><th>Resolved</th><th>Persistent</th><th>Density / KLOC</th><th>Context</th></tr></thead><tbody>{points.map(p => <tr key={p.runId}><td><button onClick={() => onSelect(p.runId)}>{runLabel(p.run)}</button></td><td>{displayCount(p.total)}</td><td>{displayCount(p.added)}</td><td>{displayCount(p.resolved)}</td><td>{displayCount(p.persistent)}</td><td>{p.density == null ? "Unavailable" : p.density.toFixed(2)}</td><td>{p.reason}</td></tr>)}</tbody></table></div>;
}

type RepositoryActivity = { repository: string; branch: string; createdAt: string; pushedAt: string; language: string;
  commits: { sha: string; message: string; at: string; url: string }[]; coverage: string; source: string };
export function PublicActivity({ url }: { url: string }) {
  const [activity, setActivity] = useState<RepositoryActivity | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController(); setActivity(null); setError("");
    fetch(`/api/analytics/repository/activity?${new URLSearchParams({ url })}`, { signal: abort.signal }).then(async response => {
      const body = await response.json();
      if (!response.ok) throw new Error(body.detail || body.message || `GitHub activity unavailable (${response.status})`);
      if (!abort.signal.aborted) setActivity(body);
    }).catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [url]);
  return <section className="analytics-panel"><h2>GitHub repository activity</h2>
    {error ? <p role="status">{error}. Recorded architecture history is independent of this public activity preview.</p> : activity ? <>
      <p>{activity.repository} · {activity.language} · Default branch {activity.branch}<br />Created {activity.createdAt.slice(0, 10)} · Last push {activity.pushedAt.slice(0, 10)}</p>
      <p>{activity.coverage}</p><details><summary>Show {activity.commits.length} recent commits</summary><div className="analytics-table-wrap"><table><thead><tr><th>Commit</th><th>UTC time</th><th>Message</th></tr></thead><tbody>{activity.commits.map(c => <tr key={c.sha}><td><a href={c.url} target="_blank" rel="noreferrer">{c.sha.slice(0, 10)} ↗</a></td><td>{c.at}</td><td>{c.message}</td></tr>)}</tbody></table></div></details>
      <p className="analytics-meta">Source: {activity.source}. Commit activity does not establish historical architecture violations.</p>
    </> : <p role="status">Loading public repository activity…</p>}
  </section>;
}

export function ProjectEvidence({ context: c }: { context: ProjectContext }) {
  return <>
    <PublicActivity url={c.project.repositoryUrl} />
    <section className="analytics-panel"><h2>Validated rule context</h2><p>Current Component 1 export. Historical snapshots retain a fingerprinted copy of the rule metadata used for that dataset.</p><div className="analytics-table-wrap"><table><thead><tr><th>Rule / ADR</th><th>Constraint</th><th>Severity / evidence</th><th>Implementation mappings</th></tr></thead><tbody>{c.ruleExport.rules.map(r => <tr key={r.ruleId}><td>{r.ruleId}<small>{r.adrId}</small></td><td>{r.sourceElement} → {r.targetElement}<small>{r.decision} · {r.relationshipType}</small></td><td>{r.severity}<small>{r.validationStatus} / {r.architectReviewStatus}</small></td><td><details><summary>{r.sourceFiles.length} source / {r.targetFiles.length} target files</summary>{r.sourceFiles.map(f => <small key={`s${f}`}>Source: {f}</small>)}{r.targetFiles.map(f => <small key={`t${f}`}>Target: {f}</small>)}</details></td></tr>)}</tbody></table></div></section>
    <section className="analytics-panel"><h2>Recorded repository graph scans</h2><p>These are real graph extractions, displayed separately from architecture violation history.</p>{c.scans.map(s => <p key={s.scanId}><Link to={`/conformance/graph/${s.scanId}`}>{s.scannedAt.slice(0, 10)} · {s.fileCount} files · {s.dependencyCount} dependencies ↗</Link></p>)}{!c.scans.length && <p>No graph scans recorded.</p>}</section>
    <section className="analytics-panel"><h2>Recorded pipeline and governance context</h2><p>Decisions describe governance actions. An exception approval does not resolve a technical violation.</p><div className="analytics-table-wrap"><table><thead><tr><th>Run / PR</th><th>Time / commit</th><th>Outcome</th><th>Decision</th><th>Project mapping</th></tr></thead><tbody>{c.governance.map(r => <tr key={r.runId}><td>#{r.runId} / PR {r.prNumber}</td><td>{r.at}<small>{r.commit}</small></td><td>{r.classification} · {r.state}<small>{r.violations} reported findings</small></td><td>{r.decision ? `${r.decision.action} · ${r.decision.reviewer}` : "No review decision"}</td><td>{r.projectMappingMatches ? "Project ID matches" : "Legacy ID; repository match only"}</td></tr>)}</tbody></table></div>{!c.governance.length && <p>No pipeline records for this repository.</p>}
    {c.warnings.map(w => <p className="analytics-meta" key={w}>{w}</p>)}</section>
  </>;
}

export function ComparisonSummary({ comparison: c, runName }: { comparison: Comparison; runName: (id: string | null) => string }) {
  return <section className="analytics-comparison-summary"><div className="analytics-section-title"><h3>{runName(c.currentRunId)} <span>compared with {runName(c.predecessorRunId)}</span></h3></div>
    <ComparisonCards comparison={c} />
    {c.comparable ? <div className="analytics-equations"><span>Active {c.currentTotal} = New {c.newCount} + Persistent {c.persistingCount}</span><span>Previous {c.previousTotal} = Resolved {c.resolvedCount} + Persistent {c.persistingCount}</span><span>Net change {c.netChange! > 0 ? "+" : ""}{c.netChange} = New {c.newCount} − Resolved {c.resolvedCount}</span></div>
      : <p className="analytics-meta">{c.reason}. Lifecycle changes are unavailable until a compatible predecessor exists.</p>}
  </section>;
}

export function ComparisonCards({ comparison: c }: { comparison: Comparison }) {
  return <div className="analytics-cards">{[["Active violations", c.currentTotal], ["New", c.newCount], ["Resolved", c.resolvedCount], ["Persistent", c.persistingCount], ["Net violation change", c.netChange]].map(([label, value]) =>
    <article key={String(label)}><span>{label}</span><strong>{displayCount(value as number | null)}</strong></article>)}</div>;
}

export function HotspotTable({ ranking: r, currentName, onPage }: { ranking: Ranking; currentName: string; onPage: (offset: number) => void }) {
  return <section className="analytics-panel"><h2>Explainable hotspot ranking</h2><p>Relative prioritization on a 0–1 scale. This is not architecture health or failure probability.</p>
    <p className="analytics-meta">W = {r.window.actualRuns} · Current: {currentName} · Cohort: {r.cohortSize} {r.grouping.toLowerCase()} entities<br />{r.window.boundaryReason}{r.window.limitedHistory && " · Limited history: one run"}</p>
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
