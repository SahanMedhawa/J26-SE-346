import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiClientError, ArchitectureStyle, Project, ProjectInput, ProjectStatus, projectsApi } from "../api/client";
import { useSelectedProject } from "./SelectedProject";

export function ArchitectureProjects() {
  const { selectedProject, setSelectedProject } = useSelectedProject();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loadState, setLoadState] = useState<"loading" | "ready" | "error">("loading");
  const [loadError, setLoadError] = useState("");
  const [activeModal, setActiveModal] = useState<"register" | "details" | "edit" | null>(null);
  const [viewedProject, setViewedProject] = useState<Project | null>(null);
  const [detailsState, setDetailsState] = useState<"loading" | "ready" | "error">("ready");
  const [detailsError, setDetailsError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | ProjectStatus>("all");
  const [actionState, setActionState] = useState<string | null>(null);
  const [confirmProject, setConfirmProject] = useState<Project | null>(null);

  async function loadProjects() {
    setLoadState("loading");
    setLoadError("");
    try {
      const loaded = await projectsApi.list();
      setProjects(loaded);
      const selected = loaded.find((project) => project.id === selectedProject?.id);
      if (selected) setSelectedProject(selected);
      setLoadState("ready");
    } catch (error) {
      setLoadState("error");
      setLoadError(getErrorMessage(error));
    }
  }

  useEffect(() => { void loadProjects(); }, []);

  const visibleProjects = useMemo(() => projects.filter((project) => {
    const matchesSearch = project.name.toLowerCase().includes(search.toLowerCase());
    return matchesSearch && (statusFilter === "all" || project.status === statusFilter);
  }), [projects, search, statusFilter]);

  async function openDetails(project: Project) {
    setViewedProject(project);
    setActiveModal("details");
    setDetailsState("loading");
    setDetailsError("");
    try {
      setViewedProject(await projectsApi.get(project.id));
      setDetailsState("ready");
    } catch (error) {
      setDetailsState("error");
      setDetailsError(getErrorMessage(error));
    }
  }

  function openEdit(project: Project) {
    setViewedProject(project);
    setNotice("");
    setActiveModal("edit");
  }

  async function handleProjectSubmit(event: FormEvent<HTMLFormElement>, projectId?: string) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const style = form.get("architectureStyle") as ArchitectureStyle;
    const preserveExistingStyle = viewedProject && projectId === viewedProject.id
      && viewedProject.architectureStyles.includes("MICROSERVICES_AND_LAYERED") && !style;
    const input: ProjectInput = {
      name: String(form.get("projectName") ?? "").trim(),
      description: String(form.get("description") ?? "").trim(),
      repositoryUrl: String(form.get("repositoryUrl") ?? "").trim(),
      architectureStyles: preserveExistingStyle ? viewedProject.architectureStyles : [style],
    };
    setActionState(projectId ? `edit-${projectId}` : "register");
    setNotice("");
    try {
      await (projectId ? projectsApi.update(projectId, input) : projectsApi.create(input));
      setActiveModal(null);
      await loadProjects();
      setNotice(projectId ? "Project updated successfully." : "Project registered successfully.");
    } catch (error) {
      setNotice(getErrorMessage(error));
    } finally {
      setActionState(null);
    }
  }

  async function toggleStatus(project: Project) {
    setActionState(`status-${project.id}`);
    setNotice("");
    try {
      await projectsApi.updateStatus(project.id, project.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
      await loadProjects();
      setNotice("Project status updated successfully.");
    } catch (error) {
      setNotice(getErrorMessage(error));
    } finally {
      setActionState(null);
    }
  }

  return (
    <main className="rules-content architecture-projects" aria-label="Architecture Projects">
      <nav className="projects-back-nav" aria-label="Project navigation"><Link className="secondary-button" to="/home">← Back to Home</Link></nav>
      <div className="projects-intro">
        <div>
          <p className="content-kicker">Workspace overview</p>
          <h2>Architecture Projects</h2>
          <p className="projects-description">Register and manage the systems governed by your architecture rules and conformance framework.</p>
        </div>
        <button className="primary-button" type="button" onClick={() => { setNotice(""); setActiveModal("register"); }}>
          <span aria-hidden="true">+</span> Register Project
        </button>
      </div>
      <div className="projects-toolbar">
        <label className="search-field">
          <span className="search-icon" aria-hidden="true">/</span>
          <span className="visually-hidden">Search projects</span>
          <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search projects..." />
        </label>
        <label className="status-filter">
          <span className="visually-hidden">Filter by status</span>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as "all" | ProjectStatus)}>
            <option value="all">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </label>
      </div>
      {loadState === "error" && <div className="form-notice error-notice" role="alert">{loadError}<button type="button" onClick={() => void loadProjects()}>Retry</button></div>}
      <div className="projects-table-wrap">
        <table className="projects-table">
          <caption className="visually-hidden">Registered architecture projects</caption>
          <thead><tr><th>Project</th><th>Architecture style</th><th>ADRs / Rules</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>{loadState === "loading" ? <tr><td colSpan={5}><div className="table-empty"><span className="loading-spinner" /><h3>Loading projects...</h3></div></td></tr> : visibleProjects.length === 0 ? <tr><td colSpan={5}><div className="table-empty">
            <div className="empty-icon"><ProjectIcon /></div>
            <h3>{projects.length === 0 ? "No architecture projects yet" : "No matching projects"}</h3>
            <p>{projects.length === 0 ? "Register your first project to begin governing its architecture." : "Try a different search term or status filter."}</p>
          </div></td></tr> : visibleProjects.map((project) => <tr key={project.id}>
            <td><strong>{project.name}</strong><small>{project.description || "No description provided."}</small></td>
            <td><div className="style-badges">{project.architectureStyles.map((style) => <span className="style-badge" key={style}>{formatStyle(style)}</span>)}</div></td>
            <td><span className="unavailable-count" aria-label="ADR and rule counts unavailable until backend support is available">—</span></td>
            <td><span className={`project-status ${project.status.toLowerCase()}`}>{project.status}</span></td>
            <td><div className="row-actions">
              <button className="details-action" type="button" onClick={() => void openDetails(project)}>View Details</button>
              <button className="edit-action" type="button" onClick={() => openEdit(project)}><EditIcon /> Edit</button>
              {project.status === "ACTIVE" ? (
                <button className="status-action deactivate-action" type="button" disabled={actionState === `status-${project.id}`} onClick={() => setConfirmProject(project)}><StatusIcon type="deactivate" /> Deactivate</button>
              ) : (
                <button className="status-action activate-action" type="button" disabled={actionState === `status-${project.id}`} onClick={() => void toggleStatus(project)}><StatusIcon type="activate" /> Activate</button>
              )}
            </div></td>
          </tr>)}</tbody>
        </table>
      </div>
      {notice && <p className={`form-notice ${notice.includes("successfully") ? "success-notice" : "error-notice"}`} role="status">{notice}</p>}
      {activeModal === "register" && <ProjectModal title="Register Architecture Project" onClose={() => setActiveModal(null)}>
        <ProjectForm onCancel={() => setActiveModal(null)} onSubmit={(event) => void handleProjectSubmit(event)} submitLabel="Register Project" busy={actionState === "register"} />
      </ProjectModal>}
      {activeModal === "details" && <ProjectModal title="Architecture Project Details" onClose={() => setActiveModal(null)}>
        {detailsState === "loading" ? <div className="modal-empty"><span className="loading-spinner" /><p>Loading project details...</p></div> : detailsState === "error" ? <div className="modal-empty"><p>Unable to load project details.</p><span>{detailsError}</span></div> : viewedProject && <ProjectDetails project={viewedProject} onCancel={() => setActiveModal(null)} onSelect={() => { setSelectedProject(viewedProject); setActiveModal(null); setNotice("Project selected."); }} />}
      </ProjectModal>}
      {activeModal === "edit" && <ProjectModal title="Edit Architecture Project" onClose={() => setActiveModal(null)}>
        {viewedProject && <ProjectForm project={viewedProject} onCancel={() => setActiveModal(null)} onSubmit={(event) => void handleProjectSubmit(event, viewedProject.id)} submitLabel="Save Changes" busy={actionState === `edit-${viewedProject.id}`} />}
      </ProjectModal>}
      {confirmProject && <ConfirmationDialog
        project={confirmProject}
        busy={actionState === `status-${confirmProject.id}`}
        onCancel={() => setConfirmProject(null)}
        onConfirm={() => { setConfirmProject(null); void toggleStatus(confirmProject); }}
      />}
    </main>
  );
}

