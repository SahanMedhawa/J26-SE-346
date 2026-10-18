import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ApiClientError, ArchitectureRule, Project, ValidationArchitectureStyle, projectsApi, rulesApi } from "../../shared/api/client";
import { DarkSelect } from "./ArchitectureRulesPage";
import { useSelectedProject } from "./rulesWorkspace";
import { ArchitectConfirmationRegister, ArchitectReviewPanel, StoredValidationResult } from "./RuleAssurance";

export function RuleValidationPage() {
  const { selectedProject, setSelectedProject } = useSelectedProject();
  const [params] = useSearchParams();
  const initialProjectId = useRef(params.get("projectId") ?? selectedProject?.id);
  const [projects, setProjects] = useState<Project[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    setState("loading");
    setError("");
    void projectsApi.list().then((loaded) => {
      if (!active) return;
      setProjects(loaded);
      const project = loaded.find((item) => item.id === initialProjectId.current) ?? loaded[0];
      if (project) setSelectedProject(project);
      setState("ready");
    }).catch((reason) => {
      if (!active) return;
      setError(errorMessage(reason));
      setState("error");
    });
    return () => { active = false; };
  }, [reload, setSelectedProject]);

  const project = projects.find((item) => item.id === selectedProject?.id);
  return <section className="rules-content rule-validation-content">
    <nav className="validation-breadcrumb" aria-label="Breadcrumb"><Link to="/rules">Component 01</Link><span aria-hidden="true">/</span><span aria-current="page">Rule Validation</span></nav>
    <div className="projects-intro"><div><p className="content-kicker">Architecture rule structure</p><h2>Rule Validation</h2><p className="projects-description">Validate existing architecture rule definitions against supported architectural structures.</p></div></div>
    <div className="validation-project-select">
      <span id="validation-project-label">Architecture Project</span>
      <fieldset disabled={state !== "ready" || !projects.length} aria-labelledby="validation-project-label">
        <DarkSelect ariaLabel="Architecture Project" value={project?.id ?? ""} options={projects.length ? projects.map((item) => ({ value: item.id, label: `${item.name}${item.status === "INACTIVE" ? " (Inactive)" : ""}` })) : [{ value: "", label: state === "loading" ? "Loading projects..." : "No projects available" }]} onChange={(id) => {
          const next = projects.find((item) => item.id === id);
          if (next) { initialProjectId.current = id; setSelectedProject(next); }
        }} />
      </fieldset>
    </div>
    {state === "loading" && <p className="validation-hint" role="status">Loading architecture projects...</p>}
    {state === "error" && <div className="form-notice error-notice" role="alert">Unable to load architecture projects: {error} <button type="button" onClick={() => setReload((value) => value + 1)}>Retry</button></div>}
    {state === "ready" && !projects.length && <p className="validation-hint">No architecture projects are registered. <Link to="/rules/projects">Register a project</Link> to get started.</p>}
    {state === "ready" && project && <ProjectValidation key={`${project.id}:${project.architectureStyles.join(",")}`} project={project} initialRuleId={params.get("projectId") === project.id ? params.get("rule") ?? "" : ""} />}
  </section>;
}

