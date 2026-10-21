import { Link, useParams } from "react-router-dom";
import "./ConformanceViolationsPage.css";

export function ConformanceViolationsPage() {
  const { projectId } = useParams();
  const backTo = projectId ? `/projects/${projectId}/conformance` : "/conformance";

  return <main className="conformance-violations-page">
    <header className="conformance-violations-topbar">
      <Link to="/" className="conformance-violations-brand">AKEGP</Link>
      <Link to={backTo} className="conformance-violations-back">Back to conformance</Link>
    </header>
    <section className="conformance-violations-content" aria-labelledby="violations-title">
      <nav className="conformance-section-tabs" aria-label="Conformance sections">
        <Link className="conformance-section-tab" to={backTo}>Scan history</Link>
        <Link className="conformance-section-tab active" to={`${backTo}/violations`}>Violations</Link>
      </nav>
      <p className="conformance-violations-kicker">CONFORMANCE WORKSPACE</p>
      <div className="conformance-violations-heading">
        <div><h1 id="violations-title">Violations</h1><p>Architecture rule findings for this project.</p></div>
        <span className="conformance-violations-status"><i aria-hidden="true" /> Awaiting analysis</span>
      </div>
      <section className="conformance-violations-panel" role="status" aria-label="No violations available">
        <div className="conformance-violations-panel-top"><span className="conformance-violations-panel-icon" aria-hidden="true">!</span><span>ANALYSIS RESULTS</span></div>
        <div className="conformance-violations-empty">
          <h2>No violations to display</h2>
          <p>Findings will appear here when project rules are compared with the conformance graph.</p>
        </div>
        <div className="conformance-violations-panel-footer"><span>RULES</span><span>CONFORMANCE GRAPH</span><span>FINDINGS</span></div>
      </section>
    </section>
  </main>;
}