function formatStyle(style: ArchitectureStyle) {
  return ({
    MICROSERVICES: "Microservices",
    LAYERED: "Layered",
    MICROSERVICES_AND_LAYERED: "Microservices + Layered",
  }[style]);
}

function EditIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16-.75 4.75L8 20l11.5-11.5a2.12 2.12 0 0 0-3-3z" /><path d="m14.5 7.5 2 2" /></svg>;
}

function StatusIcon({ type }: { type: "activate" | "deactivate" }) {
  return type === "deactivate"
    ? <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5l14 14M19 5 5 19" /></svg>
    : <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(new Date(value));
}

function ProjectDetails({ project, onCancel, onSelect }: { project: Project; onCancel: () => void; onSelect: () => void }) {
  return <div className="project-details">
    <div className="project-details-identity"><div className="project-detail-icon"><ProjectIcon /></div><div><span>Architecture project</span><strong>{project.name}</strong></div></div>
    <div><span>Architecture style</span><div className="style-badges">{project.architectureStyles.map((style) => <span className="style-badge" key={style}>{formatStyle(style)}</span>)}</div></div>
    <div><span>Git repository</span><a href={project.repositoryUrl} target="_blank" rel="noreferrer">{project.repositoryUrl}</a></div>
    <div><span>Registration date</span><strong>{formatDate(project.registeredAt)}</strong></div>
    <div><span>Architecture records</span><strong>ADRs: — &nbsp; Rules: — <small className="unavailable-hint">(not available yet)</small></strong></div>
    <div><span>Status</span><strong className={`project-status ${project.status.toLowerCase()}`}>{project.status}</strong></div>
    <div className="details-description"><span>Description</span><p>{project.description || "No description provided."}</p></div>
    <div className="modal-actions details-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" type="button" onClick={onSelect}>Select Project</button></div>
  </div>;
}

