import { Link } from "react-router-dom";

type ComponentCard = {
  number: string;
  name: string;
  description: string;
  route: string;
  icon: "rules" | "conformance" | "enforcement" | "analytics";
};

const components: ComponentCard[] = [
  {
    number: "01",
    name: "Architecture Rule Engineering & Validation",
    description: "Define, validate, and evolve architecture rules as executable knowledge.",
    route: "/rules",
    icon: "rules",
  },
  {
    number: "02",
    name: "Deterministic Structural Conformance",
    description: "Measure system structure against approved architectural boundaries with repeatable results.",
    route: "/conformance",
    icon: "conformance",
  },
  {
    number: "03",
    name: "CI/CD Enforcement",
    description: "Bring architecture checks into delivery pipelines so violations are caught early.",
    route: "/enforcement",
    icon: "enforcement",
  },
  {
    number: "04",
    name: "Architecture Drift & Governance",
    description: "Track architectural health over time and make governance decisions with confidence.",
    route: "/analytics",
    icon: "analytics",
  },
];

function ComponentIcon({ type }: { type: ComponentCard["icon"] }) {
  if (type === "rules") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4.5h14v15H5z" /><path d="M8.5 8h7M8.5 12h7M8.5 16h4" /></svg>;
  }
  if (type === "conformance") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 7 3.5v5.25c0 4.3-2.92 7.5-7 9.25-4.08-1.75-7-4.95-7-9.25V6.5z" /><path d="m8.5 12 2.25 2.25L15.75 9" /></svg>;
  }
  if (type === "enforcement") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6.5h16M4 12h16M4 17.5h16" /><circle cx="8" cy="6.5" r="1.5" /><circle cx="16" cy="12" r="1.5" /><circle cx="10" cy="17.5" r="1.5" /></svg>;
  }
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19V5M4 19h16" /><path d="m7 15 3-4 3 2 5-7" /><circle cx="7" cy="15" r="1" /><circle cx="10" cy="11" r="1" /><circle cx="13" cy="13" r="1" /><circle cx="18" cy="6" r="1" /></svg>;
}

export function HomePage() {
  return (
    <main className="home-page">
      <section className="hero">
        <p className="eyebrow"><span className="status-dot" /> Architecture intelligence platform</p>
        <h1>Architecture knowledge,<br /><span>engineered to endure.</span></h1>
        <p className="hero-copy">
          AKEGP supports architecture rule engineering, structural conformance,
          CI/CD enforcement, and architecture governance in one connected platform.
        </p>
        <div className="hero-meta">
          <span><strong>04</strong> integrated capabilities</span>
          <span className="meta-divider" />
          <span>Designed for resilient systems</span>
        </div>
      </section>

      <section className="workspace-section" aria-labelledby="workspace-heading">
        <div className="section-heading">
          <div>
            <p className="section-kicker">Platform modules</p>
            <h2 id="workspace-heading">Choose a workspace</h2>
          </div>
          <p className="section-description">Move from architectural intent to measurable, governed outcomes.</p>
        </div>
        <div className="component-grid">
          {components.map((component) => (
            <article className="component-card" key={component.route}>
              <div className="card-topline">
                <span className="component-number">{component.number}</span>
                <span className="card-icon"><ComponentIcon type={component.icon} /></span>
              </div>
              <div className="card-content">
                <h3>{component.name}</h3>
                <p>{component.description}</p>
              </div>
              <Link className="workspace-link" to={component.route}>
                Open workspace <span aria-hidden="true">-&gt;</span>
              </Link>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
