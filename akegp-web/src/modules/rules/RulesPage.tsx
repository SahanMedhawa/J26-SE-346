import { createContext, FormEvent, KeyboardEvent, ReactNode, useContext, useEffect, useMemo, useRef, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { ADR, ADRInput, ADRStatus, ApiClientError, ArchitectureStyle, Project, ProjectInput, ProjectStatus, adrsApi, projectsApi } from "../../shared/api/client";

type RuleModule = {
  label: string;
  path: "/rules/projects" | "/rules/adrs" | "/rules/architecture-rules" | "/rules/validation";
  icon: "projects" | "adrs" | "rules" | "validation";
};

const ruleModules: RuleModule[] = [
  { label: "Architecture Projects", path: "/rules/projects", icon: "projects" },
  { label: "ADR Management", path: "/rules/adrs", icon: "adrs" },
  { label: "Architecture Rule Engineering", path: "/rules/architecture-rules", icon: "rules" },
  { label: "Formal Rule & Mapping Validation", path: "/rules/validation", icon: "validation" },
];

const SelectedProjectContext = createContext<{
  selectedProject: Project | null;
  setSelectedProject: (project: Project) => void;
}>({ selectedProject: null, setSelectedProject: () => undefined });

function useSelectedProject() {
  return useContext(SelectedProjectContext);
}

function ModuleIcon({ type }: { type: RuleModule["icon"] }) {
  if (type === "projects") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7.5h6l1.5 2H20v9H4z" /><path d="M4 7.5V5h6l1.5 2.5" /></svg>;
  }
  if (type === "adrs") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h14v16H5z" /><path d="M8.5 8h7M8.5 12h7M8.5 16h4" /></svg>;
  }
  if (type === "rules") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 5h12v4H6zM6 15h12v4H6zM12 9v6M9 12h6" /></svg>;
  }
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 7 3.5v5.25c0 4.3-2.92 7.5-7 9.25-4.08-1.75-7-4.95-7-9.25V6.5z" /><path d="m8.5 12 2.25 2.25L15.75 9" /></svg>;
}

function WorkspaceHeader() {
  return (
    <div className="rules-header">
      <div>
        <p className="rules-kicker">Component 01 / Workspace</p>
        <h1>Architecture Rule Engineering <span>&amp; Validation</span></h1>
      </div>
      <div className="workspace-status"><span /> Framework active</div>
    </div>
  );
}

export function ProjectsPage() {
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
      setProjects(await projectsApi.list());
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
    const input: ProjectInput = {
      name: String(form.get("projectName") ?? "").trim(),
      description: String(form.get("description") ?? "").trim(),
      repositoryUrl: String(form.get("repositoryUrl") ?? "").trim(),
      architectureStyles: [style],
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
    <section className="rules-content">
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
            <div className="empty-icon"><ModuleIcon type="projects" /></div>
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
        {detailsState === "loading" ? <div className="modal-empty"><span className="loading-spinner" /><p>Loading project details...</p></div> : detailsState === "error" ? <div className="modal-empty"><p>Unable to load project details.</p><span>{detailsError}</span></div> : viewedProject && <ProjectDetails project={viewedProject} onCancel={() => setActiveModal(null)} onSelect={() => { setSelectedProject(viewedProject); setActiveModal(null); setNotice("Project selected for Component 1."); }} />}
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
    </section>
  );
}

const adrStatuses: ADRStatus[] = ["PROPOSED", "ACCEPTED", "DEPRECATED", "SUPERSEDED"];

