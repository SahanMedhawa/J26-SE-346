import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Project, projectsApi } from "../../shared/api/client";
import "./WorkflowPages.css";
import "./ProjectDashboardPage.css";

export function ProjectLibraryPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    void projectsApi.list().then(setProjects).catch((reason) => setError(reason instanceof Error ? reason.message : "Unable to load local projects.")).finally(() => setLoading(false));
  }, []);

  return <main className="project-dashboard">
    <header className="dashboard-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><div className="dashboard-topbar-right"><span>LOCAL ARCHITECTURE WORKSPACE</span><Link to="/settings" className="home-settings-icon" aria-label="Open settings" title="Settings">⚙</Link></div></header>
    <section className="dashboard-content"><div className="dashboard-heading"><div><p className="dashboard-kicker">PROJECT LIBRARY</p><h1>Choose a system<br /><em>to work on.</em></h1><p>Every project is a living map of its architecture, decisions, and delivery flow.</p></div><Link className="dashboard-create" to="/projects/new">+ Create project</Link></div>
      {error && <p className="dashboard-message dashboard-error" role="alert">{error}</p>}
      {loading ? <div className="dashboard-empty">Loading local projects...</div> : projects.length === 0 ? <div className="dashboard-empty"><strong>Your workspace is ready.</strong><span>Create your first project to begin mapping its architecture.</span><Link className="dashboard-empty-link" to="/projects/new">Create your first project ↗</Link></div> : <div className="dashboard-project-grid">{projects.map((project, index) => <Link className="dashboard-project-card" to={`/projects/${project.id}`} key={project.id}><div className="dashboard-project-number">{String(index + 1).padStart(2, "0")} <span>{project.status}</span></div><h2>{project.name}</h2><p>{project.description || "Architecture workspace"}</p><div className="dashboard-project-foot"><span>{project.architectureStyles[0]}</span><b>Open workspace ↗</b></div></Link>)}</div>}
    </section>
  </main>;
}
