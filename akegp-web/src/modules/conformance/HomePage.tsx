import { Link } from "react-router-dom";
import "./WorkflowPages.css";

const governanceStages = [
	{ number: "01", title: "Define architecture rules", text: "Set boundaries for modules, dependencies, and approved patterns." },
	{ number: "02", title: "Validate every change", text: "Run architecture checks as part of your pull request and CI pipeline." },
	{ number: "03", title: "Catch architecture drift", text: "See when code changes move the repository away from its intended design." },
	{ number: "04", title: "Review and improve", text: "Trace violations and dependency impact, then track trends over time." }
];

const platformAreas = [
	{ number: "01", title: "Architecture rules", text: "Create and manage the rules that define how your system should be structured.", href: "/rules", link: "Manage rules" },
	{ number: "02", title: "Pipeline enforcement", text: "Review governance decisions and enforce architecture checks in delivery workflows.", href: "/enforcement", link: "Review enforcement" },
	{ number: "03", title: "Conformance & dependencies", text: "Explore package structure, cross-language dependencies, and conformance results.", href: "/conformance", link: "Open dependency map" },
	{ number: "04", title: "Architecture trends", text: "Follow violations, hotspots, and governance outcomes across scans.", href: "/analytics", link: "View analytics" }
];

export function HomePage() {
	return <main className="workflow-app home-app">
		<header className="home-topbar"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><div className="home-topbar-right"><span>ARCHITECTURE GOVERNANCE</span><Link to="/settings" className="home-settings-icon" aria-label="Open settings" title="Settings">⚙</Link></div></header>
		<section className="home-hero"><div className="home-hero-content"><div className="home-eyebrow">CONTINUOUS ARCHITECTURE GOVERNANCE</div><h1>Keep your architecture<br/><em>on the right track.</em></h1><p className="home-intro">Validate architecture rules in your delivery pipeline, catch drift as code changes, and understand how every component connects.</p><div className="home-hero-actions"><Link className="home-primary" to="/conformance">Start a conformance scan <span>→</span></Link><Link className="home-secondary" to="/rules">Explore architecture rules</Link></div></div><div className="home-hero-visual" aria-hidden="true"><div className="architecture-orbit orbit-one"/><div className="architecture-orbit orbit-two"/><i className="orbit-node node-a"/><i className="orbit-node node-b"/><i className="orbit-node node-c"/><i className="orbit-node node-d"/><div className="orbit-core">ARCH<br/>RULES</div><span className="orbit-label label-a">CI PIPELINE</span><span className="orbit-label label-b">DEPENDENCIES</span></div><div className="home-hero-foot"><span>RULES → PIPELINE → CONFORMANCE</span><span>GOVERNANCE FOR EVERY CHANGE</span></div></section>
		<section className="home-process" id="governance-flow"><div className="home-section-heading"><div><span className="home-eyebrow">HOW AKEGP WORKS</span><h2>Govern architecture through the pipeline.</h2></div><p>Make architecture checks part of the way your team already builds and ships software.</p></div><div className="home-stage-grid">{governanceStages.map(stage => <article className="home-stage" key={stage.number}><span>{stage.number}</span><h3>{stage.title}</h3><p>{stage.text}</p></article>)}</div></section>
		<section className="home-platform"><div className="home-section-heading"><div><span className="home-eyebrow">ONE GOVERNANCE WORKSPACE</span><h2>From architecture intent to code.</h2></div><p>Move from defining standards to understanding and resolving the changes that break them.</p></div><div className="home-platform-grid">{platformAreas.map(area => <article className="home-platform-card" key={area.number}><span>{area.number}</span><h3>{area.title}</h3><p>{area.text}</p><Link to={area.href}>{area.link} <b>↗</b></Link></article>)}</div></section>
		<footer className="home-footer"><Link to="/" className="home-brand"><span>✣</span><b>AKEGP</b></Link><span>Architecture knowledge for reliable software change.</span></footer>
	</main>;
}
