import { KeyboardEvent, useEffect, useRef, useState } from "react";
import { ApiClientError, ArchitectureRule, ImplementationMappingsInput, Project, RepositoryFiles, rulesApi } from "../../shared/api/client";

type Side = "sourceFiles" | "targetFiles";
type LoadState = "loading" | "ready" | "error";
type MappingState = {
  key: string;
  status: LoadState;
  saved: ImplementationMappingsInput;
  draft: ImplementationMappingsInput;
  error: string;
};
type PickerState = { side: Side | ""; selected: string[] };

function samePaths(left: string[], right: string[]) {
  return JSON.stringify([...left].sort()) === JSON.stringify([...right].sort());
}

function errorMessage(error: unknown) {
  if (error instanceof ApiClientError) {
    return Object.values(error.fieldErrors).join(" ") || error.message;
  }
  return error instanceof Error ? error.message : "The request could not be completed.";
}

export function ImplementationMappingsModal({ projects, initialProjectId, onClose }: {
  projects: Project[];
  initialProjectId: string;
  onClose: () => void;
}) {
  // This draft selection intentionally does not update the workspace's selected project.
  const [projectId, setProjectId] = useState(initialProjectId);
  const [ruleUuid, setRuleUuid] = useState("");
  const [ruleList, setRuleList] = useState<{ projectId: string; status: LoadState; rules: ArchitectureRule[]; error: string }>({ projectId: "", status: "loading", rules: [], error: "" });
  const [mapping, setMapping] = useState<MappingState | null>(null);
  const [repository, setRepository] = useState<{ projectId: string; status: LoadState; data?: RepositoryFiles; error: string } | null>(null);
  const [picker, setPicker] = useState<PickerState | null>(null);
  const [search, setSearch] = useState("");
  const [retry, setRetry] = useState(0);
  const [repositoryRetry, setRepositoryRetry] = useState(0);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ text: string; success: boolean } | null>(null);
  const saveLock = useRef(false);
  const mounted = useRef(false);
  const dialog = useRef<HTMLDivElement>(null);
  const pickerOpen = picker !== null;
  const key = `${projectId}/${ruleUuid}`;
  const project = projects.find((item) => item.id === projectId);
  const rules = ruleList.projectId === projectId ? ruleList.rules : [];
  const rule = rules.find((item) => item.id === ruleUuid);
  const ready = !!rule && mapping?.key === key && mapping.status === "ready";
  const dirty = ready && (!samePaths(mapping.draft.sourceFiles, mapping.saved.sourceFiles) || !samePaths(mapping.draft.targetFiles, mapping.saved.targetFiles));
  const pickerDirty = !!picker && (picker.side
    ? !samePaths(picker.selected, ready ? mapping.draft[picker.side] : [])
    : picker.selected.length > 0);
  const hasRepository = !!project?.repositoryUrl?.trim();

  useEffect(() => {
    mounted.current = true;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog.current?.focus();
    return () => {
      mounted.current = false;
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    let active = true;
    setRuleList({ projectId, status: "loading", rules: [], error: "" });
    if (!projectId) return;
    void rulesApi.list(projectId).then((loaded) => {
      if (active) setRuleList({ projectId, status: "ready", rules: loaded, error: "" });
    }).catch((error: unknown) => {
      if (active) setRuleList({ projectId, status: "error", rules: [], error: errorMessage(error) });
    });
    return () => { active = false; };
  }, [projectId, retry]);

  useEffect(() => {
    let active = true;
    if (!projectId || !ruleUuid) return;
    const empty = { sourceFiles: [], targetFiles: [] };
    setMapping({ key, status: "loading", saved: empty, draft: empty, error: "" });
    void rulesApi.implementationMappings(projectId, ruleUuid).then((loaded) => {
      const files = { sourceFiles: loaded.sourceFiles, targetFiles: loaded.targetFiles };
      if (active) setMapping({ key, status: "ready", saved: files, draft: files, error: "" });
    }).catch((error: unknown) => {
      if (active) setMapping({ key, status: "error", saved: empty, draft: empty, error: errorMessage(error) });
    });
    return () => { active = false; };
  }, [key, projectId, ruleUuid, retry]);

  useEffect(() => {
    let active = true;
    if (!pickerOpen || !projectId || !hasRepository) return;
    if (repository?.projectId === projectId && repository.status === "ready") return;
    setRepository({ projectId, status: "loading", error: "" });
    void rulesApi.repositoryFiles(projectId).then((data) => {
      if (active) setRepository({ projectId, status: "ready", data, error: "" });
    }).catch((error: unknown) => {
      if (active) setRepository({ projectId, status: "error", error: errorMessage(error) });
    });
    return () => { active = false; };
  }, [pickerOpen, projectId, hasRepository, repositoryRetry]);

  function allowDiscard() {
    return !saveLock.current && (!(dirty || pickerDirty) || window.confirm("Discard unsaved implementation file mapping changes?"));
  }

  function close() {
    if (allowDiscard()) onClose();
  }

  function changeProject(id: string) {
    if (id === projectId || !allowDiscard()) return;
    setProjectId(id);
    setRuleUuid("");
    setMapping(null);
    setRepository(null);
    setPicker(null);
    setNotice(null);
  }

  function changeRule(id: string) {
    if (id === ruleUuid || !allowDiscard()) return;
    setRuleUuid(id);
    setMapping(null);
    setPicker(null);
    setNotice(null);
  }

  function openPicker(side: Side | "") {
    if (!ready || !hasRepository || saveLock.current) return;
    setPicker({ side, selected: side ? [...mapping.draft[side]] : [] });
    setSearch("");
    setNotice(null);
  }

  function changePickerSide(side: Side | "") {
    if (!picker || !ready || side === picker.side) return;
    if (!picker.side && side) {
      setPicker({ side, selected: [...new Set([...mapping.draft[side], ...picker.selected])] });
      return;
    }
    if (pickerDirty && !window.confirm("Discard the current file picker selection and change mapping side?")) return;
    setPicker({ side, selected: side ? [...mapping.draft[side]] : [] });
  }

  function addSelectedFiles() {
    if (!picker?.side || !ready) return;
    const side = picker.side;
    setMapping({ ...mapping, draft: { ...mapping.draft, [side]: [...new Set(picker.selected)].sort() } });
    setPicker(null);
    setNotice(null);
  }

  function removeFile(side: Side, path: string) {
    if (!ready || saveLock.current) return;
    setMapping({ ...mapping, draft: { ...mapping.draft, [side]: mapping.draft[side].filter((item) => item !== path) } });
    setNotice(null);
  }

  async function save() {
    if (!ready || saveLock.current || picker) return;
    saveLock.current = true;
    setSaving(true);
    setNotice(null);
    try {
      const result = await rulesApi.saveImplementationMappings(projectId, ruleUuid, mapping.draft);
      if (!mounted.current) return;
      const files = { sourceFiles: result.sourceFiles, targetFiles: result.targetFiles };
      setMapping({ key, status: "ready", saved: files, draft: files, error: "" });
      setNotice({ text: `Implementation mappings for ${rule.ruleId} saved successfully.`, success: true });
    } catch (error) {
      if (mounted.current) setNotice({ text: errorMessage(error), success: false });
    } finally {
      saveLock.current = false;
      if (mounted.current) setSaving(false);
    }
  }

  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      if (picker) setPicker(null);
      else close();
    }
    if (event.key === "Tab") {
      const elements = dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href], [tabindex="0"]');
      if (!elements?.length) return;
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) { event.preventDefault(); first.focus(); }
    }
  }

  const repositoryReady = repository?.projectId === projectId && repository.status === "ready";
  const visibleFiles = repositoryReady ? repository.data?.files.filter((path) => path.toLowerCase().includes(search.toLowerCase())) ?? [] : [];

  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) close(); }}>
    <div className="project-modal adr-modal record-form-modal implementation-mappings-modal" role="dialog" aria-modal="true" aria-labelledby="implementation-mappings-title" ref={dialog} tabIndex={-1} onKeyDown={keyDown}>
      <div className="modal-header"><h2 id="implementation-mappings-title">Implementation File Mappings</h2><button className="modal-close" type="button" disabled={saving} onClick={close} aria-label="Close implementation mappings">x</button></div>
      <div className="adr-form">
        <div className="adr-form-body" aria-busy={saving}>
          {notice && <p className={`form-notice ${notice.success ? "success-notice" : "error-notice"}`} role={notice.success ? "status" : "alert"}>{notice.text}</p>}
          {picker ? <>
            <div className="mapping-picker-heading"><h3>Select Repository Files</h3><p>{repositoryReady ? repository.data?.repositoryUrl : project?.repositoryUrl}{repositoryReady && repository.data?.branch && <span> · Branch: {repository.data.branch}</span>}</p></div>
            <label>Add files to<select value={picker.side} onChange={(event) => changePickerSide(event.target.value === "sourceFiles" ? "sourceFiles" : event.target.value === "targetFiles" ? "targetFiles" : "")}><option value="">Select mapping side</option><option value="sourceFiles">Source Implementation Files</option><option value="targetFiles">Target Implementation Files</option></select></label>
            <label>Search repository files<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Filter by file name or path..." /></label>
            <p className="mapping-hint" role="status">{picker.selected.length} files selected{repositoryReady ? ` · ${visibleFiles.length} files shown` : ""}</p>
            {repository?.status === "error" ? <div className="form-notice error-notice" role="alert">{repository.error}<button type="button" onClick={() => setRepositoryRetry((value) => value + 1)}>Retry browsing</button></div> : !repositoryReady ? <p className="mapping-hint" role="status"><span className="loading-spinner" /> Loading repository files...</p> : <div className="repository-file-list">
              {!visibleFiles.length && <p className="mapping-hint">{repository.data?.files.length ? "No files match your search." : "No files were returned for this repository."}</p>}
              {visibleFiles.map((path) => <label className="repository-file-option" key={path}><input type="checkbox" checked={picker.selected.includes(path)} onChange={(event) => setPicker({ ...picker, selected: event.target.checked ? [...new Set([...picker.selected, path])] : picker.selected.filter((item) => item !== path) })} /><span>{path}</span></label>)}
            </div>}
            {!!picker.selected.length && <details className="mapping-selected-files"><summary>Selected paths ({picker.selected.length})</summary><ul>{picker.selected.map((path) => <li key={path}>{path}</li>)}</ul></details>}
          </> : <>
            <div className="adr-form-grid">
              {projects.length === 1 ? <label>Architecture Project<input readOnly value={project?.name ?? ""} /></label> : <label>Architecture Project<select value={projectId} disabled={saving} onChange={(event) => changeProject(event.target.value)}><option value="">Select project</option>{projects.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>}
              <label>Architecture Rule<select value={ruleUuid} disabled={saving || !projectId || ruleList.projectId !== projectId || ruleList.status !== "ready"} onChange={(event) => changeRule(event.target.value)}><option value="">Select rule</option>{rules.map((item) => <option key={item.id} value={item.id}>{item.ruleId} — {item.sourceElement} → {item.targetElement}</option>)}</select></label>
            </div>
            {!projectId ? <p className="mapping-hint">Select an architecture project to see its rules.</p> : ruleList.status === "error" ? <div className="form-notice error-notice" role="alert">{ruleList.error}<button type="button" onClick={() => setRetry((value) => value + 1)}>Retry rules</button></div> : ruleList.projectId !== projectId || ruleList.status === "loading" ? <p className="mapping-hint" role="status">Loading architecture rules...</p> : !rules.length && <p className="mapping-hint">This project has no architecture rules. Create a rule before adding implementation mappings.</p>}
            {project && !hasRepository && <p className="form-notice error-notice">This project does not have a GitHub repository URL configured. <a href="/rules/projects" target="_blank" rel="noopener noreferrer">Open Architecture Projects</a> and choose Edit for {project.name} to configure it.</p>}
            {rule && <>
              <dl className="mapping-rule-summary">
                <div><dt>Rule ID</dt><dd>{rule.ruleId}</dd></div>{rule.adrDisplayId && <div><dt>Linked ADR</dt><dd>{rule.adrDisplayId}</dd></div>}
                <div><dt>Source Element</dt><dd>{rule.sourceElement}</dd></div><div><dt>Source Element Type</dt><dd>{rule.sourceElementType}</dd></div>
                <div><dt>Relationship Type</dt><dd>{rule.relationshipType}</dd></div><div><dt>Target Element</dt><dd>{rule.targetElement}</dd></div>
                <div><dt>Target Element Type</dt><dd>{rule.targetElementType}</dd></div><div><dt>Decision</dt><dd>{rule.decision}</dd></div><div><dt>Severity</dt><dd>{rule.severity}</dd></div>
              </dl>
              {!ready && (mapping?.status === "error" ? <div className="form-notice error-notice" role="alert">{mapping.error}<button type="button" onClick={() => setRetry((value) => value + 1)}>Retry mappings</button></div> : <p className="mapping-hint" role="status">Loading saved implementation mappings...</p>)}
              {ready && <>
                {hasRepository && <div className="mapping-repository"><p className="mapping-hint">Repository: {project?.repositoryUrl}</p><button className="secondary-button" type="button" disabled={saving} onClick={() => openPicker("")}>Browse Repository</button></div>}
                {(["sourceFiles", "targetFiles"] as const).map((side) => <section className="mapping-side" key={side} aria-labelledby={`mapping-${side}-title`}>
                  <div className="mapping-side-heading"><div><h3 id={`mapping-${side}-title`}>{side === "sourceFiles" ? "Source" : "Target"} Implementation Files</h3><p>{side === "sourceFiles" ? "Source" : "Target"} Element: {side === "sourceFiles" ? rule.sourceElement : rule.targetElement}</p></div><button className="secondary-button" type="button" disabled={!hasRepository || saving} onClick={() => openPicker(side)}>Browse {side === "sourceFiles" ? "Source" : "Target"} Files</button></div>
                  {!mapping.draft[side].length ? <p className="mapping-hint">No {side === "sourceFiles" ? "source" : "target"} files selected.</p> : <ul className="mapping-path-list">{mapping.draft[side].map((path) => <li key={path}><span>{path}</span><button className="secondary-button" type="button" disabled={saving} onClick={() => removeFile(side, path)} aria-label={`Remove ${side === "sourceFiles" ? "source" : "target"} file ${path}`}>Remove</button></li>)}</ul>}
                </section>)}
                {dirty && <p className="mapping-hint" role="status">You have unsaved mapping changes.</p>}
              </>}
            </>}
          </>}
        </div>
        <div className="modal-actions">
          {picker ? <><button className="secondary-button" type="button" onClick={() => setPicker(null)}>Cancel</button><button className="primary-button" type="button" disabled={!repositoryReady || !picker.side || !picker.selected.length} onClick={addSelectedFiles}>Add Selected Files</button></> : <><button className="secondary-button" type="button" disabled={saving} onClick={close}>Close</button><button className="primary-button" type="button" disabled={!ready || saving || !dirty} onClick={() => void save()}>{saving ? "Saving..." : "Save Mappings"}</button></>}
        </div>
      </div>
    </div>
  </div>;
}
