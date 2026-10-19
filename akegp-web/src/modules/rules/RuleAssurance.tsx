import { useEffect, useRef, useState } from "react";
import { ADR, AdrRuleFidelity, ApiClientError, ArchitectReviewDecision, ArchitectReviewHistoryEntry, ArchitectReviewWorkspace, ArchitectureRule, ImplementationMappings, StoredStructuralValidation, adrsApi, rulesApi } from "../../shared/api/client";

function errorMessage(error: unknown) {
  if (error instanceof ApiClientError) return Object.values(error.fieldErrors).join(" ") || error.message;
  return error instanceof Error ? error.message : "The request could not be completed.";
}

function timestamp(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function StoredValidationResult({ projectId, ruleUuid, paused, refresh }: { projectId: string; ruleUuid: string; paused: boolean; refresh: number }) {
  const [result, setResult] = useState<StoredStructuralValidation | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setResult(null);
    setError("");
    if (paused) return;
    void rulesApi.storedStructuralValidation(projectId, ruleUuid).then((loaded) => {
      if (active) setResult(loaded);
    }).catch((reason: unknown) => { if (active) setError(errorMessage(reason)); });
    return () => { active = false; };
  }, [projectId, ruleUuid, paused, refresh, retry]);

  if (paused) return <p className="validation-placeholder" role="status">Refreshing saved rule evidence...</p>;
  if (error) return <div className="form-notice error-notice" role="alert">Unable to load persisted structural validation: {error} <button type="button" onClick={() => setRetry((value) => value + 1)}>Retry validation result</button></div>;
  if (!result) return <p className="validation-placeholder" role="status">Loading persisted structural validation...</p>;
  return <div className={`validation-result ${result.status === "SUPPORTED" ? "validation-supported" : "validation-unsupported"}`}>
    <h4>Structural Validation: <span className="assurance-status">{result.status}</span></h4>
    <p>{result.reason}</p>
    <p className="validation-hint">Structural support identifies a recognized rule shape.</p>
    <dl>
      <dt>Current evidence</dt><dd>{result.current ? "Current" : "Not current"}</dd>
      {result.architectureStyle && <><dt>Validated architecture style</dt><dd>{result.architectureStyle}</dd></>}
      {result.validatedAt && <><dt>Last validated</dt><dd>{timestamp(result.validatedAt)}</dd></>}
      {result.matchedSignatureId && <><dt>Matched signature</dt><dd>{result.matchedSignatureId}</dd></>}
      {result.catalogueVersion && <><dt>Catalogue version</dt><dd>{result.catalogueVersion}</dd></>}
      {!result.current && result.lastValidationStatus && <><dt>Historical result</dt><dd>{result.lastValidationStatus} (requires revalidation)</dd></>}
    </dl>
  </div>;
}

export function AdrFidelityResult({ projectId, ruleUuid, paused, refresh }: { projectId: string; ruleUuid: string; paused: boolean; refresh: number }) {
  const [result, setResult] = useState<AdrRuleFidelity | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setResult(null);
    setError("");
    if (paused) return;
    void rulesApi.adrFidelity(projectId, ruleUuid).then((loaded) => {
      if (active) setResult(loaded);
    }).catch((reason: unknown) => { if (active) setError(errorMessage(reason)); });
    return () => { active = false; };
  }, [projectId, ruleUuid, paused, refresh, retry]);

  if (paused) return <p className="validation-placeholder" role="status">Refreshing ADR–rule fidelity...</p>;
  if (error) return <div className="form-notice error-notice" role="alert">Unable to load ADR–rule fidelity: {error} <button type="button" onClick={() => setRetry((value) => value + 1)}>Retry fidelity result</button></div>;
  if (!result) return <p className="validation-placeholder" role="status">Loading ADR–rule fidelity...</p>;
  const tone = result.status === "MATCH" ? "validation-supported" : result.status === "MISMATCH" ? "validation-mismatch" : "validation-unsupported";
  return <div className={`validation-result fidelity-result ${tone}`}>
    <h4>ADR–Rule Fidelity: <span className={`assurance-status fidelity-status-${result.status.toLowerCase()}`}>{result.status}</span></h4>
    <p>{result.explanation}</p>
    <p className="validation-hint">PP1 recognizes the explicit pattern &quot;&lt;Source&gt; must not directly access &lt;Target&gt;&quot;, including sources joined by &quot;and&quot; or commas and separate constraint sentences. Each linked rule is evaluated independently. This is not general natural-language understanding.</p>
    <dl>
      {result.adrDisplayId && <><dt>Linked ADR</dt><dd>{result.adrDisplayId}</dd></>}
      {!result.adrUuid && <><dt>Linked ADR</dt><dd>Unavailable</dd></>}
    </dl>
  </div>;
}