export function ADRManagementPage() {
      const { setSelectedProject } = useSelectedProject();
      const [projects, setProjects] = useState<Project[]>([]);
      const [selectedProjectId, setSelectedProjectId] = useState("");
      const [adrs, setAdrs] = useState<ADR[]>([]);
      const [search, setSearch] = useState("");
      const [status, setStatus] = useState<ADRStatus | "all">("all");
      const [loadState, setLoadState] = useState<"loading" | "ready" | "error">("loading");
      const [notice, setNotice] = useState<{ text: string; success: boolean } | null>(null);
      const [modal, setModal] = useState<"create" | "edit" | "details" | null>(null);
      const [selectedADR, setSelectedADR] = useState<ADR | null>(null);
      const [confirmDelete, setConfirmDelete] = useState<ADR | null>(null);
      const [actionState, setActionState] = useState(false);

      useEffect(() => {
        void projectsApi.list().then((loaded) => {
          setProjects(loaded);
          if (loaded.length && !selectedProjectId) {
            setSelectedProjectId(loaded[0].id);
            setSelectedProject(loaded[0]);
          }
        }).catch((error) => setNotice({ text: getErrorMessage(error), success: false }));
      }, [selectedProjectId, setSelectedProject]);

      useEffect(() => {
        if (!selectedProjectId) {
          setAdrs([]);
          setLoadState("ready");
          return;
        }
        const project = projects.find((item) => item.id === selectedProjectId);
        if (project) setSelectedProject(project);
        setLoadState("loading");
        void adrsApi.list(selectedProjectId, { status, search }).then((loaded) => {
          setAdrs(loaded);
          setLoadState("ready");
        }).catch((error) => {
          setLoadState("error");
          setNotice({ text: getErrorMessage(error), success: false });
        });
      }, [projects, search, selectedProjectId, setSelectedProject, status]);

      const activeProjects = projects.filter((project) => project.status === "ACTIVE");
      const selectedProject = projects.find((project) => project.id === selectedProjectId);

      async function openDetails(adr: ADR) {
        setSelectedADR(adr);
        setModal("details");
        try {
          setSelectedADR(await adrsApi.get(adr.projectId, adr.id));
        } catch (error) {
          setModal(null);
          setNotice({ text: getErrorMessage(error), success: false });
        }
      }

      async function submitADR(event: FormEvent<HTMLFormElement>, editing: boolean) {
        event.preventDefault();
        if (!selectedProjectId) {
          setNotice({ text: "Select a project before saving an ADR.", success: false });
          return;
        }
        const form = new FormData(event.currentTarget);
        const input: ADRInput = {
          adrId: String(form.get("adrId") ?? "").trim(),
          title: String(form.get("title") ?? "").trim(),
          status: String(form.get("status") ?? "PROPOSED") as ADRStatus,
          context: String(form.get("context") ?? "").trim(),
          decision: String(form.get("decision") ?? "").trim(),
          consequences: String(form.get("consequences") ?? "").trim(),
        };
        setActionState(true);
        setNotice(null);
        try {
          if (editing && selectedADR) {
            await adrsApi.update(selectedADR.projectId, selectedADR.id, { ...input, version: selectedADR.version });
            setNotice({ text: "ADR updated successfully.", success: true });
          } else {
            await adrsApi.create(selectedProjectId, input);
            setNotice({ text: "ADR created successfully.", success: true });
          }
          setModal(null);
          await reloadADRs();
        } catch (error) {
          setNotice({ text: getErrorMessage(error), success: false });
        } finally {
          setActionState(false);
        }
      }

      async function reloadADRs() {
        if (!selectedProjectId) return;
        setAdrs(await adrsApi.list(selectedProjectId, { status, search }));
        setLoadState("ready");
      }

      async function deleteADR() {
        if (!confirmDelete) return;
        setActionState(true);
        try {
          await adrsApi.remove(confirmDelete.projectId, confirmDelete.id);
          setConfirmDelete(null);
          setNotice({ text: "ADR deleted successfully.", success: true });
          await reloadADRs();
        } catch (error) {
          setNotice({ text: getErrorMessage(error), success: false });
        } finally {
          setActionState(false);
        }
      }

      function startCreate() {
        const project = activeProjects[0];
        if (project) {
          setSelectedProjectId(project.id);
          setSelectedProject(project);
        }
        setSelectedADR(null);
        setNotice(null);
        setModal("create");
      }

      return <section className="rules-content adr-content">
        <div className="adr-intro">
          <div><p className="content-kicker">Architecture decisions</p><h2>ADR Management</h2><p className="projects-description">Capture decisions, context, and architectural consequences.</p></div>
          <button className="primary-button" type="button" disabled={!activeProjects.length} onClick={startCreate}><span aria-hidden="true">+</span> Create ADR</button>
        </div>
        <div className="adr-controls">
          <label className="search-field"><span className="search-icon" aria-hidden="true">/</span><span className="visually-hidden">Search ADRs</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search ADR ID, title, or decision..." /></label>
          <DarkSelect ariaLabel="Filter ADRs by status" value={status} onChange={(value) => setStatus(value as ADRStatus | "all")} options={[{ value: "all", label: "All statuses" }, ...adrStatuses.map((value) => ({ value, label: formatADRStatus(value) }))]} />
          <div className="adr-project-select"><span>Project</span><DarkSelect ariaLabel="Select project" value={selectedProjectId} onChange={setSelectedProjectId} options={[{ value: "", label: "Select project" }, ...projects.map((project) => ({ value: project.id, label: `${project.name}${project.status === "INACTIVE" ? " (Inactive)" : ""}` }))]} /></div>
        </div>
        {notice && <p className={`form-notice ${notice.success ? "success-notice" : "error-notice"}`} role="alert">{notice.text}</p>}
        <div className="projects-table-wrap adr-table-wrap"><table className="projects-table adr-table"><caption className="visually-hidden">Architecture decision records</caption><thead><tr><th>ADR</th><th>Status</th><th>Version</th><th>Linked Rules</th><th>Actions</th></tr></thead><tbody>
          {loadState === "loading" ? <tr><td colSpan={5}><div className="table-empty"><span className="loading-spinner" /><h3>Loading ADRs...</h3></div></td></tr> : loadState === "error" ? <tr><td colSpan={5}><div className="table-empty"><h3>Unable to load ADRs</h3><p>Check the API connection and try again.</p></div></td></tr> : !selectedProjectId || adrs.length === 0 ? <tr><td colSpan={5}><div className="table-empty"><h3>{selectedProjectId ? "No ADRs found" : "Select a project"}</h3><p>{selectedProjectId ? "Create an ADR or adjust your search and filters." : "Choose a registered project to view its architectural decisions."}</p></div></td></tr> : adrs.map((adr) => <tr key={adr.id}>
            <td><strong className="adr-id">{adr.adrId}</strong><small>{adr.title}</small></td><td><span className={`adr-status ${adr.status.toLowerCase()}`}>{formatADRStatus(adr.status)}</span></td><td>v{adr.version}</td><td>{adr.linkedRuleCount ?? "—"}</td><td><div className="row-actions adr-actions"><button className="details-action" type="button" onClick={() => void openDetails(adr)}>Open</button><button className="edit-action" type="button" onClick={() => { setSelectedADR(adr); setModal("edit"); }}><EditIcon /> Edit</button><button className="danger-button" type="button" onClick={() => setConfirmDelete(adr)}><TrashIcon /> Delete</button></div></td>
          </tr>)}
        </tbody></table></div>
        {modal === "create" && <ADRModal title="Create ADR" onClose={() => setModal(null)}><ADRForm projects={activeProjects} projectId={selectedProjectId} onProjectChange={setSelectedProjectId} onCancel={() => setModal(null)} onSubmit={(event) => void submitADR(event, false)} busy={actionState} /></ADRModal>}
        {modal === "edit" && selectedADR && <ADRModal title="Edit ADR" onClose={() => setModal(null)}><ADRForm adr={selectedADR} projects={projects} projectId={selectedADR.projectId} onProjectChange={setSelectedProjectId} onCancel={() => setModal(null)} onSubmit={(event) => void submitADR(event, true)} busy={actionState} /></ADRModal>}
        {modal === "details" && selectedADR && <ADRDetails adr={selectedADR} project={projects.find((project) => project.id === selectedADR.projectId)} onClose={() => setModal(null)} onEdit={() => setModal("edit")} />}
        {confirmDelete && <div className="modal-backdrop"><div className="project-modal confirmation-modal" role="alertdialog" aria-modal="true"><div className="modal-header"><h2>Delete ADR?</h2><button className="modal-close" type="button" onClick={() => setConfirmDelete(null)} aria-label="Close confirmation">x</button></div><div className="confirmation-content"><p>Are you sure you want to delete <strong>{confirmDelete.adrId}</strong> — {confirmDelete.title}?</p></div><div className="modal-actions"><button className="secondary-button" type="button" onClick={() => setConfirmDelete(null)}>Cancel</button><button className="danger-button" type="button" disabled={actionState} onClick={() => void deleteADR()}>{actionState ? "Deleting..." : "Confirm Delete"}</button></div></div></div>}
      </section>;
    }

    function formatADRStatus(status: ADRStatus) {
      return status.charAt(0) + status.slice(1).toLowerCase();
    }

    function DarkSelect({ ariaLabel, value, options, onChange }: { ariaLabel: string; value: string; options: { value: string; label: string }[]; onChange: (value: string) => void }) {
      const [open, setOpen] = useState(false);
      const [highlighted, setHighlighted] = useState(Math.max(0, options.findIndex((option) => option.value === value)));
      const containerRef = useRef<HTMLDivElement>(null);
      const selected = options.find((option) => option.value === value) ?? options[0];

      useEffect(() => {
        const close = (event: MouseEvent) => {
          if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener("mousedown", close);
        return () => document.removeEventListener("mousedown", close);
      }, []);

      function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
        if (event.key === "Escape") setOpen(false);
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
          event.preventDefault();
          const next = event.key === "ArrowDown" ? Math.min(highlighted + 1, options.length - 1) : Math.max(highlighted - 1, 0);
          setHighlighted(next);
          setOpen(true);
        }
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          if (open) {
            onChange(options[highlighted].value);
            setOpen(false);
          } else {
            setOpen(true);
          }
        }
      }

      return <div className="dark-select" ref={containerRef}>
        <button className="dark-select-trigger" type="button" aria-label={ariaLabel} aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen(!open)} onKeyDown={handleKeyDown}>
          <span>{selected?.label ?? "Select..."}</span><span className="dark-select-chevron" aria-hidden="true">⌄</span>
        </button>
        {open && <div className="dark-select-menu" role="listbox" aria-label={ariaLabel}>
          {options.map((option, index) => <button className={`dark-select-option${option.value === value ? " selected" : ""}${index === highlighted ? " highlighted" : ""}`} key={option.value || "empty"} type="button" role="option" aria-selected={option.value === value} onMouseEnter={() => setHighlighted(index)} onClick={() => { onChange(option.value); setOpen(false); }}>{option.label}</button>)}
        </div>}
      </div>;
    }

    function ADRModal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
      return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><div className="project-modal adr-modal" role="dialog" aria-modal="true"><div className="modal-header"><h2>{title}</h2><button className="modal-close" type="button" onClick={onClose} aria-label="Close modal">x</button></div>{children}</div></div>;
    }

    function ADRForm({ adr, projects, projectId, onProjectChange, onCancel, onSubmit, busy }: { adr?: ADR; projects: Project[]; projectId: string; onProjectChange: (id: string) => void; onCancel: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; busy: boolean }) {
      return <form onSubmit={onSubmit} className="adr-form"><div className="adr-form-body"><div className="adr-form-grid"><label>ADR ID *<input name="adrId" required defaultValue={adr?.adrId} placeholder="ADR-001" /></label><label>Title *<input name="title" required defaultValue={adr?.title} /></label><label>Project *<DarkSelect ariaLabel="Select project for ADR" value={projectId} onChange={onProjectChange} options={projects.map((project) => ({ value: project.id, label: project.name }))} /><input type="hidden" name="projectId" value={projectId} /></label><label>Status *<select name="status" required defaultValue={adr?.status ?? "PROPOSED"}>{adrStatuses.map((value) => <option key={value} value={value}>{formatADRStatus(value)}</option>)}</select></label></div><label>Context *<textarea name="context" required minLength={10} maxLength={5000} defaultValue={adr?.context} /></label><label>Decision *<textarea name="decision" required minLength={10} maxLength={5000} defaultValue={adr?.decision} /></label><label>Consequences *<textarea name="consequences" required minLength={10} maxLength={5000} defaultValue={adr?.consequences} /></label></div><div className="modal-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" type="submit" disabled={busy}>{busy ? "Saving..." : "Save ADR"}</button></div></form>;
    }

    function ADRDetails({ adr, project, onClose, onEdit }: { adr: ADR; project?: Project; onClose: () => void; onEdit: () => void }) {
      return <div className="modal-backdrop"><div className="project-modal adr-details-modal" role="dialog" aria-modal="true"><div className="modal-header"><h2>ADR Details · {adr.adrId}</h2><button className="modal-close" type="button" onClick={onClose} aria-label="Close details">x</button></div><div className="adr-details-body"><div className="adr-detail-title"><h3>{adr.title}</h3><span className={`adr-status ${adr.status.toLowerCase()}`}>{formatADRStatus(adr.status)}</span></div><p className="adr-meta">{project?.name ?? "Unknown project"} · Version {adr.version}</p><dl><dt>Context</dt><dd>{adr.context}</dd><dt>Decision</dt><dd className="adr-decision">{adr.decision}</dd><dt>Consequences</dt><dd>{adr.consequences}</dd><dt>Linked Architecture Rules</dt><dd>{adr.linkedRuleIds?.length ? <div className="linked-rule-list">{adr.linkedRuleIds.map((id) => <span key={id}>{id}</span>)}</div> : "No architecture rules linked yet."}</dd></dl></div><div className="modal-actions"><button className="secondary-button" type="button" onClick={onClose}>Cancel</button><button className="primary-button" type="button" onClick={onEdit}>Edit ADR</button></div></div></div>;
    }


