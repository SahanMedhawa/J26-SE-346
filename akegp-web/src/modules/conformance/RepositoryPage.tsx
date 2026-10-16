import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./WorkflowPages.css";

type CodeGraph = { files: { path: string; language: string }[]; dependencies: { source: string; target: string }[] };
const availableLanguages = ["Java", "TypeScript", "JavaScript"];

export function RepositoryPage() {
	const navigate = useNavigate();
	const [repositoryUrl, setRepositoryUrl] = useState("");
	const [languages, setLanguages] = useState(availableLanguages);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	function toggleLanguage(name: string) {
		setLanguages(current => current.includes(name) ? current.filter(language => language !== name) : [...current, name]);
		setError("");
	}
	async function startScan(event: FormEvent) {
		event.preventDefault();
		if (!languages.length) { setError("Choose at least one language to scan."); return; }
		let owner = "";
		let repository = "";
		try {
			const url = new URL(repositoryUrl.trim());
			const parts = url.pathname.split("/").filter(Boolean);
			if (url.protocol !== "https:" || url.hostname !== "github.com" || parts.length < 2) throw new Error();
			[owner, repository] = parts;
			repository = repository.replace(/\.git$/i, "");
		} catch { setError("Enter a valid GitHub repository URL, such as https://github.com/owner/repository."); return; }
		setError(""); setLoading(true);
		try {
			const response = await fetch("/api/conformance/graphs/import", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ repositoryId: repository.toLowerCase(), repositoryUrl: `https://github.com/${owner}/${repository}`, languages }) });
			if (!response.ok) throw new Error(await response.text());
			const graph = await response.json() as CodeGraph;
			navigate("/conformance/graph", { state: { repositoryId: repository.toLowerCase(), graph } });
		} catch (scanError) { setError(scanError instanceof Error ? scanError.message : "The repository could not be scanned."); }
		finally { setLoading(false); }
	}
	return <main className="workflow-app repository-app"><header className="workflow-topbar"><Link to="/" className="workflow-wordmark">AKEGP</Link><span>Governance Engine</span><div className="workflow-top-actions">CONFORMANCE / REPOSITORY SCAN</div><Link to="/settings" className="repository-settings" aria-label="Project settings" title="Project settings">Settings</Link></header><section className="repository-main repository-main-wide"><div className="repository-breadcrumb"><Link to="/">Home</Link><span>/</span><strong>Conformance scan</strong></div><div className={`repository-card ${loading ? "scanning" : ""}`}>
			{loading ? <div className="scan-progress"><div className="scan-spinner"/><div className="repository-step">CONFORMANCE SCAN IN PROGRESS</div><h1>Mapping your<br/>architecture.</h1><p>Downloading the repository, discovering packages, and tracing dependencies across the selected languages.</p><div className="scan-progress-bar"><i/></div><small>SCANNING REPOSITORY - THIS MAY TAKE A MOMENT</small></div> : <><div className="repository-step">01 / CONFORMANCE</div><h1>Map your<br/>architecture.</h1><p>Enter a GitHub repository and choose which languages to include. AKEGP will scan its packages and dependencies, then open the conformance graph.</p><form onSubmit={startScan}><label htmlFor="github-repository">GitHub repository URL</label><div className="repository-input"><span>GH</span><input id="github-repository" type="url" required value={repositoryUrl} onChange={event => { setRepositoryUrl(event.target.value); setError(""); }} placeholder="https://github.com/owner/repository"/></div><fieldset className="repository-languages"><legend>Languages to scan</legend>{availableLanguages.map(name => <label key={name} className="repository-language"><input type="checkbox" checked={languages.includes(name)} onChange={() => toggleLanguage(name)}/><span><b>{name}</b><small>{name === "Java" ? "Java source files" : name === "TypeScript" ? "TypeScript and TSX files" : "JavaScript and JSX files"}</small></span></label>)}</fieldset>{error && <p className="repository-error" role="alert">{error}</p>}<button type="submit" disabled={loading}>Scan repository <span>→</span></button></form><div className="repository-note">The scan includes all selected languages in one dependency graph.</div></>}
		</div><div className="repository-progress"><span className="progress-active">1 / Select repository</span><i/><span className={loading ? "progress-active" : ""}>2 / Scan architecture</span><i/><span>3 / Explore conformance</span></div></section></main>;
}
