import { FormEvent, KeyboardEvent, ReactNode, useEffect, useRef, useState } from "react";
import { Link, Navigate, NavLink, Outlet, useLocation, useParams } from "react-router-dom";
import { ADR, ADRInput, ADRStatus, ApiClientError, Project, adrsApi, projectsApi } from "../../shared/api/client";
import { useSelectedProject } from "./rulesWorkspace";
import { ProjectWorkspaceLayout } from "../../shared/projects/ProjectWorkspaceLayout";

type RuleModule = {
  label: string;
  path: "adrs" | "architecture-rules" | "validation";
  icon: "adrs" | "rules" | "validation";
};

const ruleModules: RuleModule[] = [
  { label: "ADR Management", path: "adrs", icon: "adrs" },
  { label: "Architecture Rule Engineering", path: "architecture-rules", icon: "rules" },
  { label: "Rule Validation", path: "validation", icon: "validation" },
];

function ModuleIcon({ type }: { type: RuleModule["icon"] }) {
  if (type === "adrs") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h14v16H5z" /><path d="M8.5 8h7M8.5 12h7M8.5 16h4" /></svg>;
  }
  if (type === "rules") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 5h12v4H6zM6 15h12v4H6zM12 9v6M9 12h6" /></svg>;
  }
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 7 3.5v5.25c0 4.3-2.92 7.5-7 9.25-4.08-1.75-7-4.95-7-9.25V6.5z" /><path d="m8.5 12 2.25 2.25L15.75 9" /></svg>;
}

const adrStatuses: ADRStatus[] = ["PROPOSED", "ACCEPTED", "DEPRECATED", "SUPERSEDED"];