function getErrorMessage(error: unknown) {
  if (error instanceof ApiClientError) {
    const fieldMessages = Object.values(error.fieldErrors);
    if (error.status === 409) return `${error.message} Please refresh the ADR and try again if this is a version conflict.`;
    return fieldMessages.length ? fieldMessages.join(" ") : error.message;
  }
  return error instanceof Error ? error.message : "The request could not be completed.";
}

function formatStyle(style: ArchitectureStyle) {
  return ({
    MICROSERVICES: "Microservices",
    LAYERED: "Layered",
    MICROSERVICES_AND_LAYERED: "Microservices + Layered",
  }[style]);
}

function formatStyles(styles: ArchitectureStyle[]) {
  return styles.map(formatStyle).join(", ");
}

function EditIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16-.75 4.75L8 20l11.5-11.5a2.12 2.12 0 0 0-3-3z" /><path d="m14.5 7.5 2 2" /></svg>;
}

function TrashIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14M10 11v6M14 11v6M8 7l1-3h6l1 3M7 7l1 14h8l1-14" /></svg>;
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
    <div className="project-details-identity"><div className="project-detail-icon"><ModuleIcon type="projects" /></div><div><span>Architecture project</span><strong>{project.name}</strong></div></div>
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
  const style = project?.architectureStyles[0] ?? "";
  return (
    <form className={`project-form ${project ? "edit-project-form" : "register-project-form"}`} onSubmit={onSubmit}>
      <label>Project Name <span>*</span><input required name="projectName" defaultValue={project?.name} placeholder="e.g. Customer Platform" /></label>
      <label>Architecture Style <span>*</span><select required name="architectureStyle" defaultValue={style}><option value="" disabled>Select architecture style</option><option value="MICROSERVICES">Microservices</option><option value="LAYERED">Layered</option><option value="MICROSERVICES_AND_LAYERED">Microservices + Layered</option></select></label>
      <label>Project Description <em>Optional</em><textarea name="description" defaultValue={project?.description ?? ""} rows={3} placeholder="Describe the system and its architectural context..." /></label>
      <label>Git Repository URL <span>*</span><input required type="url" name="repositoryUrl" defaultValue={project?.repositoryUrl} placeholder="https://github.com/organization/repository" /></label>
      <div className="modal-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" disabled={busy} type="submit">{busy ? "Saving..." : submitLabel}</button></div>
    </form>
  );
}