export function ArchitectReviewPanel({ projectId, rule, paused, refresh, onEvidenceChanged, onReviewSaved }: {
  projectId: string;
  rule: ArchitectureRule;
  paused: boolean;
  refresh: number;
  onEvidenceChanged: () => void;
  onReviewSaved?: () => void;
}) {
  const [workspace, setWorkspace] = useState<ArchitectReviewWorkspace | null>(null);
  const [history, setHistory] = useState<ArchitectReviewHistoryEntry[] | null>(null);
  const [adr, setADR] = useState<ADR | null>(null);
  const [evidenceRule, setEvidenceRule] = useState<ArchitectureRule | null>(null);
  const [mappings, setMappings] = useState<ImplementationMappings | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [reviewer, setReviewer] = useState("");
  const [comments, setComments] = useState("");
  const [submitting, setSubmitting] = useState<ArchitectReviewDecision | null>(null);
  const [notice, setNotice] = useState<{ text: string; success: boolean } | null>(null);
  const submitLock = useRef(false);
  const operationVersion = useRef(0);

  useEffect(() => () => { operationVersion.current += 1; }, []);

  useEffect(() => {
    let active = true;
    setWorkspace(null);
    setHistory(null);
    setADR(null);
    setEvidenceRule(null);
    setMappings(null);
    setErrors([]);
    setLoading(false);
    if (paused || submitting) return;
    setLoading(true);
    const reviewRequest = rulesApi.architectReview(projectId, rule.id);
    // Read detailed evidence after the fingerprint snapshot. A later change will be rejected by POST.
    void Promise.allSettled([
      reviewRequest,
      rulesApi.architectReviewHistory(projectId, rule.id),
      reviewRequest.then((snapshot) => adrsApi.get(projectId, snapshot.adrUuid)),
      reviewRequest.then(() => rulesApi.implementationMappings(projectId, rule.id)),
      reviewRequest.then(() => rulesApi.get(projectId, rule.id)),
    ]).then(([reviewResult, historyResult, adrResult, mappingsResult, ruleResult]) => {
      if (!active) return;
      const failures: string[] = [];
      if (reviewResult.status === "fulfilled") setWorkspace(reviewResult.value);
      else failures.push(`Review workspace: ${errorMessage(reviewResult.reason)}`);
      if (historyResult.status === "fulfilled") setHistory([...historyResult.value].sort((a, b) => Date.parse(b.review.decidedAt) - Date.parse(a.review.decidedAt)));
      else failures.push(`Review history: ${errorMessage(historyResult.reason)}`);
      if (adrResult.status === "fulfilled") setADR(adrResult.value);
      else failures.push(`Linked ADR: ${errorMessage(adrResult.reason)}`);
      if (mappingsResult.status === "fulfilled") setMappings(mappingsResult.value);
      else failures.push(`Implementation mappings: ${errorMessage(mappingsResult.reason)}`);
      if (ruleResult.status === "fulfilled") setEvidenceRule(ruleResult.value);
      else failures.push(`Saved rule: ${errorMessage(ruleResult.reason)}`);
      setErrors(failures);
      setLoading(false);
    });
    return () => { active = false; };
  }, [projectId, rule.id, rule.adrId, paused, refresh, reload, submitting]);

  const identityValid = !!reviewer.trim() && reviewer.trim().length <= 150 && comments.length <= 4000;
  const available = !!workspace && !loading && !paused && !submitting;

  async function submit(decision: ArchitectReviewDecision) {
    if (!available || !identityValid || submitLock.current) return;
    if (decision === "CONFIRMED" && (!workspace.confirmationEligible || !workspace.evidenceFingerprint || errors.length > 0)) return;
    if (decision === "CHANGES_REQUESTED" && comments.trim().length < 10) return;
    // Submit the fingerprint the architect actually saw; never substitute a newer one silently.
    const snapshot = workspace;
    const version = ++operationVersion.current;
    submitLock.current = true;
    setSubmitting(decision);
    setNotice(null);
    setWorkspace(null);
    try {
      const result = await rulesApi.submitArchitectReview(projectId, rule.id, {
        decision,
        reviewer: reviewer.trim(),
        comments: comments.trim(),
        expectedEvidenceFingerprint: snapshot.evidenceFingerprint,
      });
      if (version !== operationVersion.current) return;
      setWorkspace(result);
      setNotice({ text: `${decision === "CONFIRMED" ? "Confirmation" : "Change request"} recorded for ${result.ruleId}.`, success: true });
      setComments("");
      onReviewSaved?.();
    } catch (reason) {
      if (version !== operationVersion.current) return;
      const stale = reason instanceof ApiClientError && reason.status === 409;
      setNotice({ text: stale ? `${errorMessage(reason)} Review the current evidence again before submitting a new decision.` : errorMessage(reason), success: false });
      if (stale) onEvidenceChanged();
    } finally {
      if (version === operationVersion.current) {
        submitLock.current = false;
        setSubmitting(null);
        // Reload effective status and history after every attempt, including ambiguous network failures.
        setReload((value) => value + 1);
      }
    }
  }

  return <section className="validation-panel architect-review-panel" aria-labelledby="architect-review-title" aria-busy={paused || loading || !!submitting}>
    <div className="architect-review-heading"><div><h3 id="architect-review-title">Architect Confirmation</h3><p className="validation-hint">Review the saved interpretation and evidence for {rule.ruleId}, then record your decision.</p></div><button className="secondary-button" type="button" disabled={paused || !!submitting || loading} onClick={() => { setReload((value) => value + 1); onEvidenceChanged(); }}>Refresh Evidence</button></div>
    {notice && <p className={`form-notice ${notice.success ? "success-notice" : "error-notice"}`} role={notice.success ? "status" : "alert"}>{notice.text}</p>}
    {errors.length > 0 && <div className="form-notice error-notice" role="alert"><ul>{errors.map((error) => <li key={error}>{error}</li>)}</ul><button type="button" onClick={() => setReload((value) => value + 1)}>Retry review evidence</button></div>}
    {loading || paused || submitting ? <p className="validation-hint" role="status">{submitting ? "Recording architect decision..." : "Loading current review evidence..."}</p> : workspace && <>
      <dl className="architect-readiness">
        <div><dt>Structural Validation</dt><dd className="assurance-status">{workspace.structuralValidationStatus}</dd></div>
        <div><dt>Architect Review (effective)</dt><dd className={`assurance-status review-status-${workspace.reviewStatus.toLowerCase()}`}>{workspace.reviewStatus}</dd></div>
      </dl>
      {workspace.latestReview && !workspace.latestReview.current && <p className="form-notice review-stale-notice" role="status">The previous {workspace.latestReview.review.decision} decision applies to older evidence. This rule requires a new review. The historical decision remains in Review History.</p>}
      <p className="validation-hint">Linked ADR: {workspace.adrDisplayId ?? "Unavailable"}{workspace.adrVersion != null && ` · Version ${workspace.adrVersion}`} · Source files: {workspace.sourceFileCount} · Target files: {workspace.targetFileCount}</p>
      <details className="architect-evidence"><summary>Saved rule, ADR and implementation evidence</summary>
        {evidenceRule && <dl><dt>Rule interpretation</dt><dd>{evidenceRule.sourceElement} ({evidenceRule.sourceElementType}) → {evidenceRule.relationshipType} → {evidenceRule.targetElement} ({evidenceRule.targetElementType})</dd><dt>Decision / Severity</dt><dd>{evidenceRule.decision} / {evidenceRule.severity}</dd><dt>Description</dt><dd>{evidenceRule.description}</dd></dl>}
        {adr && <><h4>{adr.adrId} · {adr.title} · Version {adr.version} · {adr.status}</h4><dl><dt>Context</dt><dd>{adr.context}</dd><dt>Decision</dt><dd>{adr.decision}</dd><dt>Consequences</dt><dd>{adr.consequences}</dd></dl></>}
        {mappings && (["sourceFiles", "targetFiles"] as const).map((side) => <div key={side}><h4>{side === "sourceFiles" ? "Source" : "Target"} Implementation Files</h4>{mappings[side].length ? <ul>{mappings[side].map((path) => <li key={path}>{path}</li>)}</ul> : <p className="validation-hint">No saved files.</p>}</div>)}
        <p className="validation-hint">Evidence revision: {workspace.evidenceRevision}</p><dl><dt>Evidence fingerprint</dt><dd className="evidence-fingerprint">{workspace.evidenceFingerprint}</dd></dl>
      </details>
      {!workspace.confirmationEligible && <div className="review-blocked" role="status"><strong>Confirmation is blocked</strong><ul>{workspace.blockedReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul></div>}
    </>}
    <form className="architect-review-form" onSubmit={(event) => event.preventDefault()}>
      <label>Reviewer *<input name="reviewer" required maxLength={150} value={reviewer} disabled={!available} onChange={(event) => setReviewer(event.target.value)} placeholder="Your name or reviewer identifier" /></label>
      <label>Review Comments<textarea name="reviewComments" maxLength={4000} value={comments} disabled={!available} onChange={(event) => setComments(event.target.value)} placeholder="Explain your decision or the changes required..." /></label>
      <p className="validation-hint">Reviewer identity is self-declared and is not authenticated in PP1. Change requests require meaningful comments of at least 10 characters.</p>
      <div className="validation-actions"><button className="primary-button" type="button" disabled={!available || !identityValid || !workspace?.confirmationEligible || !workspace.evidenceFingerprint || errors.length > 0} onClick={() => void submit("CONFIRMED")}>{submitting === "CONFIRMED" ? "Confirming..." : "Confirm Rule"}</button><button className="secondary-button" type="button" disabled={!available || !identityValid || comments.trim().length < 10} onClick={() => void submit("CHANGES_REQUESTED")}>{submitting === "CHANGES_REQUESTED" ? "Recording..." : "Request Changes"}</button></div>
      {available && !identityValid && <p className="validation-hint">Enter a reviewer name to submit a decision.</p>}
    </form>
    <section className="architect-review-history" aria-labelledby="architect-history-title"><h4 id="architect-history-title">Review History</h4>
      {history === null ? <p className="validation-hint">{paused || submitting ? "Review history will reload after the active operation." : loading ? "Loading review history..." : "Review history is unavailable. Retry loading the evidence."}</p> : !history.length ? <p className="validation-hint">No architect reviews have been recorded.</p> : <div className="review-history-table-wrap"><table className="projects-table"><caption className="visually-hidden">Architect review history, newest first</caption><thead><tr><th>Decision</th><th>Reviewer</th><th>Comments</th><th>Timestamp</th><th>Evidence</th></tr></thead><tbody>{history.map((entry) => <tr key={entry.review.id}><td>{entry.review.decision}</td><td>{entry.review.reviewer}</td><td>{entry.review.comments || "No comments"}</td><td><time dateTime={entry.review.decidedAt}>{timestamp(entry.review.decidedAt)}</time></td><td>{entry.current ? "Current" : "Historical / stale"}</td></tr>)}</tbody></table></div>}
    </section>
  </section>;
}

type RegisterStatus = { status: "ready"; workspace: ArchitectReviewWorkspace } | { status: "error"; message: string };

export function ArchitectConfirmationRegister({ projectId, rules, selectedRuleId, paused, refresh, onSelectRule, onRefresh }: {
  projectId: string;
  rules: ArchitectureRule[];
  selectedRuleId: string;
  paused: boolean;
  refresh: number;
  onSelectRule: (ruleUuid: string) => void;
  onRefresh?: () => void;
}) {
  const [retry, setRetry] = useState(0);
  const [snapshot, setSnapshot] = useState<{ key: string; rows: Record<string, RegisterStatus> } | null>(null);
  // Primitive dependencies keep selection changes and new array instances from restarting the requests.
  const ruleIds = JSON.stringify(rules.map((rule) => rule.id));
  const key = JSON.stringify([projectId, ruleIds, refresh, retry]);

  useEffect(() => {
    let active = true;
    setSnapshot({ key, rows: {} });
    if (paused) return;
    const ids = rules.map((rule) => rule.id);
    let next = 0;
    async function worker() {
      while (active && next < ids.length) {
        const id = ids[next++];
        let row: RegisterStatus;
        try {
          row = { status: "ready", workspace: await rulesApi.architectReview(projectId, id) };
        } catch (reason) {
          row = { status: "error", message: errorMessage(reason) };
        }
        if (!active) return;
        setSnapshot((current) => current?.key === key ? { key, rows: { ...current.rows, [id]: row } } : current);
      }
    }
    void Promise.all(Array.from({ length: Math.min(4, ids.length) }, () => worker()));
    return () => { active = false; };
  }, [projectId, ruleIds, key, paused]);

  const rows = !paused && snapshot?.key === key ? snapshot.rows : {};
  const loading = paused || Object.keys(rows).length < rules.length;
  const failed = Object.values(rows).some((row) => row.status === "error");

  return <section className="validation-panel architect-confirmation-register" aria-labelledby="confirmation-register-title" aria-busy={loading}>
    <div className="architect-review-heading"><div><h3 id="confirmation-register-title">Architect Confirmation Register</h3><p className="validation-hint">Current effective statuses for this project's saved rules. Select a Rule ID to view its details above.</p></div><button className="secondary-button" type="button" disabled={loading || !rules.length} onClick={() => { setRetry((value) => value + 1); onRefresh?.(); }}>{failed ? "Retry Statuses" : "Refresh Register"}</button></div>
    {!rules.length ? <p className="validation-hint">No saved rules are available for this project.</p> : <>
      {loading && <p className="validation-hint" role="status">{paused ? "Refreshing saved rules..." : `Loading current statuses: ${Object.keys(rows).length} of ${rules.length} requests completed.`}</p>}
      {failed && <p className="form-notice error-notice" role="alert">Some rule statuses are unavailable. Retry to load their current status.</p>}
      <div className="review-history-table-wrap"><table className="projects-table"><caption className="visually-hidden">Saved rule structural validation and effective architect review statuses</caption><thead><tr><th>Rule ID</th><th>Rule Summary</th><th>Structural Validation</th><th>Architect Review</th></tr></thead><tbody>{rules.map((rule) => {
        const row = rows[rule.id];
        return <tr key={rule.id} data-selected={rule.id === selectedRuleId}>
          <td><button className="register-rule-link" type="button" disabled={paused} aria-pressed={rule.id === selectedRuleId} onClick={() => onSelectRule(rule.id)}>{rule.ruleId}</button></td>
          <td><span>{rule.sourceElement} → {rule.relationshipType} → {rule.targetElement}</span><small>{rule.description}</small>{row?.status === "error" && <small className="register-row-error">{row.message}</small>}</td>
          <td>{row?.status === "ready" ? <span className={`assurance-status structural-status-${row.workspace.structuralValidationStatus.toLowerCase()}`}>{row.workspace.structuralValidationStatus}</span> : row?.status === "error" ? "Unavailable" : "Loading..."}</td>
          <td>{row?.status === "ready" ? <span className={`assurance-status review-status-${row.workspace.reviewStatus.toLowerCase()}`}>{row.workspace.reviewStatus}</span> : row?.status === "error" ? "Unavailable" : "Loading..."}</td>
        </tr>;
      })}</tbody></table></div>
    </>}
  </section>;
}
