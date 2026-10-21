import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArchitectureStyle, Project, ProjectInput, projectsApi } from "../../shared/api/client";
import "./CreateProjectPage.css";
import "./ProjectDashboardPage.css";

export function CreateProjectPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [state, setState] = useState<"idle" | "saving" | "error">("idle");
  const [message, setMessage] = useState("");

  useEffect(() => {
    void projectsApi.list().then(setProjects).catch((reason) => setMessage(reason instanceof Error ? reason.message : "Unable to load projects.")).finally(() => setLoading(false));
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input: ProjectInput = {
      name: String(form.get("projectName") ?? "").trim(),
      description: String(form.get("description") ?? "").trim(),
      repositoryUrl: String(form.get("repositoryUrl") ?? "").trim(),
      architectureStyles: [String(form.get("architectureStyle")) as ArchitectureStyle],
    };

    setState("saving");
    setMessage("");
    try {
      const project = await projectsApi.create(input);
      setProjects((current) => [project, ...current]);
      navigate(`/projects/${project.id}`);
    } catch (error) {
      setState("error");
      setMessage(error instanceof Error ? error.message : "Unable to create project.");
    }
  }

  return <main className="project-dashboard create-project-library">
    <header className="dashboard-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><div className="dashboard-topbar-right"><span>LOCAL ARCHITECTURE WORKSPACE</span><Link to="/" className="create-project-back">← Home</Link></div></header>
    <section className="dashboard-content"><div className="dashboard-heading"><div><p className="dashboard-kicker">PROJECT LIBRARY</p><h1>Your systems,<br /><em>all in one place.</em></h1><p>Open a project to work with its architecture, or create a new workspace.</p></div><button className="dashboard-create" type="button" onClick={() => { setModalOpen(true); setState("idle"); setMessage(""); }}>+ Create project</button></div>
      {message && state !== "error" && <p className="dashboard-message dashboard-error" role="alert">{message}</p>}
      {loading ? <div className="dashboard-empty">Loading local projects...</div> : projects.length === 0 ? <div className="dashboard-empty"><strong>No projects yet.</strong><span>Create a project to open your first architecture workspace.</span><button className="dashboard-empty-link" type="button" onClick={() => setModalOpen(true)}>Create your first project ↗</button></div> : <div className="dashboard-project-grid">{projects.map((project, index) => <Link className="dashboard-project-card" to={`/projects/${project.id}`} key={project.id}><div className="dashboard-project-number">{String(index + 1).padStart(2, "0")} <span>{project.status}</span></div><h2>{project.name}</h2><p>{project.description || "Architecture workspace"}</p><div className="dashboard-project-foot"><span>{project.architectureStyles[0]}</span><b>Open workspace ↗</b></div></Link>)}</div>}
    </section>
    {modalOpen && <div className="create-project-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setModalOpen(false); }}><section className="create-project-modal" role="dialog" aria-modal="true" aria-labelledby="create-project-title"><div className="create-project-modal-heading"><div><p className="create-project-kicker">NEW WORKSPACE</p><h2 id="create-project-title">Create a project</h2></div><button type="button" onClick={() => setModalOpen(false)} aria-label="Close create project dialog">×</button></div><p className="create-project-modal-intro">Give your system a home. Add its repository and a little context to get started.</p><form className="create-project-form" onSubmit={(event) => void handleSubmit(event)}><label>Project name <span>*</span><input required name="projectName" placeholder="Customer Platform" /></label><label>GitHub repository <span>*</span><input required type="url" name="repositoryUrl" placeholder="https://github.com/org/repository" /></label><label>Project description <small>Optional</small><textarea name="description" rows={3} placeholder="What are you building?" /></label><label>Architecture style <span>*</span><select required name="architectureStyle" defaultValue=""><option value="" disabled>Choose a starting point</option><option value="MICROSERVICES">Microservices</option><option value="LAYERED">Layered</option></select></label>{state === "error" && <p className="create-project-message error" role="alert">{message}</p>}<div className="create-project-modal-actions"><button type="button" onClick={() => setModalOpen(false)}>Cancel</button><button className="create-project-submit" disabled={state === "saving"} type="submit">{state === "saving" ? "Creating..." : "Create project"}<span>↗</span></button></div></form></section></div>}
  </main>;
}
