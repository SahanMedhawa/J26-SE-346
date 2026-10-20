import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Project, projectsApi } from "../../shared/api/client";
import { useEnforcementStats } from "./Useenforcementstats";
import { fetchHistory, submitDecision, type DecisionOutcome, type RunHistoryItem } from "./Enforcementapi";
import type { DecisionAction, DecisionView, RunDetail, RunSummary } from "./Enforcementapi";
import styles from "./EnforcementPage.module.css";

type Tone = "ok" | "bad" | "warn" | "muted";

const RESULT_TONE: Record<string, Tone> = {
  Passed: "ok",
  Skipped: "muted",
  "Violation found": "bad",
  Held: "warn",
  "Exception approved": "ok",
  "Changes requested": "warn",
  Rejected: "bad",
  "In progress": "muted",
};

function toneOf(result: string): Tone { return RESULT_TONE[result] ?? "muted"; }

function ago(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  return `${Math.floor(seconds / 86400)} d ago`;
}

function Stat({ label, value, hint, tone }: { label: string; value: number | undefined; hint: string; tone?: "warn" | "bad" }) {
  const hot = tone !== undefined && (value ?? 0) > 0;
  return (
    <div className={`${styles.stat} ${hot ? styles[tone === "bad" ? "statBad" : "statWarn"] : ""}`}>
      <div className={styles.statValue}>{value ?? "–"}</div>
      <div className={styles.statLabel}>{label}</div>
      <div className={styles.statHint}>{hint}</div>
    </div>
  );
}

function ResultPill({ result }: { result: string }) {
  return <span className={`${styles.pill} ${styles[`pill_${toneOf(result)}`]}`}>{result}</span>;
}

const ACTION_LABEL: Record<string, string> = {
  approve_exception: "Approved with exception",
  request_changes: "Changes requested",
  reject: "Rejected",
  apply_fix: "Fix re-scanned",
};

const JUSTIFICATION_LABEL: Record<string, string> = {
  "rule-not-applicable-in-context": "Rule not applicable in this context",
  "known-technical-constraint": "Known technical constraint",
  "temporary-with-follow-up-ticket": "Temporary, with a follow-up ticket",
  "rule-needs-revision": "The rule needs revision",
};

const REVIEWER_KEY = "akegp.enforcement.reviewer";

function savedReviewer(): string {
  try { return window.localStorage.getItem(REVIEWER_KEY) ?? ""; } catch { return ""; }
}

function DecisionSummary({ decision }: { decision: DecisionView }) {
  return (
    <div className={styles.decisionDone}>
      <div className={styles.decisionTitle}>Decision recorded</div>
      <p className={styles.decisionLine}>
        <strong>{ACTION_LABEL[decision.action] ?? decision.action}</strong> by {decision.reviewer} · {new Date(decision.at).toLocaleString()}
      </p>
      {decision.justification && (
        <p className={styles.decisionLine}>Justification: {JUSTIFICATION_LABEL[decision.justification] ?? decision.justification}</p>
      )}
      {decision.comment && <p className={styles.decisionLine}>Comment: {decision.comment}</p>}
    </div>
  );
}

type Notice = { ok: boolean; text: string };
interface DecisionHandlers {
  onStart: () => void;
  onDecided: (outcome: DecisionOutcome | null) => void;
}