function ProjectValidation({ project, initialRuleId }: { project: Project; initialRuleId: string }) {
  const navigate = useNavigate();
  const [rules, setRules] = useState<ArchitectureRule[]>([]);
  const [ruleId, setRuleId] = useState(initialRuleId);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [reload, setReload] = useState(0);
  const [chosenStyle, setChosenStyle] = useState<ValidationArchitectureStyle | "">("");
  const [busy, setBusy] = useState(false);
  const [assuranceRefresh, setAssuranceRefresh] = useState(0);
  const [detailRefresh, setDetailRefresh] = useState(0);
  const [validationError, setValidationError] = useState("");
  const requestVersion = useRef(0);
  const submitting = useRef(false);
  const ruleSelectors = useRef<HTMLFieldSetElement>(null);
  const styles = [...new Set(project.architectureStyles.flatMap((style): ValidationArchitectureStyle[] => style === "MICROSERVICES_AND_LAYERED" ? ["MICROSERVICES", "LAYERED"] : [style]))];
  const style = styles.length === 1 ? styles[0] : chosenStyle;
  const rule = rules.find((item) => item.id === ruleId);

  function clearResult() {
    requestVersion.current += 1;
    submitting.current = false;
    setBusy(false);
    setValidationError("");
  }

  function refreshRules() {
    // Pause dependent reads in the same update as the explicit refresh, before the rules effect runs.
    setLoading(true);
    setReload((value) => value + 1);
  }

  useEffect(() => {
    let active = true;
    async function loadRules() {
      const version = ++requestVersion.current;
      submitting.current = false;
      setBusy(false);
      setValidationError("");
      setLoading(true);
      setLoadError("");
      try {
        const loaded = await rulesApi.list(project.id);
        if (!active || version !== requestVersion.current) return;
        setRules(loaded);
        setRuleId((current) => loaded.some((item) => item.id === current) ? current : "");
      } catch (reason) {
        if (!active || version !== requestVersion.current) return;
        setLoadError(errorMessage(reason));
      } finally {
        if (active && version === requestVersion.current) setLoading(false);
      }
    }
    void loadRules();
    return () => { active = false; requestVersion.current += 1; };
  }, [project.id, reload]);

  async function runValidation() {
    if (submitting.current) return;
    setValidationError("");
    if (!project.id || !rule) { setValidationError("Select an architecture project and rule first."); return; }
    if (!style || !styles.includes(style)) { setValidationError("Select an architecture style registered for this project."); return; }
    const version = ++requestVersion.current;
    submitting.current = true;
    setBusy(true);
    try {
      // Reload saved content, including edits made before returning to this page.
      const latest = await rulesApi.get(project.id, rule.id);
      if (version !== requestVersion.current) return;
      setRules((current) => current.map((item) => item.id === latest.id ? latest : item));
      const response = await rulesApi.validateStructure(project.id, rule.id, { architectureStyle: style });
      if (version !== requestVersion.current) return;
      if (!response || (response.validationStatus !== "SUPPORTED" && response.validationStatus !== "UNSUPPORTED")) {
        throw new Error("The backend returned an unrecognized validation result. Please try again.");
      }
      setAssuranceRefresh((value) => value + 1);
    } catch (reason) {
      if (version === requestVersion.current) setValidationError(errorMessage(reason));
    } finally {
      if (version === requestVersion.current) { submitting.current = false; setBusy(false); }
    }
  }

  return <>
    <fieldset className="validation-selectors" disabled={loading || busy} ref={ruleSelectors} tabIndex={-1}>
      <div><span id="validation-rule-label">Architecture Rule</span><DarkSelect ariaLabel="Architecture Rule" value={ruleId} onChange={(id) => { clearResult(); setRuleId(id); }} options={[{ value: "", label: loading ? "Loading architecture rules..." : "Select architecture rule" }, ...rules.map((item) => ({ value: item.id, label: `${item.ruleId} — ${item.sourceElement} → ${item.targetElement}` }))]} /></div>
      {styles.length > 1 ? <div><span>Architecture Style</span><DarkSelect ariaLabel="Architecture Style" value={chosenStyle} onChange={(value) => { clearResult(); setChosenStyle(value as ValidationArchitectureStyle | ""); }} options={[{ value: "", label: "Select architecture style" }, ...styles.map((value) => ({ value, label: styleLabel(value) }))]} /></div> : <div><span>Architecture Style</span><p className="validation-style">{style ? styleLabel(style) : "No applicable architecture style registered"}</p></div>}
    </fieldset>
    {loading && <p className="validation-hint" role="status">Loading architecture rules...</p>}
    {loadError && <div className="form-notice error-notice" role="alert">Unable to load architecture rules: {loadError} <button type="button" onClick={refreshRules}>Retry</button></div>}
    {!loading && !loadError && !rules.length && <p className="validation-hint">This project has no saved architecture rules. <Link to="/rules/architecture-rules">Create an architecture rule</Link> before running validation.</p>}
    {styles.length > 1 && !style && <p className="validation-hint">This project supports multiple styles. Choose a style before running validation.</p>}
    <div className="validation-panels">
      <section className="validation-panel" aria-labelledby="selected-rule-title">
        <h3 id="selected-rule-title">Selected Rule Structure</h3>
        {rule && !loading ? <>
          <p className="validation-rule-id">{rule.ruleId}</p>
          <div className="validation-structure">
            <div className="validation-element"><span>Source Element</span><strong>{rule.sourceElement}</strong><small>{rule.sourceElementType}</small></div>
            <span className="validation-arrow" aria-hidden="true">→</span>
            <div className="validation-element validation-relationship"><span>Relationship</span><strong>{relationshipLabel(rule.relationshipType)}</strong></div>
            <span className="validation-arrow" aria-hidden="true">→</span>
            <div className="validation-element"><span>Target Element</span><strong>{rule.targetElement}</strong><small>{rule.targetElementType}</small></div>
          </div>
        </> : <p className="validation-placeholder">Select an architecture rule to view its saved structure.</p>}
        <div className="validation-actions">
          <button className="primary-button" type="button" disabled={!rule || !style || loading || busy || !!loadError} onClick={() => void runValidation()}>{busy ? <span className="loading-spinner" aria-hidden="true" /> : <svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m8 4 12 8-12 8z" /></svg>}{busy ? "Validating..." : "Run Validation"}</button>
          <button className="secondary-button" type="button" disabled={!rule || loading || busy} onClick={() => {
            if (rule) navigate(`/rules/architecture-rules?${new URLSearchParams({ projectId: project.id, editRule: rule.id, returnTo: "validation" })}`);
          }}><svg className="button-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16-.75 4.75L8 20l11.5-11.5a2.12 2.12 0 0 0-3-3zM14.5 7.5l2 2" /></svg>Edit Rule</button>
        </div>
      </section>
      <section className="validation-panel validation-results" aria-labelledby="validation-results-title" aria-busy={busy}>
        <h3 id="validation-results-title">Validation Results</h3>
        <div aria-live="polite" aria-atomic="true">
          {validationError && <div className="form-notice error-notice" role="alert"><strong>Unable to validate rule structure</strong><p>{validationError}</p></div>}
          {rule ? <StoredValidationResult key={rule.id} projectId={project.id} ruleUuid={rule.id} paused={busy || loading} refresh={assuranceRefresh + detailRefresh} /> : <p className="validation-placeholder">Select an architecture rule to view its persisted validation result.</p>}
        </div>
      </section>
    </div>
    {rule && <ArchitectReviewPanel key={rule.id} projectId={project.id} rule={rule} paused={busy || loading} refresh={assuranceRefresh + detailRefresh} onEvidenceChanged={refreshRules} onReviewSaved={() => setAssuranceRefresh((value) => value + 1)} />}
    <ArchitectConfirmationRegister projectId={project.id} rules={rules} selectedRuleId={ruleId} paused={busy || loading} refresh={assuranceRefresh} onRefresh={refreshRules} onSelectRule={(id) => {
      clearResult();
      setRuleId(id);
      setDetailRefresh((value) => value + 1);
      ruleSelectors.current?.focus({ preventScroll: true });
      ruleSelectors.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }} />
  </>;
}

function styleLabel(style: ValidationArchitectureStyle) {
  return style === "MICROSERVICES" ? "Microservices" : "Layered";
}

function relationshipLabel(value: ArchitectureRule["relationshipType"]) {
  return value.toLowerCase().split("_").map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
}

function errorMessage(error: unknown) {
  if (error instanceof ApiClientError) return Object.values(error.fieldErrors).join(" ") || error.message;
  return error instanceof Error ? error.message : "The request could not be completed.";
}