function ConfirmationDialog({ project, busy, onCancel, onConfirm }: { project: Project; busy: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <div className="modal-backdrop" role="presentation">
    <div className="project-modal confirmation-modal" role="alertdialog" aria-modal="true" aria-labelledby="deactivate-title">
      <div className="modal-header"><div><p className="content-kicker destructive-kicker">Confirm action</p><h2 id="deactivate-title">Deactivate project?</h2></div><button className="modal-close" type="button" aria-label="Close confirmation" onClick={onCancel}>x</button></div>
      <div className="confirmation-content"><div className="confirmation-icon"><StatusIcon type="deactivate" /></div><p>This will mark <strong>{project.name}</strong> as inactive. You can activate it again later.</p></div>
      <div className="modal-actions confirmation-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="danger-button" type="button" disabled={busy} onClick={onConfirm}>{busy ? "Deactivating..." : "Deactivate"}</button></div>
    </div>
  </div>;
}

function ProjectModal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="project-modal" role="dialog" aria-modal="true" aria-labelledby="project-modal-title">
        <div className="modal-header"><div><p className="content-kicker">Architecture projects</p><h2 id="project-modal-title">{title}</h2></div><button className="modal-close" type="button" aria-label="Close modal" onClick={onClose}>x</button></div>
        {children}
      </div>
    </div>
  );
}

function ProjectForm({ project, onCancel, onSubmit, submitLabel, busy }: { project?: Project; onCancel: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; submitLabel: string; busy: boolean }) {
  const hasLegacyStyle = project?.architectureStyles.includes("MICROSERVICES_AND_LAYERED") ?? false;
  const style = hasLegacyStyle ? "" : project?.architectureStyles[0] ?? "";
  return (
    <form className={`project-form ${project ? "edit-project-form" : "register-project-form"}`} onSubmit={onSubmit}>
      <label>Project Name <span>*</span><input required name="projectName" defaultValue={project?.name} placeholder="e.g. Customer Platform" /></label>
      <label>Architecture Style <span>*</span><select required={!hasLegacyStyle} name="architectureStyle" defaultValue={style} aria-describedby={hasLegacyStyle ? "legacy-architecture-style" : undefined}><option value="" disabled>{hasLegacyStyle ? "Keep existing architecture style" : "Select architecture style"}</option><option value="MICROSERVICES">Microservices</option><option value="LAYERED">Layered</option></select></label>
      {hasLegacyStyle && <p id="legacy-architecture-style">Existing style: Microservices + Layered. This value will be preserved unless you select a different style.</p>}
      <label>Project Description <em>Optional</em><textarea name="description" defaultValue={project?.description ?? ""} rows={3} placeholder="Describe the system and its architectural context..." /></label>
      <label>Git Repository URL <span>*</span><input required type="url" name="repositoryUrl" defaultValue={project?.repositoryUrl} placeholder="https://github.com/organization/repository" /></label>
      <div className="modal-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" disabled={busy} type="submit">{busy ? "Saving..." : submitLabel}</button></div>
    </form>
  );
}

function ProjectIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7.5h6l1.5 2H20v9H4z" /><path d="M4 7.5V5h6l1.5 2.5" /></svg>;
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiClientError) return Object.values(error.fieldErrors).join(" ") || error.message;
  return error instanceof Error ? error.message : "The request could not be completed.";
}