function DecisionForm({ detail, result, onStart, onDecided }: { detail: RunDetail; result: Notice | null } & DecisionHandlers) {
  const [action, setAction] = useState<DecisionAction>("approve_exception");
  const [reviewer, setReviewer] = useState(savedReviewer);
  const [justification, setJustification] = useState("");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const needsJustification = action === "approve_exception";
  const ready = reviewer.trim() !== "" && (!needsJustification || justification !== "");
  const idPrefix = `decision-${detail.runId}`;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!ready || busy) return;
    setBusy(true);
    setError(null);
    onStart();
    try {
      const outcome = await submitDecision(detail.repo, detail.prNumber, {
        action,
        reviewer: reviewer.trim(),
        justification: needsJustification ? justification : undefined,
        comment: comment.trim() === "" ? undefined : comment.trim(),
      });
      try { window.localStorage.setItem(REVIEWER_KEY, reviewer.trim()); } catch { /* storage unavailable: fine */ }
      onDecided(action === "apply_fix" ? outcome : null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The decision was not saved.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className={styles.decisionForm} onSubmit={submit}>
      <div className={styles.decisionTitle}>Reviewer decision</div>
      <p className={styles.decisionHint}>
        This pull request is held. Only a reviewer can release or fail it.
        {action === "apply_fix" && " Re-scan checks the latest commit on GitHub, so push your fix to the pull request first."}
      </p>
      <div className={styles.decisionGrid}>
        <label className={styles.field} htmlFor={`${idPrefix}-action`}>
          <span>Decision</span>
          <select id={`${idPrefix}-action`} value={action} onChange={(e) => setAction(e.target.value as DecisionAction)}>
            <option value="approve_exception">Approve with exception</option>
            <option value="request_changes">Request changes</option>
            <option value="reject">Reject</option>
            <option value="apply_fix">I pushed a fix, re-scan it</option>
          </select>
        </label>
        <label className={styles.field} htmlFor={`${idPrefix}-reviewer`}>
          <span>Reviewer</span>
          <input id={`${idPrefix}-reviewer`} value={reviewer} maxLength={80} onChange={(e) => setReviewer(e.target.value)} placeholder="Your name" />
        </label>
        {needsJustification && (
          <label className={`${styles.field} ${styles.wide}`} htmlFor={`${idPrefix}-justification`}>
            <span>Justification (required for an exception)</span>
            <select id={`${idPrefix}-justification`} value={justification} onChange={(e) => setJustification(e.target.value)}>
              <option value="">Choose a reason…</option>
              {Object.entries(JUSTIFICATION_LABEL).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
        )}
        <label className={`${styles.field} ${styles.wide}`} htmlFor={`${idPrefix}-comment`}>
          <span>Comment (optional)</span>
          <textarea id={`${idPrefix}-comment`} rows={2} maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} />
        </label>
      </div>
      <div className={styles.decisionBar}>
        <button type="submit" className={styles.decisionButton} disabled={!ready || busy}>
          {busy ? "Saving…" : "Record decision"}
        </button>
        {error && <span className={styles.decisionError} role="alert">{error}</span>}
        {result && <span className={result.ok ? styles.decisionOk : styles.decisionError} role="status">{result.text}</span>}
      </div>
    </form>
  );
}

/** What is needed to explain one scan; both the latest run (RunDetail) and an earlier scan (RunHistoryItem) have it. */
type Explainable = {
  classification: string;
  state: string;
  holdReason: string | null;
  files: number;
  changedFiles: string[];
  violations: { ruleId: string }[];
  decision: DecisionView | null;
};

function fileList(scan: Explainable): string {
  const names = scan.changedFiles.map((path) => path.split("/").pop() ?? path);
  const shown = names.slice(0, 3).join(", ");
  const more = scan.files - Math.min(names.length, 3);
  return more > 0 ? `${shown} and ${more} more` : shown;
}

/** One plain sentence saying why a scan ended the way it did. */
function reasonOf(scan: Explainable): string {
  if (scan.files === 0) return "No files are changed in this pull request, so there is nothing to scan and it passes.";
  if (scan.classification === "Documentation") return `Only documentation changed (${fileList(scan)}). That cannot break an architecture rule, so it passes without a scan.`;
  if (scan.classification === "Configuration") return `Only configuration changed (${fileList(scan)}). That is not architecture-checked, so it passes without a scan.`;
  const code = `Source code changed (${fileList(scan)}).`;
  const n = scan.violations.length;
  const rules = [...new Set(scan.violations.map((v) => v.ruleId))].join(", ");
  const found = `The scan found ${n} violation${n === 1 ? "" : "s"} (${rules})`;
  const who = scan.decision?.reviewer ?? "A reviewer";
  switch (scan.state) {
    case "passed": return `${code} The architecture scan found no violations, so it passes.`;
    case "approved_exception": {
      const why = scan.decision?.justification ? JUSTIFICATION_LABEL[scan.decision.justification] ?? scan.decision.justification : "no reason given";
      return `${code} ${found}. ${who} approved an exception (${why}), so it can merge.`;
    }
    case "changes_requested": return `${code} ${found}. ${who} requested changes, so the merge stays blocked.`;
    case "rejected": return `${code} ${found}. ${who} rejected it, so it cannot merge.`;
    case "held":
      if (scan.holdReason === "project-not-linked") return `${code} The repository is not linked to a project, so it cannot be checked and stays held.`;
      if (scan.holdReason === "scan-unavailable") return `${code} The architecture scan could not run, so it stays held rather than passing unchecked.`;
      return `${code} ${found}, so it is held and cannot merge until a reviewer decides or a fix is pushed.`;
    default: return `${code} The scan is still running.`;
  }
}

const PROBLEM_RESULTS = ["Violation found", "Held", "Rejected", "Changes requested", "Exception approved"];

