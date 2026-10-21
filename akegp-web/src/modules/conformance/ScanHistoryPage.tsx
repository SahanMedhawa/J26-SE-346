import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { Project, projectsApi } from "../../shared/api/client";
import "./ScanHistoryPage.css";

type Scan = {
  scanId: string;
  repositoryId: string;
  repositoryUrl: string;
  scannedAt: string;
  fileCount: number;
  dependencyCount: number;
};
type EnforcementPR = { repo: string; prNumber: number; classification: string };

export function ScanHistoryPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { projectId } = useParams();
  const selectedProjectId = projectId || (location.state as { projectId?: string } | null)?.projectId;
  const conformancePath = selectedProjectId ? `/projects/${selectedProjectId}/conformance` : "/conformance";
  const [scans, setScans] = useState<Scan[]>([]);
  const [enforcementPRs, setEnforcementPRs] = useState<EnforcementPR[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  useEffect(() => {
    let active = true;
    let refreshing = false;
    const loadLatest = async () => {
      if (refreshing) return;
      refreshing = true;
      try {
        const [scanResponse, prResponse] = await Promise.all([
          fetch("/api/conformance/graphs/history"),
          fetch("/api/enforcement/prs"),
        ]);
        if (!scanResponse.ok) throw new Error("Previous scans could not be loaded.");
        const [latestScans, latestPRs] = await Promise.all([
          scanResponse.json() as Promise<Scan[]>,
          prResponse.ok ? prResponse.json() as Promise<EnforcementPR[]> : Promise.resolve([] as EnforcementPR[]),
        ]);
        if (active) { setScans(latestScans); setEnforcementPRs(latestPRs); setError(""); }
      } catch (reason) {
        if (active && loading) setError(reason instanceof Error ? reason.message : "Previous scans could not be loaded.");
      } finally {
        refreshing = false;
        if (active) setLoading(false);
      }
    };

    void projectsApi.list().then((loadedProjects) => { if (active) setProjects(loadedProjects); })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "Projects could not be loaded."); });
    void loadLatest();
    const timer = window.setInterval(() => { if (document.visibilityState === "visible") void loadLatest(); }, 10000);
    const onFocus = () => { if (document.visibilityState === "visible") void loadLatest(); };
    window.addEventListener("focus", onFocus);
    return () => { active = false; window.clearInterval(timer); window.removeEventListener("focus", onFocus); };
  }, []);

  const skippedPRs = new Set(enforcementPRs
    .filter((pr) => ["documentation", "configuration", "skipped"].includes(pr.classification.toLowerCase()))
    .map((pr) => `${repositorySlug(pr.repo)}#${pr.prNumber}`));
  const visibleScans = scans.filter((scan) => {
    const prNumber = scan.repositoryId.match(/:pr-(\d+):/)?.[1];
    return !prNumber || !skippedPRs.has(`${repositorySlug(scan.repositoryUrl)}#${prNumber}`);
  });
  const normalizedSearch = search.trim().toLowerCase().replace(/^#/, "");
  const matchingScans = visibleScans.filter((scan) => {
    if (!normalizedSearch) return true;
    const project = projects.find((item) => scan.repositoryId === item.id || scan.repositoryId.startsWith(`${item.id}:pr-`)
      || normalizeUrl(scan.repositoryUrl) === normalizeUrl(item.repositoryUrl));
    const prNumber = scan.repositoryId.match(/:pr-(\d+):/)?.[1] ?? "";
    return [repositoryName(scan.repositoryUrl), scan.repositoryUrl, scan.repositoryId, `pr ${prNumber}`, `pr #${prNumber}`, project?.name ?? ""]
      .some((value) => value.toLowerCase().includes(normalizedSearch));
  });
  const scansByProject = projects.map((project) => ({ project, scans: [] as Scan[] }));
  const assignedScanIds = new Set<string>();
  matchingScans.forEach((scan) => {
    const directMatch = scansByProject.find(({ project }) => scan.repositoryId === project.id || scan.repositoryId.startsWith(`${project.id}:pr-`));
    const matchingUrls = scansByProject.filter(({ project }) => normalizeUrl(scan.repositoryUrl) === normalizeUrl(project.repositoryUrl));
    const match = directMatch ?? (matchingUrls.length === 1 ? matchingUrls[0] : undefined);
    if (match) {
      match.scans.push(scan);
      assignedScanIds.add(scan.scanId);
    }
  });

  const unassignedScans = matchingScans.filter((scan) => !assignedScanIds.has(scan.scanId));
  const visibleProjects = (selectedProjectId ? scansByProject.filter(({ project }) => project.id === selectedProjectId) : scansByProject)
    .sort((a, b) => newestTime(b.scans) - newestTime(a.scans));
  const newestScanId = matchingScans.reduce<Scan | undefined>((latest, scan) =>
    !latest || new Date(scan.scannedAt).getTime() > new Date(latest.scannedAt).getTime() ? scan : latest, undefined)?.scanId;
  const otherScansFirst = !selectedProjectId && newestTime(unassignedScans) > newestTime(visibleProjects.flatMap(({ scans: projectScans }) => projectScans));
  const otherScansSection = !selectedProjectId && unassignedScans.length > 0 && <section className="scan-history-project">
    <div className="scan-history-project-heading"><div><span>OTHER REPOSITORIES</span><h2>Other scans</h2></div></div>
    <div className="scan-history-list">{[...unassignedScans].sort((a, b) => new Date(b.scannedAt).getTime() - new Date(a.scannedAt).getTime()).map(renderScan)}</div>
  </section>;

  function renderScan(scan: Scan) {
    const prNumber = scan.repositoryId.match(/:pr-(\d+):/)?.[1];
    const isPullRequest = Boolean(prNumber);
    const isBaseline = scan.repositoryId === scan.repositoryId.split(":pr-")[0];
    return <button type="button" className="scan-history-card" key={scan.scanId} onClick={() => navigate(`/conformance/graph/${scan.scanId}`)}>
      <div className="scan-history-card-top">
        <span>{formatDate(scan.scannedAt)} at {formatTime(scan.scannedAt)}</span>
        <span className={`scan-history-tag ${isPullRequest ? "pull-request" : "baseline"}`}>
          {isPullRequest ? `Pull request #${prNumber}` : scan.scanId === newestScanId ? "Latest project scan" : "Project scan"}
        </span>
      </div>
      <h3>{isPullRequest ? "Code changes checked" : isBaseline ? "Project baseline" : "Repository scan"}</h3>
      <p className="scan-history-repository">{repositoryName(scan.repositoryUrl)}</p>
      <div className="scan-history-stats">
        <span><strong>{scan.fileCount.toLocaleString()}</strong> files mapped</span>
        <span><strong>{scan.dependencyCount.toLocaleString()}</strong> connections found</span>
      </div>
      <span className="scan-history-card-action">View dependency map <span aria-hidden="true">→</span></span>
    </button>;
  }

  return <main className="scan-history-page">
    <header className="scan-history-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><Link to={selectedProjectId ? `/projects/${selectedProjectId}` : "/"} className="scan-history-home">← {selectedProjectId ? "Project workspace" : "Home"}</Link></header>
    <section className="scan-history-content">
      <nav className="conformance-section-tabs" aria-label="Conformance sections">
        <Link className="conformance-section-tab active" to={conformancePath}>Scan history</Link>
        <Link className="conformance-section-tab" to={`${conformancePath}/violations`}>Violations</Link>
      </nav>
      <div className="scan-history-heading">
        <div><p className="scan-history-kicker">CONFORMANCE / HISTORY</p><h1>{selectedProjectId ? "Project scan history" : "Your project scans"}</h1>
          <p>{selectedProjectId ? "See the latest code checks and explore how this project is connected. Documentation-only and configuration-only PRs are hidden." : "Recent code checks are shown first. Documentation-only and configuration-only PRs are hidden."}</p></div>
        <div className="scan-history-heading-actions">
          <button type="button" className="scan-history-new" onClick={() => navigate("/repository", { state: selectedProjectId ? { ...location.state } : undefined })}>Start a scan</button>
        </div>
      </div>
      <label className="scan-history-search"><span>Search project scans</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Project, repository, or PR number"/><span className="scan-history-search-icon" aria-hidden="true">⌕</span></label>
      {error && <p className="scan-history-error" role="alert">{error}</p>}
      {loading ? <div className="scan-history-empty">Loading your project scans...</div> : matchingScans.length === 0 ? <div className="scan-history-empty"><strong>{search ? "No matching scans" : "No scans to show"}</strong><span>{search ? "Try another project name, repository, or PR number." : scans.length ? "Skipped documentation and configuration PR scans are hidden here." : "Start a repository scan to build your first dependency map."}</span>{search ? <button type="button" onClick={() => setSearch("")}>Clear search</button> : !scans.length && <button type="button" onClick={() => navigate("/repository")}>Start a scan</button>}</div> : <>
        {otherScansFirst && otherScansSection}
        {visibleProjects.map(({ project, scans: projectScans }) => {
          const newestFirst = [...projectScans].sort((a, b) => new Date(b.scannedAt).getTime() - new Date(a.scannedAt).getTime());
          if (normalizedSearch && newestFirst.length === 0) return null;
          return <section className="scan-history-project" key={project.id}>
            <div className="scan-history-project-heading"><div><span>PROJECT</span><h2>{project.name}</h2></div><Link to={`/projects/${project.id}`}>View project details</Link></div>
            {newestFirst.length ? <div className="scan-history-list">{newestFirst.map(renderScan)}</div> : <p className="scan-history-project-empty">No scans yet. A scan will appear here after this project is checked.</p>}
          </section>;
        })}
        {!otherScansFirst && otherScansSection}
      </>}
    </section>
  </main>;
}

function normalizeUrl(value: string) { return value.replace(/\/$/, "").toLowerCase(); }
function newestTime(scans: Scan[]) { return Math.max(0, ...scans.map((scan) => new Date(scan.scannedAt).getTime())); }
function repositoryName(value: string) {
  return repositorySlug(value) || "Repository";
}
function repositorySlug(value: string) {
  try { return new URL(value.startsWith("https://") ? value : `https://github.com/${value}`).pathname.split("/").filter(Boolean).slice(0, 2).join("/").toLowerCase(); }
  catch { return ""; }
}
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(new Date(value)); }
function formatTime(value: string) { return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(value)); }