export function ComingSoonPage({ title, description }: { title: string; description: string }) {
  return (
    <section className="rules-content">
      <div className="content-heading">
        <div>
          <p className="content-kicker">Component 01 / Workspace</p>
          <h2>{title}</h2>
        </div>
        <span className="content-count">IN DEVELOPMENT</span>
      </div>
      <div className="coming-soon-state">
        <span className="coming-soon-line" />
        <p className="content-kicker">Coming soon</p>
        <h3>{title} is being prepared.</h3>
        <p>{description}</p>
      </div>
    </section>
  );
}

export function RulesPage() {
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);

  return (
    <SelectedProjectContext.Provider value={{ selectedProject, setSelectedProject }}>
    <div className="rules-workspace">
      <aside className="rules-sidebar">
        <div className="sidebar-label">Component 01</div>
        <div className="sidebar-title">Rule Engineering<br /><span>&amp; Validation</span></div>
        <nav className="module-nav" aria-label="Architecture rule engineering modules">
          {ruleModules.map((module) => (
            <NavLink
              className={({ isActive }) => `module-link${isActive ? " active" : ""}`}
              end
              key={module.path}
              to={module.path}
            >
              <span className="module-icon"><ModuleIcon type={module.icon} /></span>
              <span>{module.label}</span>
              <span className="module-arrow" aria-hidden="true">-&gt;</span>
            </NavLink>
          ))}
        </nav>
        <div className="selected-project">
          <span className="selected-project-label">Selected project</span>
          <strong>{selectedProject?.name ?? "No project selected"}</strong>
        </div>
        <div className="sidebar-footer">
          <span className="status-dot" /> AKEGP / PLATFORM
        </div>
      </aside>
      <div className="rules-main">
        <WorkspaceHeader />
        <Outlet />
      </div>
    </div>
    </SelectedProjectContext.Provider>
  );
}