/** How the pull request got from its earlier scans to the latest one (only when an earlier scan had a problem). */
function historyNote(latest: RunSummary, older: RunSummary[]): string | null {
  const flagged = older.filter((run) => PROBLEM_RESULTS.includes(run.result)).length;
  if (flagged === 0) return null;
  const earlier = flagged === 1 ? "An earlier scan of this pull request found a problem." : `${flagged} earlier scans of this pull request found problems.`;
  let outcome: string;
  if (PROBLEM_RESULTS.includes(latest.result)) outcome = "The latest commit still has problems.";
  else if (latest.files === 0) outcome = "The latest commit removed those changes, so the pull request no longer changes anything and passes.";
  else if (latest.result === "Passed") outcome = "The latest commit fixed it: the new scan found no violations.";
  else outcome = "The latest commit no longer changes source code, so it is not scanned and passes.";
  return `${earlier} ${outcome} The earlier scans are listed at the bottom of this panel.`;
}

type FixResult = Notice & { runId: number };

function fixResult(outcome: DecisionOutcome | null): FixResult | null {
  const notice = fixNotice(outcome);
  return notice && outcome ? { ...notice, runId: outcome.runId } : null;
}

function fixNotice(outcome: DecisionOutcome | null): Notice | null {
  if (!outcome) return null;
  const commit = outcome.commitSha.slice(0, 7);
  if (outcome.state === "passed") return { ok: true, text: `Fix verified on commit ${commit}: no violations. The pull request can merge.` };
  const n = outcome.violations.length;
  return { ok: false, text: `The fix did not work on commit ${commit}: ${n} violation${n === 1 ? "" : "s"} remain. The pull request stays held.` };
}


/** The result label of an earlier scan (the same words the summary list uses). */
function labelOf(scan: RunHistoryItem): string {
  if (scan.files === 0 || scan.classification !== "Source code") return "Skipped";
  switch (scan.state) {
    case "passed": return "Passed";
    case "approved_exception": return "Exception approved";
    case "changes_requested": return "Changes requested";
    case "rejected": return "Rejected";
    case "held": return scan.holdReason === "violations" ? "Violation found" : "Held";
    default: return "In progress";
  }
}

/** One short line for the collapsed card. */
function briefOf(scan: Explainable): string {
  if (scan.files === 0) return "No changes, so nothing to scan";
  if (scan.classification === "Documentation") return `Documentation only (${fileList(scan)}), no scan needed`;
  if (scan.classification === "Configuration") return `Configuration only (${fileList(scan)}), no scan needed`;
  const n = scan.violations.length;
  const rules = [...new Set(scan.violations.map((v) => v.ruleId))].join(", ");
  const count = `${n} violation${n === 1 ? "" : "s"} (${rules})`;
  const who = scan.decision?.reviewer ?? "a reviewer";
  switch (scan.state) {
    case "passed": return `${fileList(scan)}: scanned, no violations`;
    case "approved_exception": return `${count}, exception approved by ${who}`;
    case "changes_requested": return `${count}, changes requested by ${who}`;
    case "rejected": return `${count}, rejected by ${who}`;
    case "held":
      if (scan.holdReason === "project-not-linked") return "Repository is not linked to a project, so it stays held";
      if (scan.holdReason === "scan-unavailable") return "The scan could not run, so it stays held";
      return `${count} in ${fileList(scan)}, needs a reviewer`;
    default: return "Scan in progress";
  }
}

function ViolationList({ violations }: { violations: RunDetail["violations"] }) {
  return (
    <ul className={styles.violations}>
      {violations.map((v, index) => (
        <li key={`${v.ruleId}-${v.file}-${v.line}-${index}`} className={styles.violationItem}>
          <div className={styles.violationTop}>
            <span className={styles.ruleId}>{v.ruleId}</span>
            <span className={styles.severity}>{v.severity}</span>
            <code className={styles.location}>{v.line > 0 ? `${v.file}:${v.line}` : v.file}</code>
          </div>
          <p className={styles.message}>{v.message}</p>
        </li>
      ))}
    </ul>
  );
}

