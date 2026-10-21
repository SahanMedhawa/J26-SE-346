import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
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

export function ScanHistoryPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedProjectId = (location.state as { projectId?: string } | null)?.projectId;
  const [scans, setScans] = useState<Scan[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    void Promise.all([
      fetch("/api/conformance/graphs/history").then(async (response) => {
        if (!response.ok) throw new Error("Previous scans could not be loaded.");
        return response.json() as Promise<Scan[]>;
      }),
      projectsApi.list(),
    ])
      .then(([loadedScans, loadedProjects]) => { setScans(loadedScans); setProjects(loadedProjects); })
      .catch((reason) => setError(reason instanceof Error ? reason.message : "Previous scans could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  const scansByProject = projects.map((project) => ({ project, scans: [] as Scan[] }));
  const assignedScanIds = new Set<string>();
  scans.forEach((scan) => {
    const directMatch = scansByProject.find(({ project }) => scan.repositoryId === project.id || scan.repositoryId.startsWith(`${project.id}:pr-`));
    const matchingUrls = scansByProject.filter(({ project }) => normalizeUrl(scan.repositoryUrl) === normalizeUrl(project.repositoryUrl));
    const match = directMatch ?? (matchingUrls.length === 1 ? matchingUrls[0] : undefined);
    if (match) {
      match.scans.push(scan);
      assignedScanIds.add(scan.scanId);
    }
  });

  const unassignedScans = scans.filter((scan) => !assignedScanIds.has(scan.scanId));
  const visibleProjects = (selectedProjectId ? scansByProject.filter(({ project }) => project.id === selectedProjectId) : scansByProject)
    .sort((a, b) => newestTime(b.scans) - newestTime(a.scans));
  const newestScanId = scans.reduce<Scan | undefined>((latest, scan) =>
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
    <header className="scan-history-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><Link to="/" className="scan-history-home">← Home</Link></header>
    <section className="scan-history-content">
      <div className="scan-history-heading">
        <div><p className="scan-history-kicker">CONFORMANCE / HISTORY</p><h1>{selectedProjectId ? "Project scan history" : "Your project scans"}</h1>
          <p>{selectedProjectId ? "See the latest code checks and explore how this project is connected." : "Recent code checks are shown first. Open a scan to explore the dependency map."}</p></div>
        <button type="button" className="scan-history-new" onClick={() => navigate("/repository", { state: selectedProjectId ? { ...location.state } : undefined })}>Start a scan</button>
      </div>
      {error && <p className="scan-history-error" role="alert">{error}</p>}
      {loading ? <div className="scan-history-empty">Loading your project scans...</div> : scans.length === 0 ? <div className="scan-history-empty"><strong>No scans yet</strong><span>Start a repository scan to build your first dependency map.</span><button type="button" onClick={() => navigate("/repository")}>Start a scan</button></div> : <>
        {otherScansFirst && otherScansSection}
        {visibleProjects.map(({ project, scans: projectScans }) => {
          const newestFirst = [...projectScans].sort((a, b) => new Date(b.scannedAt).getTime() - new Date(a.scannedAt).getTime());
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
  try { return new URL(value).pathname.split("/").filter(Boolean).slice(0, 2).join("/") || "Repository"; }
  catch { return "Repository"; }
}
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(new Date(value)); }
function formatTime(value: string) { return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(value)); }
