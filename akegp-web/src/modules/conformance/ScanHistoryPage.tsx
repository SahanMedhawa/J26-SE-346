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
    const directMatch = scansByProject.find(({ project }) => scan.repositoryId === project.id);
    const matchingUrls = scansByProject.filter(({ project }) => normalizeUrl(scan.repositoryUrl) === normalizeUrl(project.repositoryUrl));
    const projectMatch = directMatch ?? (matchingUrls.length === 1 ? matchingUrls[0] : undefined);
    if (projectMatch) {
      projectMatch.scans.push(scan);
      assignedScanIds.add(scan.scanId);
    }
  });
  const unassignedScans = scans.filter((scan) => !assignedScanIds.has(scan.scanId));
  const visibleProjects = selectedProjectId ? scansByProject.filter(({ project }) => project.id === selectedProjectId) : scansByProject;

  function renderScan(scan: Scan, sequence: number) {
    return <button type="button" className="scan-history-card" key={scan.scanId} onClick={() => navigate(`/conformance/graph/${scan.scanId}`)}><div className="scan-history-card-top"><span>{formatDate(scan.scannedAt)}</span><b className="scan-history-tag">{sequenceLabel(sequence)}</b><b>Open graph ↗</b></div><h2>{scan.repositoryUrl}</h2><p>{scan.repositoryId}</p><div className="scan-history-stats"><span><strong>{scan.fileCount.toLocaleString()}</strong> files</span><span><strong>{scan.dependencyCount.toLocaleString()}</strong> dependencies</span><span>{formatTime(scan.scannedAt)}</span></div></button>;
  }

  return <main className="scan-history-page">
    <header className="scan-history-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><Link to="/" className="scan-history-home">← Home</Link></header>
    <section className="scan-history-content">
      <div className="scan-history-heading"><div><p className="scan-history-kicker">CONFORMANCE / HISTORY</p><h1>{selectedProjectId ? "Project conformance." : "Project scans."}</h1><p>{selectedProjectId ? "View this project’s initial scan and conformance history." : "Open a project’s initial scan or review its scan history."}</p></div><button type="button" className="scan-history-new" onClick={() => navigate("/repository", { state: selectedProjectId ? { ...location.state } : undefined })}>+ New scan</button></div>
      {error && <p className="scan-history-error" role="alert">{error}</p>}
      {loading ? <div className="scan-history-empty">Loading project scans...</div> : scans.length === 0 ? <div className="scan-history-empty"><strong>No scans yet.</strong><span>Run your first repository scan to build a dependency graph.</span><button type="button" onClick={() => navigate("/repository")}>Start a new scan ↗</button></div> : <>
        {visibleProjects.map(({ project, scans: projectScans }) => {
          const chronologicalScans = [...projectScans].sort((a, b) => new Date(a.scannedAt).getTime() - new Date(b.scannedAt).getTime());
          return <section className="scan-history-project" key={project.id}><div className="scan-history-project-heading"><div><span>PROJECT</span><h2>{project.name}</h2></div><Link to={`/projects/${project.id}`}>Open project ↗</Link></div>{chronologicalScans.length ? <div className="scan-history-list">{chronologicalScans.map((scan, index) => renderScan(scan, index + 1))}</div> : <p className="scan-history-project-empty">No scans for this project yet.</p>}</section>;
        })}
        {!selectedProjectId && unassignedScans.length > 0 && <section className="scan-history-project"><div className="scan-history-project-heading"><div><span>OTHER SCANS</span><h2>Unassigned scans</h2></div></div><div className="scan-history-list">{unassignedScans.map((scan, index) => renderScan(scan, index + 1))}</div></section>}
      </>}
    </section>
  </main>;
}

function normalizeUrl(value: string) { return value.replace(/\/$/, "").toLowerCase(); }
function sequenceLabel(sequence: number) {
  if (sequence === 1) return "Initial scan";
  const labels = ["", "", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth", "Tenth"];
  return `${labels[sequence] ?? `${sequence}th`} scan`;
}
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(new Date(value)); }
function formatTime(value: string) { return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(value)); }
