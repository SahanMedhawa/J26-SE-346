import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { adrsApi, Project, projectsApi, rulesApi } from "../../shared/api/client";
import type { ArchitectureRule } from "../../shared/api/client";
import "./ConformanceViolationsPage.css";

type Scan = { scanId: string; repositoryId: string; repositoryUrl: string; scannedAt: string };
type Graph = { files: { path: string }[]; dependencies: { source: string; target: string }[] };
type RuleEdgeHighlight = { source: string; target: string; tone: "violation" | "satisfied"; missing?: boolean; synthetic?: boolean };
type ProjectRule = {
  ruleId: string; adrId: string; sourceElement: string; relationshipType: string; targetElement: string;
  decision: "ALLOWED" | "FORBIDDEN" | "REQUIRED"; severity: string;
  sourceFiles: string[]; targetFiles: string[];
};
type RuleCheck = {
  projectId: string; projectName: string; repositoryUrl: string; prNumber: number; scannedAt: string;
  rule: ProjectRule; outcome: "PASS" | "FAIL" | "NOT ASSESSED"; explanation: string;
};
type PRGraphResult = { scanId: string; scannedAt: string; checks: RuleCheck[]; graph: Graph };

export function ConformanceViolationsPage() {
  const { projectId } = useParams();
  const backTo = projectId ? `/projects/${projectId}/conformance` : "/conformance";
  const storageKey = `akegp.conformance-violations.${projectId ?? "all-projects"}`;
  const [projects, setProjects] = useState<Project[]>([]);
  const [results, setResults] = useState<Record<string, PRGraphResult>>(() => readCachedResults(storageKey));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [openGraphScanId, setOpenGraphScanId] = useState("");
  const [onlyRuleEdges, setOnlyRuleEdges] = useState(true);
  const graphFrameRef = useRef<HTMLIFrameElement>(null);
  const popupResult = Object.values(results).find((result) => result.scanId === openGraphScanId);
  const popupHighlights = popupResult ? createRuleEdgeHighlights(popupResult.graph, popupResult.checks) : [];

  useEffect(() => { setResults(readCachedResults(storageKey)); }, [storageKey]);

  useEffect(() => {
    let active = true;
    let refreshing = false;
    const refresh = async () => {
      if (refreshing) return;
      refreshing = true;
      try {
        const [allProjects, scanResponse, prResponse] = await Promise.all([
          projectsApi.list(), fetch("/api/conformance/graphs/history"), fetch("/api/enforcement/prs"),
        ]);
        if (!scanResponse.ok) throw new Error("PR dependency graphs could not be loaded.");
        const [scans, enforcementPRs] = await Promise.all([
          scanResponse.json() as Promise<Scan[]>,
          prResponse.ok ? prResponse.json() as Promise<{ repo: string; prNumber: number; classification: string }[]> : Promise.resolve([]),
        ]);
        const scopedProjects = allProjects.filter((project) => project.status === "ACTIVE" && (!projectId || project.id === projectId));
        const projectIds = new Set(scopedProjects.map((project) => project.id));
        const skipped = new Set(enforcementPRs
          .filter((pr) => ["documentation", "configuration", "skipped"].includes((pr.classification ?? "").toLowerCase()))
          .map((pr) => `${repositorySlug(pr.repo).toLowerCase()}#${pr.prNumber}`));
        const latestByPR = new Map<string, { scan: Scan; project: Project; prNumber: number }>();
        scans.forEach((scan) => {
          const match = scan.repositoryId.match(/^(.+):pr-(\d+):/);
          if (!match || !projectIds.has(match[1])) return;
          const prNumber = Number(match[2]);
          if (skipped.has(`${repositorySlug(scan.repositoryUrl).toLowerCase()}#${prNumber}`)) return;
          const project = scopedProjects.find((item) => item.id === match[1]);
          if (!project) return;
          const key = `${project.id}#${prNumber}`;
          const previous = latestByPR.get(key);
          if (!previous || new Date(scan.scannedAt).getTime() > new Date(previous.scan.scannedAt).getTime()) {
            latestByPR.set(key, { scan, project, prNumber });
          }
        });

        const rulesByProject = new Map<string, ProjectRule[]>();
        for (const project of scopedProjects) {
          const [rules, adrs] = await Promise.all([rulesApi.list(project.id), adrsApi.list(project.id)]);
          const adrById = new Map(adrs.map((adr) => [adr.id, adr]));
          const confirmedRules = await mapWithConcurrency(rules, 4, async (rule): Promise<ProjectRule | null> => {
            const adr = adrById.get(rule.adrId);
            if (!adr || adr.status === "DEPRECATED" || adr.status === "SUPERSEDED") return null;
            const review = await rulesApi.architectReview(project.id, rule.id);
            if (!review.confirmationEligible || review.reviewStatus !== "CONFIRMED") return null;
            const mappings = await rulesApi.implementationMappings(project.id, rule.id);
            return toProjectRule(rule, adr.adrId, mappings.sourceFiles, mappings.targetFiles);
          });
          rulesByProject.set(project.id, confirmedRules.filter((rule): rule is ProjectRule => rule !== null));
        }

        const targets = [...latestByPR.values()];
        const graphs = await Promise.all(targets.map(async ({ scan }) => {
          const response = await fetch(`/api/conformance/graphs/${encodeURIComponent(scan.scanId)}`);
          return response.ok ? response.json() as Promise<Graph> : null;
        }));
        const nextResults: Record<string, PRGraphResult> = {};
        targets.forEach(({ scan, project, prNumber }, index) => {
          const graph = graphs[index];
          if (!graph) return;
          const rules = rulesByProject.get(project.id) ?? [];
          const checks = rules.map((rule) => compareRule(project, scan, prNumber, rule, graph));
          nextResults[`${project.id}#${prNumber}`] = { scanId: scan.scanId, scannedAt: scan.scannedAt, checks, graph };
        });

        if (active) {
          setProjects(scopedProjects);
          setResults(nextResults);
          setError("");
          try { window.localStorage.setItem(storageKey, JSON.stringify(nextResults)); } catch { /* Browser storage can be disabled. */ }
        }
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : "Rule results could not be loaded.");
      } finally {
        refreshing = false;
        if (active) setLoading(false);
      }
    };

    void refresh();
    const timer = window.setInterval(() => { if (document.visibilityState === "visible") void refresh(); }, 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, [projectId, storageKey]);

  useEffect(() => {
    if (!openGraphScanId) return;
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === "Escape") setOpenGraphScanId(""); };
    const sendGraphSettings = () => graphFrameRef.current?.contentWindow?.postMessage({
      type: "AKEGP_RULE_EDGE_HIGHLIGHTS", edges: popupHighlights, onlyRuleEdges,
    }, window.location.origin);
    const onGraphReady = (event: MessageEvent) => {
      if (event.origin === window.location.origin && event.source === graphFrameRef.current?.contentWindow
        && event.data?.type === "AKEGP_GRAPH_READY") sendGraphSettings();
    };
    window.addEventListener("keydown", closeOnEscape);
    window.addEventListener("message", onGraphReady);
    sendGraphSettings();
    return () => { window.removeEventListener("keydown", closeOnEscape); window.removeEventListener("message", onGraphReady); };
  }, [openGraphScanId, onlyRuleEdges, popupHighlights]);

  const title = projectId ? projects.find((project) => project.id === projectId)?.name ?? "Project" : "All projects";
  const prResults = Object.entries(results).sort(([, a], [, b]) =>
    new Date(b.scannedAt ?? b.checks[0]?.scannedAt ?? 0).getTime() - new Date(a.scannedAt ?? a.checks[0]?.scannedAt ?? 0).getTime());
  return <main className="conformance-violations-page">
    <header className="conformance-violations-topbar">
      <Link to="/" className="conformance-violations-brand">AKEGP</Link>
      <Link to={projectId ? `/projects/${projectId}` : "/"} className="conformance-violations-back">{projectId ? "Project workspace" : "Home"}</Link>
    </header>
    <section className="conformance-violations-content" aria-labelledby="rule-results-title">
      <nav className="conformance-section-tabs" aria-label="Conformance sections">
        <Link className="conformance-section-tab" to={backTo}>Scan history</Link>
        <Link className="conformance-section-tab active" to={`${backTo}/violations`}>Violations</Link>
      </nav>
      {error && <p className="conformance-violations-error" role="alert">{error}</p>}
      <section className="conformance-rule-checks" aria-label="Rule results by pull request">
        <header><div><h1 id="rule-results-title">Rule results by pull request</h1></div></header>
        {loading && !prResults.length && <p className="conformance-violations-loading">Loading confirmed rules and PR graphs...</p>}
        {!loading && !prResults.length && <p className="conformance-violations-loading">No confirmed rule results are available for this project yet.</p>}
        {prResults.map(([key, result]) => {
          const first = result.checks[0];
          const passed = result.checks.filter((check) => check.outcome === "PASS").length;
          const failed = result.checks.filter((check) => check.outcome === "FAIL").length;
          const unassessed = result.checks.filter((check) => check.outcome === "NOT ASSESSED").length;
          const violations = result.checks.filter((check) => check.outcome === "FAIL");
          return <article className="conformance-pr-check-group" key={key} role="button" tabIndex={result.scanId ? 0 : -1}
            aria-label={`Open dependency graph for pull request #${first?.prNumber ?? key.split("#")[1]}`}
            onClick={() => { if (result.scanId) { setOnlyRuleEdges(true); setOpenGraphScanId(result.scanId); } }}
            onKeyDown={(event) => { if (result.scanId && (event.key === "Enter" || event.key === " ")) { event.preventDefault(); setOnlyRuleEdges(true); setOpenGraphScanId(result.scanId); } }}>
            <header><div><span>{first?.projectName ?? title}</span><h3>Pull request #{first?.prNumber ?? key.split("#")[1]}</h3></div><span className="conformance-pr-check-counts"><b>{passed} pass</b><b>{failed} fail</b><b>{unassessed} need attention</b></span></header>
            {violations.length > 0 && <section className="conformance-pr-violations"><h4>Violations</h4>{violations.map((check) => <div className="conformance-pr-violation" key={check.rule.ruleId}>
              <strong>{check.rule.ruleId} · ADR {check.rule.adrId}</strong>
              <p>{check.rule.sourceElement} <b>{check.rule.relationshipType.replace(/_/g, " ").toLowerCase()}</b> {check.rule.targetElement}</p>
              <small>{check.explanation}</small>
            </div>)}</section>}
            <p className="conformance-pr-graph-prompt">Select this pull request card to open its scanned dependency graph.</p>
            <ul className="conformance-rule-check-list">{result.checks.map((check) => <li className={`conformance-rule-check conformance-rule-check-${check.outcome.toLowerCase().replace(/ /g, "-")}`} key={check.rule.ruleId}>
              <span className="conformance-rule-check-status">{check.outcome}</span>
              <div><strong>{check.rule.ruleId} · ADR {check.rule.adrId}</strong><p>{check.rule.sourceElement} <b>{check.rule.relationshipType.replace(/_/g, " ").toLowerCase()}</b> {check.rule.targetElement}</p><small>{check.explanation}</small></div>
            </li>)}</ul>
          </article>;
        })}
      </section>
    </section>
    {openGraphScanId && <div className="conformance-graph-modal-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) setOpenGraphScanId(""); }}>
      <section className="conformance-graph-modal" role="dialog" aria-modal="true" aria-label="Scanned pull request dependency graph">
        <header><div className="conformance-graph-modal-title"><h2>Scanned dependency graph</h2><p><span className="fail">Fail / missing</span><span className="pass">Pass</span><span>{onlyRuleEdges ? "Other edges hidden" : "All edges shown"}</span></p></div><div className="conformance-graph-modal-actions"><button type="button" onClick={() => setOnlyRuleEdges((value) => !value)}>{onlyRuleEdges ? "Show all edges" : "Pass/fail edges only"}</button><button type="button" onClick={() => setOpenGraphScanId("")} aria-label="Close dependency graph">Close <span aria-hidden="true">x</span></button></div></header>
        <iframe ref={graphFrameRef} title="Dependency graph from this scan" src={`/conformance/graph/${encodeURIComponent(openGraphScanId)}?embedded=1`}
          onLoad={() => graphFrameRef.current?.contentWindow?.postMessage({ type: "AKEGP_RULE_EDGE_HIGHLIGHTS", edges: popupHighlights, onlyRuleEdges }, window.location.origin)} />
      </section>
    </div>}
  </main>;
}

