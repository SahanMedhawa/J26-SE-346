import { FormEvent, KeyboardEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  ADR,
  ArchitectureElementType,
  ArchitectureRule,
  ArchitectureRuleInput,
  ApiClientError,
  Project,
  RelationshipType,
  RuleDecision,
  RuleSeverity,
  adrsApi,
  projectsApi,
  rulesApi,
} from "../../shared/api/client";
import { useSelectedProject } from "./rulesWorkspace";

const elementTypes: ArchitectureElementType[] = ["SERVICE", "DATABASE", "CONTROLLER", "SERVICE_LAYER", "REPOSITORY", "MESSAGE_BROKER", "API_GATEWAY", "EXTERNAL_SYSTEM"];
const relationships: RelationshipType[] = ["CALLS", "DEPENDS_ON", "DIRECT_DATABASE_ACCESS", "DATA_ACCESS", "PUBLISHES_TO", "CONSUMES_FROM", "USES_API"];
const decisions: RuleDecision[] = ["ALLOWED", "FORBIDDEN", "REQUIRED"];
const severities: RuleSeverity[] = ["HIGH", "MEDIUM", "LOW"];

type Modal = "create" | "edit" | "details" | null;

export function ArchitectureRulesPage() {
  const { selectedProject: workspaceProject, setSelectedProject } = useSelectedProject();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const initialProjectId = useRef(params.get("projectId") ?? workspaceProject?.id);
  const editRuleUuid = params.get("editRule");
  const returnToValidation = params.get("returnTo") === "validation";
  const openedEdit = useRef<string | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectId, setProjectId] = useState("");
  const [adrs, setAdrs] = useState<ADR[]>([]);
  const [rules, setRules] = useState<ArchitectureRule[]>([]);
  const [search, setSearch] = useState("");
  const [decisionFilter, setDecisionFilter] = useState<RuleDecision | "all">("all");
  const [severityFilter, setSeverityFilter] = useState<RuleSeverity | "all">("all");
  const [loadState, setLoadState] = useState<"loading" | "ready" | "error">("loading");
  const [notice, setNotice] = useState<{ text: string; success: boolean } | null>(null);
  const [modal, setModal] = useState<Modal>(null);
  const [selectedRule, setSelectedRule] = useState<ArchitectureRule | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<ArchitectureRule | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    void projectsApi.list().then((loaded) => {
      if (!active) return;
      setProjects(loaded);
      if (loaded.length) {
        const project = loaded.find((item) => item.id === initialProjectId.current) ?? loaded[0];
        setProjectId(project.id);
        setSelectedProject(project);
      }
    }).catch((error) => setNotice({ text: getRuleError(error), success: false }));
    return () => { active = false; };
  }, [setSelectedProject]);

  useEffect(() => {
    let active = true;
    if (!projectId) {
      setRules([]);
      setAdrs([]);
      setLoadState("ready");
      return;
    }
    const project = projects.find((item) => item.id === projectId);
    if (project) setSelectedProject(project);
    setLoadState("loading");
    void Promise.all([rulesApi.list(projectId), adrsApi.list(projectId)]).then(([loadedRules, loadedAdrs]) => {
      if (!active) return;
      setRules(loadedRules);
      setAdrs(loadedAdrs);
      setLoadState("ready");
    }).catch((error) => {
      if (!active) return;
      setLoadState("error");
      setNotice({ text: getRuleError(error), success: false });
    });
    return () => { active = false; };
  }, [projectId, projects, setSelectedProject]);

  useEffect(() => {
    if (!editRuleUuid || !projectId || projectId !== params.get("projectId") || loadState !== "ready" || openedEdit.current === editRuleUuid) return;
    let active = true;
    void rulesApi.get(projectId, editRuleUuid).then((rule) => {
      if (!active) return;
      openedEdit.current = editRuleUuid;
      setSelectedRule(rule);
      setModal("edit");
    }).catch((error) => {
      if (!active) return;
      openedEdit.current = editRuleUuid;
      setNotice({ text: getRuleError(error), success: false });
    });
    return () => { active = false; };
  }, [editRuleUuid, loadState, params, projectId]);

  const selectedProject = projects.find((project) => project.id === projectId);
  const visibleRules = useMemo(() => {
    const term = search.trim().toLowerCase();
    return rules.filter((rule) => {
      const matchesSearch = !term || [rule.ruleId, rule.sourceElement, rule.targetElement, rule.description].some((value) => value.toLowerCase().includes(term));
      return matchesSearch && (decisionFilter === "all" || rule.decision === decisionFilter) && (severityFilter === "all" || rule.severity === severityFilter);
    });
  }, [decisionFilter, rules, search, severityFilter]);

  function changeProject(id: string) {
    setProjectId(id);
    setSelectedRule(null);
    setModal(null);
    setNotice(null);
  }

  async function openDetails(rule: ArchitectureRule) {
    setSelectedRule(rule);
    setModal("details");
    try {
      setSelectedRule(await rulesApi.get(rule.projectId, rule.id));
    } catch (error) {
      setModal(null);
      setNotice({ text: getRuleError(error), success: false });
    }
  }

  async function reloadRules() {
    if (!projectId) return;
    setRules(await rulesApi.list(projectId));
    setLoadState("ready");
  }

  async function submitRule(event: FormEvent<HTMLFormElement>, editing: boolean) {
    event.preventDefault();
    if (!projectId) {
      setNotice({ text: "Select an architecture project first.", success: false });
      return;
    }
    const form = new FormData(event.currentTarget);
    const input: ArchitectureRuleInput = {
      adrId: String(form.get("adrId") ?? ""),
      sourceElement: String(form.get("sourceElement") ?? "").trim(),
      sourceElementType: String(form.get("sourceElementType") ?? "") as ArchitectureElementType,
      relationshipType: String(form.get("relationshipType") ?? "") as RelationshipType,
      targetElement: String(form.get("targetElement") ?? "").trim(),
      targetElementType: String(form.get("targetElementType") ?? "") as ArchitectureElementType,
      decision: String(form.get("decision") ?? "") as RuleDecision,
      description: String(form.get("description") ?? "").trim(),
      severity: String(form.get("severity") ?? "") as RuleSeverity,
    };
    if (!input.adrId) {
      setNotice({ text: "Select an ADR to link to this rule.", success: false });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      if (editing && selectedRule) {
        await rulesApi.update(selectedRule.projectId, selectedRule.id, input);
        setNotice({ text: "Architecture rule updated successfully.", success: true });
      } else {
        const created = await rulesApi.create(projectId, input);
        setNotice({ text: `Architecture Rule ${created.ruleId} created successfully.`, success: true });
      }
      setModal(null);
      await reloadRules();
      if (editing && selectedRule && returnToValidation) {
        navigate(`/rules/validation?${new URLSearchParams({ projectId: selectedRule.projectId, rule: selectedRule.id })}`);
      }
    } catch (error) {
      setNotice({ text: getRuleError(error), success: false });
    } finally {
      setBusy(false);
    }
  }

  async function deleteRule() {
    if (!confirmDelete) return;
    setBusy(true);
    try {
      await rulesApi.remove(confirmDelete.projectId, confirmDelete.id);
      setConfirmDelete(null);
      setNotice({ text: "Architecture rule deleted successfully.", success: true });
      await reloadRules();
    } catch (error) {
      setNotice({ text: getRuleError(error), success: false });
    } finally {
      setBusy(false);
    }
  }

  return <section className="rules-content architecture-rules-content">
    <div className="projects-intro">
      <div><p className="content-kicker">Architecture decisions and constraints</p><h2>Architecture Rule Engineering</h2><p className="projects-description">Manage architecture rules linked to the decisions that govern each project.</p></div>
      <button className="primary-button" type="button" disabled={!selectedProject || selectedProject.status !== "ACTIVE" || !adrs.length} onClick={() => { setSelectedRule(null); setModal("create"); }}><span aria-hidden="true">+</span> Create Rule</button>
    </div>
    <div className="rule-project-bar"><span>Architecture project</span><DarkSelect ariaLabel="Select architecture project" value={projectId} onChange={changeProject} options={[{ value: "", label: "Select project" }, ...projects.map((project) => ({ value: project.id, label: `${project.name}${project.status === "INACTIVE" ? " (Inactive)" : ""}` }))]} /></div>
    <div className="rule-controls">
      <label className="search-field"><span className="search-icon" aria-hidden="true">/</span><span className="visually-hidden">Search architecture rules</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search Rule ID, source, target, or description..." /></label>
      <DarkSelect ariaLabel="Filter rules by decision" value={decisionFilter} onChange={(value) => setDecisionFilter(value as RuleDecision | "all")} options={[{ value: "all", label: "All decisions" }, ...decisions.map((value) => ({ value, label: titleCase(value) }))]} />
      <DarkSelect ariaLabel="Filter rules by severity" value={severityFilter} onChange={(value) => setSeverityFilter(value as RuleSeverity | "all")} options={[{ value: "all", label: "All severities" }, ...severities.map((value) => ({ value, label: titleCase(value) }))]} />
    </div>
    {notice && <p className={`form-notice ${notice.success ? "success-notice" : "error-notice"}`} role="alert">{notice.text}</p>}
    <div className="projects-table-wrap architecture-rules-table-wrap"><table className="projects-table architecture-rules-table"><caption className="visually-hidden">Architecture rules</caption><thead><tr><th>Rule ID</th><th>Linked ADR</th><th>Source Element</th><th>Relationship</th><th>Target Element</th><th>Decision</th><th>Severity</th><th>Actions</th></tr></thead><tbody>
      {loadState === "loading" ? <tr><td colSpan={8}><div className="table-empty"><span className="loading-spinner" /><h3>Loading architecture rules...</h3></div></td></tr> : loadState === "error" ? <tr><td colSpan={8}><div className="table-empty"><h3>Unable to load architecture rules</h3><p>Check the API connection and try again.</p></div></td></tr> : !projectId ? <tr><td colSpan={8}><div className="table-empty"><h3>Select a project</h3><p>Choose a registered project to view its architecture rules.</p></div></td></tr> : !visibleRules.length ? <tr><td colSpan={8}><div className="table-empty"><h3>{rules.length ? "No matching rules" : "No architecture rules yet"}</h3><p>{rules.length ? "Adjust your search or filters." : adrs.length ? "Create the first rule for this project." : "Create an ADR before linking an architecture rule."}</p></div></td></tr> : visibleRules.map((rule) => <tr key={rule.id}>
        <td><strong className="rule-id">{rule.ruleId}</strong></td><td>{rule.adrDisplayId || "—"}</td><td>{rule.sourceElement}</td><td>{titleCase(rule.relationshipType)}</td><td>{rule.targetElement}</td><td><span className={`rule-badge decision-${rule.decision.toLowerCase()}`}>{titleCase(rule.decision)}</span></td><td><span className={`rule-badge severity-${rule.severity.toLowerCase()}`}>{titleCase(rule.severity)}</span></td><td><div className="row-actions adr-actions"><button className="details-action" type="button" onClick={() => void openDetails(rule)}>Open</button><button className="edit-action" type="button" onClick={() => { setSelectedRule(rule); setModal("edit"); }}><RuleEditIcon /> Edit</button><button className="danger-button" type="button" onClick={() => setConfirmDelete(rule)}><RuleTrashIcon /> Delete</button></div></td>
      </tr>)}
    </tbody></table></div>
    {modal === "create" && <RuleModal title="Create Architecture Rule" onClose={() => setModal(null)}><RuleForm projects={adrs} rule={undefined} busy={busy} onCancel={() => setModal(null)} onSubmit={(event) => void submitRule(event, false)} /></RuleModal>}
    {modal === "edit" && selectedRule && <RuleModal title="Edit Architecture Rule" identifier={selectedRule.ruleId} onClose={() => setModal(null)}><RuleForm projects={adrs} rule={selectedRule} busy={busy} onCancel={() => setModal(null)} onSubmit={(event) => void submitRule(event, true)} /></RuleModal>}
    {modal === "details" && selectedRule && <RuleDetails rule={selectedRule} project={selectedProject} adrs={adrs} onClose={() => setModal(null)} onEdit={() => setModal("edit")} />}
    {confirmDelete && <div className="modal-backdrop"><div className="project-modal confirmation-modal" role="alertdialog" aria-modal="true"><div className="modal-header"><h2>Delete architecture rule?</h2><button className="modal-close" type="button" onClick={() => setConfirmDelete(null)} aria-label="Close confirmation">x</button></div><div className="confirmation-content"><p>Are you sure you want to delete architecture rule <strong>{confirmDelete.ruleId}</strong>? This action cannot be undone.</p></div><div className="modal-actions"><button className="secondary-button" type="button" onClick={() => setConfirmDelete(null)}>Cancel</button><button className="danger-button" type="button" disabled={busy} onClick={() => void deleteRule()}>{busy ? "Deleting..." : "Delete Rule"}</button></div></div></div>}
  </section>;
}