export function ADRManagementPage() {
      const { selectedProject } = useSelectedProject();
      const selectedProjectId = selectedProject?.id ?? "";
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
        let active = true;
        if (!selectedProjectId) return;
        setLoadState("loading");
        void adrsApi.list(selectedProjectId, { status, search }).then((loaded) => {
          if (!active) return;
          setAdrs(loaded);
          setLoadState("ready");
        }).catch((error) => {
          if (!active) return;
          setLoadState("error");
          setNotice({ text: getErrorMessage(error), success: false });
        });
        return () => { active = false; };
      }, [search, selectedProjectId, status]);

      async function openDetails(adr: ADR) {
        setSelectedADR(adr);
        setModal("details");
        try {
          setSelectedADR(await adrsApi.get(selectedProjectId, adr.id));
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
            await adrsApi.update(selectedProjectId, selectedADR.id, { ...input, version: selectedADR.version });
            setNotice({ text: "ADR updated successfully.", success: true });
          } else {
            const created = await adrsApi.create(selectedProjectId, input);
            setNotice({ text: `ADR ${created.adrId} created successfully.`, success: true });
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
          await adrsApi.remove(selectedProjectId, confirmDelete.id);
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
        setSelectedADR(null);
        setNotice(null);
        setModal("create");
      }

      return <section className="rules-content adr-content">
        <div className="adr-intro">
          <div><p className="content-kicker">Architecture decisions</p><h2>ADR Management</h2><p className="projects-description">Capture decisions, context, and architectural consequences.</p></div>
          <button className="primary-button" type="button" disabled={selectedProject?.status !== "ACTIVE"} onClick={startCreate}><span aria-hidden="true">+</span> Create ADR</button>
        </div>
        <div className="adr-controls">
          <label className="search-field"><span className="search-icon" aria-hidden="true">/</span><span className="visually-hidden">Search ADRs</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search ADR ID, title, or decision..." /></label>
          <DarkSelect ariaLabel="Filter ADRs by status" value={status} onChange={(value) => setStatus(value as ADRStatus | "all")} options={[{ value: "all", label: "All statuses" }, ...adrStatuses.map((value) => ({ value, label: formatADRStatus(value) }))]} />
        </div>
        {notice && <p className={`form-notice ${notice.success ? "success-notice" : "error-notice"}`} role="alert">{notice.text}</p>}
        <div className="projects-table-wrap adr-table-wrap"><table className="projects-table adr-table"><caption className="visually-hidden">Architecture decision records</caption><thead><tr><th>ADR</th><th>Status</th><th>Version</th><th>Linked Rules</th><th>Actions</th></tr></thead><tbody>
          {loadState === "loading" ? <tr><td colSpan={5}><div className="table-empty"><span className="loading-spinner" /><h3>Loading ADRs...</h3></div></td></tr> : loadState === "error" ? <tr><td colSpan={5}><div className="table-empty"><h3>Unable to load ADRs</h3><p>Check the API connection and try again.</p></div></td></tr> : !selectedProjectId || adrs.length === 0 ? <tr><td colSpan={5}><div className="table-empty"><h3>{selectedProjectId ? "No ADRs found" : "Select a project"}</h3><p>{selectedProjectId ? "Create an ADR or adjust your search and filters." : "Choose a registered project to view its architectural decisions."}</p></div></td></tr> : adrs.map((adr) => <tr key={adr.id}>
            <td><strong className="adr-id">{adr.adrId}</strong><small>{adr.title}</small></td><td><span className={`adr-status ${adr.status.toLowerCase()}`}>{formatADRStatus(adr.status)}</span></td><td>v{adr.version}</td><td>{adr.linkedRuleCount ?? "—"}</td><td><div className="row-actions adr-actions"><button className="details-action" type="button" onClick={() => void openDetails(adr)}>Open</button><button className="edit-action" type="button" onClick={() => { setSelectedADR(adr); setModal("edit"); }}><EditIcon /> Edit</button><button className="danger-button" type="button" onClick={() => setConfirmDelete(adr)}><TrashIcon /> Delete</button></div></td>
          </tr>)}
        </tbody></table></div>
        {modal === "create" && <ADRModal title="Create ADR" onClose={() => setModal(null)}><ADRForm project={selectedProject} onCancel={() => setModal(null)} onSubmit={(event) => void submitADR(event, false)} busy={actionState} /></ADRModal>}
        {modal === "edit" && selectedADR && <ADRModal title="Edit ADR" identifier={selectedADR.adrId} onClose={() => setModal(null)}><ADRForm adr={selectedADR} project={selectedProject} onCancel={() => setModal(null)} onSubmit={(event) => void submitADR(event, true)} busy={actionState} /></ADRModal>}
        {modal === "details" && selectedADR && <ADRDetails adr={selectedADR} project={selectedProject ?? undefined} onClose={() => setModal(null)} onEdit={() => setModal("edit")} />}
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

    function ADRModal({ title, identifier, onClose, children }: { title: string; identifier?: string; onClose: () => void; children: ReactNode }) {
      return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><div className="project-modal adr-modal record-form-modal" role="dialog" aria-modal="true"><div className="modal-header"><h2 className="record-modal-title">{title}{identifier && <span className="record-id-badge">{identifier}</span>}</h2><button className="modal-close" type="button" onClick={onClose} aria-label="Close modal">x</button></div>{children}</div></div>;
    }

    function ADRForm({ adr, project, onCancel, onSubmit, busy }: { adr?: ADR; project: Project | null; onCancel: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; busy: boolean }) {
      return <form onSubmit={onSubmit} className="adr-form">
        <div className="adr-form-body">
          <div className="adr-form-grid">
            <label className="record-form-full-width">Title *<input name="title" required defaultValue={adr?.title} /></label>
            <label>Project<input readOnly value={project?.name ?? ""} /></label>
            <label>Status *<select name="status" required defaultValue={adr?.status ?? "PROPOSED"}>{adrStatuses.map((value) => <option key={value} value={value}>{formatADRStatus(value)}</option>)}</select></label>
          </div>
          <label>Context *<textarea name="context" required minLength={10} maxLength={5000} defaultValue={adr?.context} /></label>
          <label>Decision *<textarea name="decision" required minLength={10} maxLength={5000} defaultValue={adr?.decision} /></label>
          <label>Consequences *<textarea name="consequences" required minLength={10} maxLength={5000} defaultValue={adr?.consequences} /></label>
        </div>
        <div className="modal-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" type="submit" disabled={busy}>{busy ? "Saving..." : "Save ADR"}</button></div>
      </form>;
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

function EditIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16-.75 4.75L8 20l11.5-11.5a2.12 2.12 0 0 0-3-3z" /><path d="m14.5 7.5 2 2" /></svg>;
}

function TrashIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14M10 11v6M14 11v6M8 7l1-3h6l1 3M7 7l1 14h8l1-14" /></svg>;
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
  const { selectedProject, setSelectedProject } = useSelectedProject();
  const { projectId } = useParams();
  const location = useLocation();
  const [loadedId, setLoadedId] = useState("");
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoadedId("");
    setError("");
    if (!projectId) return;
    void projectsApi.get(projectId).then((project) => {
      if (!active) return;
      if (!project || project.id !== projectId) throw new Error("The requested project could not be found.");
      setSelectedProject(project);
      setLoadedId(projectId);
    }).catch((reason) => {
      if (active) setError(getErrorMessage(reason));
    });
    return () => { active = false; };
  }, [projectId, retry, setSelectedProject]);

  // Keep old bookmarks with an explicit ID or existing selection; never choose a list fallback.
  if (!projectId) {
    const legacyId = new URLSearchParams(location.search).get("projectId") || selectedProject?.id;
    if (legacyId) return <Navigate replace to={`/projects/${encodeURIComponent(legacyId)}${location.pathname}${location.search}`} />;
    return <section className="rules-content"><p role="alert">Select a project from the Project Library to open Architecture Rules.</p><Link to="/projects">Open Project Library</Link></section>;
  }
  if (error) return <section className="rules-content"><p className="form-notice error-notice" role="alert">Unable to open this project: {error}</p><button type="button" onClick={() => setRetry((value) => value + 1)}>Retry</button> <Link to="/projects">Open Project Library</Link></section>;
  if (loadedId !== projectId || selectedProject?.id !== projectId) return <section className="rules-content"><p role="status">Loading project workspace...</p></section>;
  const basePath = `/projects/${encodeURIComponent(projectId)}/rules`;

  return <ProjectWorkspaceLayout component="01" title="Architecture Rule Engineering" subtitle="& Validation" status="Framework active" statusIndicator sidebarTitle="Rule Engineering" navLabel="Architecture rule engineering modules"
    projectId={projectId} projectName={selectedProject.name}
    modules={ruleModules.map(module => ({ label: module.label, to: `${basePath}/${module.path}`, icon: <ModuleIcon type={module.icon} /> }))}>
    <Outlet key={projectId} />
  </ProjectWorkspaceLayout>;
}