function toProjectRule(rule: ArchitectureRule, adrId: string, sourceFiles: string[], targetFiles: string[]): ProjectRule {
  return { ruleId: rule.ruleId, adrId, sourceElement: rule.sourceElement, relationshipType: rule.relationshipType,
    targetElement: rule.targetElement, decision: rule.decision, severity: rule.severity, sourceFiles, targetFiles };
}

function compareRule(project: Project, scan: Scan, prNumber: number, rule: ProjectRule, graph: Graph): RuleCheck {
  const paths = new Set(graph.files.map((file) => normalizePath(file.path)));
  const sources = rule.sourceFiles.filter((file) => paths.has(normalizePath(file)));
  const targets = rule.targetFiles.filter((file) => paths.has(normalizePath(file)));
  const base = { projectId: project.id, projectName: project.name, repositoryUrl: scan.repositoryUrl,
    prNumber, scannedAt: scan.scannedAt, rule };
  if (rule.decision === "ALLOWED") return { ...base, outcome: "PASS", explanation: "This relationship is allowed by the confirmed rule." };
  if (!sources.length || !targets.length) return {
    ...base, outcome: "NOT ASSESSED",
    explanation: `Mapped files do not match this PR graph (source ${sources.length}/${rule.sourceFiles.length}, target ${targets.length}/${rule.targetFiles.length}).`,
  };
  const sourceSet = new Set(sources.map(normalizePath));
  const targetSet = new Set(targets.map(normalizePath));
  const edges = graph.dependencies.filter((edge) => sourceSet.has(normalizePath(edge.source)) && targetSet.has(normalizePath(edge.target)));
  const failed = rule.decision === "FORBIDDEN" ? edges.length > 0 : edges.length === 0;
  return {
    ...base,
    outcome: failed ? "FAIL" : "PASS",
    explanation: rule.decision === "FORBIDDEN"
      ? failed ? `${edges.length} forbidden dependency edge${edges.length === 1 ? "" : "s"} found.` : "No forbidden dependency edge was found."
      : failed ? "The required dependency is missing from this PR graph." : "The required dependency is present in this PR graph.",
  };
}