function RuleForm({ rule, projects, busy, onCancel, onSubmit }: { rule?: ArchitectureRule; projects: ADR[]; busy: boolean; onCancel: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  const [adrId, setAdrId] = useState(rule?.adrId ?? "");
  return <form className="adr-form rule-form" onSubmit={onSubmit}><div className="adr-form-body"><div className="rule-form-grid">
    <label className="record-form-full-width">Linked ADR *<DarkSelect ariaLabel="Select linked ADR" value={adrId} options={[{ value: "", label: "Select ADR" }, ...projects.map((adr) => ({ value: adr.id, label: `${adr.adrId} — ${adr.title}` }))]} onChange={setAdrId} /><input className="rule-hidden-select" required name="adrId" value={adrId} readOnly /></label>
    <label>Source Element *<input required name="sourceElement" defaultValue={rule?.sourceElement} placeholder="OrderService" /></label>
    <SelectField name="sourceElementType" label="Source Element Type" value={rule?.sourceElementType ?? ""} options={elementTypes} />
    <SelectField name="relationshipType" label="Relationship Type" value={rule?.relationshipType ?? ""} options={relationships} />
    <label>Target Element *<input required name="targetElement" defaultValue={rule?.targetElement} placeholder="PaymentDB" /></label>
    <SelectField name="targetElementType" label="Target Element Type" value={rule?.targetElementType ?? ""} options={elementTypes} />
    <SelectField name="decision" label="Decision" value={rule?.decision ?? ""} options={decisions} />
    <SelectField name="severity" label="Severity" value={rule?.severity ?? ""} options={severities} />
    <label className="rule-description-field">Description *<textarea required name="description" maxLength={5000} defaultValue={rule?.description} placeholder="Describe the architectural constraint..." /></label>
  </div></div><div className="modal-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" type="submit" disabled={busy}>{busy ? "Saving..." : "Save Rule"}</button></div></form>;
}

function SelectField({ name, label, value, options }: { name: string; label: string; value: string; options: string[] }) {
  const [selected, setSelected] = useState(value);
  return <label>{label} *<DarkSelect ariaLabel={`Select ${label}`} value={selected} onChange={setSelected} options={[{ value: "", label: `Select ${label.toLowerCase()}` }, ...options.map((option) => ({ value: option, label: titleCase(option) }))]} /><input className="rule-hidden-select" required name={name} value={selected} readOnly /></label>;
}

function RuleDetails({ rule, project, adrs, onClose, onEdit }: { rule: ArchitectureRule; project?: Project; adrs: ADR[]; onClose: () => void; onEdit: () => void }) {
  const adr = adrs.find((item) => item.id === rule.adrId);
  return <div className="modal-backdrop"><div className="project-modal rule-details-modal" role="dialog" aria-modal="true"><div className="modal-header"><h2>Rule Details · {rule.ruleId}</h2><button className="modal-close" type="button" onClick={onClose} aria-label="Close details">x</button></div><div className="adr-details-body"><div className="rule-detail-heading"><h3>{rule.ruleId}</h3><div><span className={`rule-badge decision-${rule.decision.toLowerCase()}`}>{titleCase(rule.decision)}</span><span className={`rule-badge severity-${rule.severity.toLowerCase()}`}>{titleCase(rule.severity)}</span></div></div><dl><dt>Architecture Project</dt><dd>{project?.name ?? rule.projectId}</dd><dt>Linked ADR</dt><dd>{rule.adrDisplayId} {adr ? `— ${adr.title}` : ""}</dd><dt>Source Element</dt><dd>{rule.sourceElement} · {titleCase(rule.sourceElementType)}</dd><dt>Relationship Type</dt><dd>{titleCase(rule.relationshipType)}</dd><dt>Target Element</dt><dd>{rule.targetElement} · {titleCase(rule.targetElementType)}</dd><dt>Description</dt><dd>{rule.description}</dd><dt>Created</dt><dd>{formatDateTime(rule.createdAt)}</dd><dt>Updated</dt><dd>{formatDateTime(rule.updatedAt)}</dd></dl></div><div className="modal-actions"><button className="secondary-button" type="button" onClick={onClose}>Cancel</button><button className="primary-button" type="button" onClick={onEdit}>Edit Rule</button></div></div></div>;
}

export function DarkSelect({ ariaLabel, value, options, onChange }: { ariaLabel: string; value: string; options: { value: string; label: string }[]; onChange: (value: string) => void }) {
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(Math.max(0, options.findIndex((option) => option.value === value)));
  const ref = useRef<HTMLDivElement>(null);
  const selected = options.find((option) => option.value === value) ?? options[0];
  useEffect(() => {
    const close = (event: MouseEvent) => { if (!ref.current?.contains(event.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);
  function keyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === "Escape") setOpen(false);
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const next = event.key === "ArrowDown" ? Math.min(highlighted + 1, options.length - 1) : Math.max(highlighted - 1, 0);
      setHighlighted(next);
      setOpen(true);
    }
    if ((event.key === "Enter" || event.key === " ") && open) {
      event.preventDefault();
      onChange(options[highlighted].value);
      setOpen(false);
    } else if ((event.key === "Enter" || event.key === " ") && !open) {
      event.preventDefault();
      setOpen(true);
    }
  }
  return <div className="dark-select" ref={ref}><button className="dark-select-trigger" type="button" aria-label={ariaLabel} aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen(!open)} onKeyDown={keyDown}><span>{selected?.label ?? "Select..."}</span><span className="dark-select-chevron" aria-hidden="true">⌄</span></button>{open && <div className="dark-select-menu" role="listbox" aria-label={ariaLabel}>{options.map((option, index) => <button className={`dark-select-option${option.value === value ? " selected" : ""}${index === highlighted ? " highlighted" : ""}`} key={option.value || "empty"} type="button" role="option" aria-selected={option.value === value} onMouseEnter={() => setHighlighted(index)} onClick={() => { onChange(option.value); setOpen(false); }}>{option.label}</button>)}</div>}</div>;
}

function RuleModal({ title, identifier, onClose, children }: { title: string; identifier?: string; onClose: () => void; children: ReactNode }) {
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><div className="project-modal adr-modal rule-modal record-form-modal" role="dialog" aria-modal="true"><div className="modal-header"><h2 className="record-modal-title">{title}{identifier && <span className="record-id-badge">{identifier}</span>}</h2><button className="modal-close" type="button" onClick={onClose} aria-label="Close modal">x</button></div>{children}</div></div>;
}

function titleCase(value: string) {
  return value.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function getRuleError(error: unknown) {
  if (error instanceof ApiClientError) {
    const fields = Object.values(error.fieldErrors);
    return fields.length ? fields.join(" ") : error.message;
  }
  return error instanceof Error ? error.message : "The request could not be completed.";
}

function RuleEditIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16-.75 4.75L8 20l11.5-11.5a2.12 2.12 0 0 0-3-3z" /><path d="m14.5 7.5 2 2" /></svg>;
}

function RuleTrashIcon() {
  return <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14M10 11v6M14 11v6M8 7l1-3h6l1 3M7 7l1 14h8l1-14" /></svg>;
}