function History({ run, count }: { run: RunSummary; count: number }) {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<RunHistoryItem[] | "error" | null>(null);

  function toggle() {
    const opening = !open;
    setOpen(opening);
    if (opening && items === null) {
      fetchHistory(run.repo, run.pr).then(setItems).catch(() => setItems("error"));
    }
  }

  const earlier = Array.isArray(items) ? items.filter((item) => item.runId !== run.runId) : [];
  return (
    <section className={styles.block}>
      <button type="button" className={styles.linkButton} aria-expanded={open} onClick={toggle}>
        {open ? "Hide" : "Show"} {count} earlier scan{count === 1 ? "" : "s"} of this pull request
      </button>
      {open && items === null && <p className={styles.muted}>Loading…</p>}
      {open && items === "error" && <p className={styles.muted}>The scan history could not be loaded.</p>}
      {open && earlier.length > 0 && (
        <ol className={styles.timeline}>
          {earlier.map((item) => (
            <li key={item.runId} className={styles.timelineItem}>
              <div className={styles.timelineTop}>
                <ResultPill result={labelOf(item)} />
                <span className={styles.sha}>commit {item.commitSha.slice(0, 7)}</span>
                <span className={styles.when} title={new Date(item.at).toLocaleString()}>{ago(item.at)}</span>
              </div>
              <p className={styles.reason}>{reasonOf(item)}</p>
              {item.violations.length > 0 && <ViolationList violations={item.violations} />}
              {item.decision && <DecisionSummary decision={item.decision} />}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function PullRequestCard({ run, detail, older, result, expanded, onToggle, onStart, onDecided }: {
  run: RunSummary;
  detail: RunDetail | undefined;
  older: RunSummary[];
  result: FixResult | null;
  expanded: boolean;
  onToggle: () => void;
} & DecisionHandlers) {
  const tone = toneOf(run.result);
  const formVisible = expanded && detail !== undefined && detail.state === "held";
  const brief = detail ? briefOf(detail) : `${run.changeType}, ${run.files} file${run.files === 1 ? "" : "s"}`;
  const note = historyNote(run, older);
  return (
    <article className={`${styles.card} ${styles[`card_${tone}`]}`}>
      <button type="button" className={styles.rowButton} aria-expanded={expanded} disabled={!detail} onClick={onToggle}>
        <span className={styles.rowMain}>
          <span className={styles.rowTitle}>
            <span className={styles.prNumber}>PR #{run.pr}</span>
            <ResultPill result={run.result} />
          </span>
          <span className={styles.brief}>{brief}</span>
        </span>
        <span className={styles.rowMeta}>
          {older.length > 0 && <span className={styles.tag}>{older.length} earlier scan{older.length === 1 ? "" : "s"}</span>}
          <span className={styles.when} title={new Date(run.at).toLocaleString()}>{ago(run.at)}</span>
        </span>
        {detail && <span className={styles.chevron} aria-hidden="true">{expanded ? "▾" : "▸"}</span>}
      </button>

      {result && !formVisible && (
        <div className={`${styles.notice} ${result.ok ? styles.noticeOk : styles.noticeBad}`} role="status">{result.text}</div>
      )}

      {expanded && detail && (
        <div className={styles.panel}>
          <section className={styles.block}>
            <h3 className={styles.blockTitle}>What happened</h3>
            <p className={styles.reason}>{reasonOf(detail)}</p>
            {note && <p className={styles.muted}>{note}</p>}
            <p className={styles.facts}>
              <span title={detail.repo}>{detail.repo}</span> · commit <span className={styles.sha}>{detail.commitSha.slice(0, 7)}</span> · {detail.files} file{detail.files === 1 ? "" : "s"} changed
            </p>
          </section>

          {detail.violations.length > 0 && (
            <section className={styles.block}>
              <h3 className={styles.blockTitle}>Violations found</h3>
              <ViolationList violations={detail.violations} />
            </section>
          )}

          {detail.decision && (
            <section className={styles.block}>
              <DecisionSummary decision={detail.decision} />
            </section>
          )}

          {detail.state === "held" && (
            <section className={styles.block}>
              <DecisionForm detail={detail} result={result} onStart={onStart} onDecided={onDecided} />
            </section>
          )}

          {older.length > 0 && <History run={run} count={older.length} />}
        </div>
      )}
    </article>
  );
}

export function EnforcementPage() {
  const { projectId } = useParams();
  const [project, setProject] = useState<Project | null>(null);
  const [projectError, setProjectError] = useState("");
  useEffect(() => {
    let active = true; setProject(null); setProjectError("");
    if (projectId) void projectsApi.get(projectId).then(p => { if (active) setProject(p); })
      .catch(e => { if (active) setProjectError(e.message); });
    return () => { active = false; };
  }, [projectId]);
  const repo = project ? new URL(project.repositoryUrl).pathname.replace(/^\//, "").replace(/\/$/, "").replace(/\.git$/, "") : undefined;
  const { stats, details, failed, updatedAt, refresh } = useEnforcementStats(repo, !projectId || !!project);
  const [filter, setFilter] = useState("All");
  const [open, setOpen] = useState<Set<string>>(new Set());
  const [fixResults, setFixResults] = useState<Record<string, FixResult>>({});

  function setResult(key: string, notice: FixResult | null) {
    setFixResults((current) => {
      const next = { ...current };
      if (notice) next[key] = notice;
      else delete next[key];
      return next;
    });
  }

  function toggle(key: string) {
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  const recent = stats?.recent ?? [];
  // One card per pull request (its newest scan); older scans of the same PR sit inside its panel.
  const { latest, earlier } = useMemo(() => {
    const first = new Map<string, RunSummary>();
    const older = new Map<string, RunSummary[]>();
    for (const run of recent) {
      const key = `${run.repo}#${run.pr}`;
      if (!first.has(key)) first.set(key, run);
      else older.set(key, [...(older.get(key) ?? []), run]);
    }
    return { latest: [...first.values()], earlier: older };
  }, [recent]);
  const results = useMemo(() => ["All", ...new Set(latest.map((run) => run.result))], [latest]);
  const shown = filter === "All" ? latest : latest.filter((run) => run.result === filter);
  const needsReview = latest.filter((run) => run.result === "Violation found" || run.result === "Held").length;
  const connecting = stats === null && !failed;

  return (
    <div className={styles.shell}>
      <main className={styles.page}>
        {projectId && <p><Link to={`/projects/${projectId}`}>← {project?.name || "Project"} workspace</Link></p>}
        {projectError && <p role="alert">{projectError}</p>}
        <header className={styles.header}>
          <div>
            <h1 className={styles.title}>CI/CD Enforcement</h1>
            <p className={styles.lead}>
              Every pull request is checked before it can merge. Changes that break an architecture rule are held until a reviewer decides.
            </p>
          </div>
          <div className={`${styles.live} ${failed ? styles.liveOff : ""}`} aria-live="polite">
            <span className={styles.liveDot} />
            {failed ? "Backend offline" : updatedAt ? `Live · ${updatedAt.toLocaleTimeString()}` : "Connecting"}
          </div>
        </header>

        {failed && (
          <div className={`${styles.notice} ${styles.noticeBad}`} role="alert">
            Cannot reach the backend. Make sure the AKEGP application is running on port 8080.
          </div>
        )}

        <section className={styles.stats} aria-label="Summary">
          <Stat label="Needs a reviewer" value={connecting ? undefined : needsReview} hint="Held pull requests, merge blocked" tone="warn" />
          <Stat label="Scans run" value={stats?.runs} hint="Every push is checked" />
          <Stat label="Skipped" value={stats?.skipped} hint="Docs or config, no scan needed" />
          <Stat label="Violations found" value={stats?.violations} hint="Reported by the scanner" tone="bad" />
        </section>

        <section aria-labelledby="activity-heading">
          <div className={styles.sectionHead}>
            <h2 id="activity-heading" className={styles.heading}>Pull requests</h2>
            {results.length > 2 && (
              <div className={styles.filters} role="group" aria-label="Filter by result">
                {results.map((result) => (
                  <button
                    key={result}
                    type="button"
                    className={`${styles.chip} ${filter === result ? styles.chipOn : ""}`}
                    aria-pressed={filter === result}
                    onClick={() => setFilter(result)}
                  >
                    {result}
                  </button>
                ))}
              </div>
            )}
          </div>
          <p className={styles.hint}>Click a pull request to see what happened and, if it is held, to record a decision.</p>

          {connecting ? (
            <div className={styles.empty}><span className="loading-spinner" /><p className={styles.emptyText}>Connecting…</p></div>
          ) : shown.length === 0 ? (
            <div className={styles.empty}>
              <p className={styles.emptyTitle}>No pull requests yet</p>
              <p className={styles.emptyText}>Open a pull request in a linked repository and it will appear here.</p>
            </div>
          ) : (
            <div className={styles.list}>
              {shown.map((run) => {
                const key = `${run.repo}#${run.pr}`;
                const saved = fixResults[key];
                return (
                  <PullRequestCard
                    key={key}
                    run={run}
                    detail={details.get(run.runId)}
                    older={earlier.get(key) ?? []}
                    result={saved && saved.runId === run.runId ? saved : null}
                    expanded={open.has(key)}
                    onToggle={() => toggle(key)}
                    onStart={() => setResult(key, null)}
                    onDecided={(outcome) => { setResult(key, fixResult(outcome)); void refresh(); }}
                  />
                );
              })}
            </div>
          )}
        </section>

        <p className={styles.note}>Violations come from a stand-in for Component 2 until it is connected.</p>
      </main>
    </div>
  );
}
