import { Fragment, useMemo, useState } from "react";
import { useEnforcementStats } from "./Useenforcementstats";
import type { RunDetail, RunSummary } from "./Enforcementapi";
import styles from "./EnforcementPage.module.css";

const RESULT_STYLE: Record<string, string> = {
  Passed: styles.passed,
  Skipped: styles.skipped,
  "Violation found": styles.violation,
  Held: styles.held,
  "Exception approved": styles.passed,
  "Changes requested": styles.held,
  Rejected: styles.violation,
  "In progress": styles.info,
};

const HOLD_REASON_TEXT: Record<string, string> = {
  violations: "Architecture violations found.",
  "project-not-linked": "This repository is not linked to a project, so the pull request is held until it is.",
  "scan-unavailable": "The architecture scan could not run, so the pull request is held (fail closed).",
};

function ago(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  return `${Math.floor(seconds / 86400)} d ago`;
}

function Stat({ label, value, hint, danger = false }: { label: string; value: number | undefined; hint: string; danger?: boolean }) {
  const hot = danger && (value ?? 0) > 0;
  return (
    <div className={styles.stat}>
      <div className={styles.statLabel}>{label}</div>
      <div className={`${styles.statValue} ${hot ? styles.statDanger : ""}`}>{value ?? "–"}</div>
      <div className={styles.statHint}>{hint}</div>
    </div>
  );
}

function ResultPill({ result }: { result: string }) {
  return <span className={`${styles.pill} ${RESULT_STYLE[result] ?? styles.info}`}>{result}</span>;
}

function DetailPanel({ detail }: { detail: RunDetail }) {
  if (detail.violations.length === 0) {
    const reason = detail.holdReason ? (HOLD_REASON_TEXT[detail.holdReason] ?? detail.holdReason) : null;
    return <div className={styles.detailEmpty}>{reason ?? "No violations recorded for this run."}</div>;
  }
  return (
    <ul className={styles.violations}>
      {detail.violations.map((v, index) => (
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

function RunsTable({ runs, details }: { runs: RunSummary[]; details: Map<number, RunDetail> }) {
  const [open, setOpen] = useState<Set<number>>(new Set());

  function toggle(runId: number) {
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(runId)) next.delete(runId);
      else next.add(runId);
      return next;
    });
  }

  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>PR</th>
            <th className={styles.hideSmall}>Change type</th>
            <th>Result</th>
            <th className={styles.hideSmall}>Repository</th>
            <th className={styles.hideSmall}>Files</th>
            <th className={styles.hideSmall}>When</th>
            <th><span className="visually-hidden">Details</span></th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => {
            const detail = details.get(run.runId);
            const showable = detail !== undefined && (detail.violations.length > 0 || detail.holdReason !== null);
            const expanded = open.has(run.runId);
            return (
              <Fragment key={run.runId}>
                <tr className={styles.row}>
                  <td className={styles.pr}>#{run.pr}</td>
                  <td className={styles.hideSmall}>{run.changeType}</td>
                  <td><ResultPill result={run.result} /></td>
                  <td className={`${styles.repo} ${styles.hideSmall}`} title={run.repo}>{run.repo}</td>
                  <td className={`${styles.dim} ${styles.hideSmall}`}>{run.files}</td>
                  <td className={`${styles.dim} ${styles.hideSmall}`} title={new Date(run.at).toLocaleString()}>{ago(run.at)}</td>
                  <td className={styles.actionCell}>
                    {showable && (
                      <button type="button" className={styles.detailButton} aria-expanded={expanded} onClick={() => toggle(run.runId)}>
                        {expanded ? "Hide" : "Details"}
                      </button>
                    )}
                  </td>
                </tr>
                {showable && expanded && (
                  <tr>
                    <td colSpan={7} className={styles.detailCell}>
                      <DetailPanel detail={detail} />
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function EmptyRuns() {
  return (
    <div className={styles.empty}>
      <span className="empty-icon">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 6.5h16M4 12h16M4 17.5h16" />
          <circle cx="8" cy="6.5" r="1.5" />
          <circle cx="16" cy="12" r="1.5" />
          <circle cx="10" cy="17.5" r="1.5" />
        </svg>
      </span>
      <h3 className={styles.emptyTitle}>No pipeline runs yet</h3>
      <p className={styles.emptyText}>Open a pull request in a linked repository and it will appear here.</p>
    </div>
  );
}

export function EnforcementPage() {
  const { stats, details, failed, updatedAt } = useEnforcementStats();
  const [filter, setFilter] = useState("All");

  const recent = stats?.recent ?? [];
  const results = useMemo(() => ["All", ...new Set(recent.map((run) => run.result))], [recent]);
  const shown = filter === "All" ? recent : recent.filter((run) => run.result === filter);
  const connecting = stats === null && !failed;

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>CI/CD <span>Enforcement</span></h1>
          <p className={styles.lead}>
            Every pull request is checked before it can merge. Changes that break an architecture rule are held for review.
          </p>
        </div>
        <div className={`${styles.live} ${failed ? styles.liveOff : ""}`} aria-live="polite">
          <span className={styles.liveDot} />
          {failed ? "Backend offline" : updatedAt ? `Live · ${updatedAt.toLocaleTimeString()}` : "Connecting"}
        </div>
      </header>

      <div className={styles.content}>
        {failed && (
          <div className="form-notice error-notice" role="alert">
            Cannot reach the backend. Make sure the AKEGP application is running on port 8080.
          </div>
        )}

        <section className={styles.stats} aria-label="Summary">
          <Stat label="Pipeline runs" value={stats?.runs} hint="Pull requests received" />
          <Stat label="Analysis triggered" value={stats?.analysisTriggered} hint="Code changes sent for scan" />
          <Stat label="Skipped" value={stats?.skipped} hint="Docs or config, no scan needed" />
          <Stat label="Violations" value={stats?.violations} hint="Found by the scanner" danger />
        </section>

        <section aria-labelledby="activity-heading">
          <div className={styles.sectionHead}>
            <div>
              <p className={styles.kickerSmall}>Pipeline</p>
              <h2 id="activity-heading" className={styles.heading}>Recent activity</h2>
            </div>
            <div className={styles.tools}>
              {results.length > 2 && (
                <div className={styles.filters} role="group" aria-label="Filter by result">
                  {results.map((result) => (
                    <button
                      key={result}
                      type="button"
                      className={`${styles.chip} ${filter === result ? styles.chipOn : ""}`}
                      onClick={() => setFilter(result)}
                    >
                      {result}
                    </button>
                  ))}
                </div>
              )}
              <span className={styles.count}>{shown.length} {shown.length === 1 ? "RUN" : "RUNS"}</span>
            </div>
          </div>

          {connecting ? (
            <div className={styles.empty}><span className="loading-spinner" /><p className={styles.emptyText}>Connecting…</p></div>
          ) : shown.length === 0 ? (
            <EmptyRuns />
          ) : (
            <RunsTable runs={shown} details={details} />
          )}
        </section>

        <p className={styles.note}>Violations come from a mock of Component 2 until it is connected.</p>
      </div>
    </main>
  );
}