function normalizePath(path: string) { return path.replace(/\\/g, "/").replace(/^\.\//, "").toLowerCase(); }
function graphEdgeKey(source: string, target: string) { return `${normalizePath(source)}=>${normalizePath(target)}`; }
function createRuleEdgeHighlights(graph: Graph, checks: RuleCheck[]): RuleEdgeHighlight[] {
  const highlights = new Map<string, RuleEdgeHighlight>();
  graph.dependencies.forEach(({ source, target }) => {
    const matching = checks.filter(({ rule }) => rule.sourceFiles.some((path) => normalizePath(path) === normalizePath(source))
      && rule.targetFiles.some((path) => normalizePath(path) === normalizePath(target)));
    if (matching.some(({ rule, outcome }) => rule.decision === "FORBIDDEN" && outcome === "FAIL")) {
      highlights.set(graphEdgeKey(source, target), { source, target, tone: "violation" });
    } else if (matching.some(({ rule, outcome }) => outcome === "PASS" && rule.decision !== "FORBIDDEN")) {
      highlights.set(graphEdgeKey(source, target), { source, target, tone: "satisfied" });
    }
  });

  const graphPaths = new Set(graph.files.map(({ path }) => normalizePath(path)));
  const existingEdges = new Set(graph.dependencies.map(({ source, target }) => graphEdgeKey(source, target)));
  checks.filter(({ outcome, rule }) => outcome === "PASS" && rule.decision === "FORBIDDEN")
    .forEach(({ rule }) => rule.sourceFiles.forEach((source) => rule.targetFiles.forEach((target) => {
      const key = graphEdgeKey(source, target);
      if (graphPaths.has(normalizePath(source)) && graphPaths.has(normalizePath(target)) && !existingEdges.has(key) && !highlights.has(key)) {
        highlights.set(key, { source, target, tone: "satisfied", synthetic: true });
      }
    })));
  checks.filter(({ outcome, rule }) => outcome === "FAIL" && rule.decision === "REQUIRED")
    .forEach(({ rule }) => rule.sourceFiles.forEach((source) => rule.targetFiles.forEach((target) => {
      const key = graphEdgeKey(source, target);
      if (graphPaths.has(normalizePath(source)) && graphPaths.has(normalizePath(target)) && !existingEdges.has(key) && !highlights.has(key)) {
        highlights.set(key, { source, target, tone: "violation", missing: true, synthetic: true });
      }
    })));
  return [...highlights.values()];
}
async function mapWithConcurrency<T, R>(items: T[], concurrency: number, mapper: (item: T) => Promise<R>): Promise<R[]> {
  const results = new Array<R>(items.length);
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (next < items.length) {
      const index = next++;
      results[index] = await mapper(items[index]);
    }
  }));
  return results;
}
function readCachedResults(key: string): Record<string, PRGraphResult> {
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(key) ?? "{}");
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as Record<string, PRGraphResult> : {};
  } catch { return {}; }
}
function repositorySlug(value: string) {
  try { return new URL(value.startsWith("https://") ? value : `https://github.com/${value}`).pathname.split("/").filter(Boolean).slice(0, 2).join("/"); }
  catch { return value; }
}
