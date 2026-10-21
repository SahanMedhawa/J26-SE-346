import { Link, useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { Project, projectsApi } from "../../shared/api/client";
import "./ProjectDashboardPage.css";
import { useSelectedProject } from "../../shared/projects/SelectedProject";

function initialScanKey(projectId: string) {
  return `akegp.initial-scan.${projectId}`;
}

function savedInitialScanId(projectId: string) {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(initialScanKey(projectId)) ?? "";
}

const modules = [
  { number: "01", label: "Architecture rules", detail: "Define the boundaries your system should protect.", route: "rules" },
  { number: "02", label: "Conformance & dependencies", detail: "See how the codebase is shaped and connected.", route: "/conformance" },
  { number: "03", label: "Pipeline enforcement", detail: "Keep architecture checks close to every change.", route: "/enforcement" },
  { number: "04", label: "Architecture trends", detail: "Track drift, hotspots, and governance outcomes.", route: "/analytics" },
];

export function ProjectDashboardPage() {
	const { setSelectedProject } = useSelectedProject();
  const { projectId } = useParams();
  const [project, setProject] = useState<Project | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setProject(null);
    setError("");
    if (!projectId) return;
    void projectsApi.get(projectId).then((loaded) => {
      if (!active) return;
      if (!loaded || loaded.id !== projectId) throw new Error("The requested project could not be found.");
      setProject(loaded);
			setSelectedProject(loaded);
    }).catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "Unable to load project."); });
    return () => { active = false; };
  }, [projectId]);

  const initialScanId = project ? savedInitialScanId(project.id) : "";

  return <main className="project-workspace-screen">
    <header className="workspace-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><Link to="/" className="workspace-back">← Project library</Link></header>
    {error ? <div className="workspace-error"><p>{error}</p><Link to="/">Return to projects</Link></div> : project?.id === projectId && project ? <section className="workspace-content">
      <div className="workspace-project-heading"><div><p className="workspace-kicker">PROJECT / {project.status}</p><h1>{project.name}</h1><p>{project.description || "Your architecture governance workspace."}</p>{initialScanId ? <Link to={`/conformance/graph/${initialScanId}`}>Initial scan ↗</Link> : <a href={project.repositoryUrl} target="_blank" rel="noreferrer">Initial scan ↗</a>}</div><span className="workspace-style">{project.architectureStyles[0]}</span></div>
      <div className="workspace-module-heading"><span>WORKSPACE MODULES</span><p>Choose where you want to take the architecture next.</p></div>
      <div className="workspace-module-grid">{modules.map((module) => <Link className="workspace-module-card" to={`/projects/${encodeURIComponent(project.id)}/${module.route === "/analytics" ? "analytics/dashboard" : module.route.replace(/^\//, "")}`} state={module.route === "/conformance" ? { projectId: project.id, repositoryId: project.id, repositoryUrl: project.repositoryUrl, initialScanId } : undefined} key={module.number}><span className="workspace-module-number">{module.number}</span><div><h2>{module.label}</h2><p>{module.detail}</p></div><b>↗</b></Link>)}</div>
    </section> : <div className="workspace-loading">Opening project workspace...</div>}
  </main>;
}
