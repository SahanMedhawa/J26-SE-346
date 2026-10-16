import { useState } from "react";
import { Link } from "react-router-dom";
import "./SettingsPage.css";

type ProjectSettings = { projectName: string; defaultBranch: string; authRequired: boolean; enforceInPipeline: boolean };
const settingsKey = "akegp-project-settings";
function loadSettings(): ProjectSettings {
	try { return { projectName: "Architecture Governance", defaultBranch: "main", authRequired: true, enforceInPipeline: true, ...JSON.parse(localStorage.getItem(settingsKey) ?? "{}") }; }
	catch { return { projectName: "Architecture Governance", defaultBranch: "main", authRequired: true, enforceInPipeline: true }; }
}

export function SettingsPage() {
	const [settings, setSettings] = useState<ProjectSettings>(loadSettings);
	const [notice, setNotice] = useState("");
	function update(field: keyof ProjectSettings, value: string | boolean) { setSettings(current => ({ ...current, [field]: value })); setNotice(""); }
	function save() { localStorage.setItem(settingsKey, JSON.stringify(settings)); setNotice("Project settings saved."); }
	return <main className="settings-app"><header className="settings-topbar"><Link className="settings-home-link" to="/">AKEGP</Link><div className="settings-top-context">PROJECT SETTINGS</div><Link className="settings-avatar" to="/conformance" aria-label="Open conformance">C</Link></header><section className="settings-main settings-main-wide"><header className="settings-heading"><div className="settings-crumb"><Link to="/">Home</Link> / Project Settings</div><h1>Project Settings</h1><p>Manage project identity, repository access, and architecture validation across your delivery pipeline.</p></header><div className="settings-form"><section className="settings-card"><h2>Project Defaults</h2><div className="settings-rule"/><div className="settings-fields settings-project-fields"><label className="settings-label">Project name<input value={settings.projectName} onChange={event => update("projectName", event.target.value)} /></label><label className="settings-label">Default branch<input value={settings.defaultBranch} onChange={event => update("defaultBranch", event.target.value)} /></label></div></section><section className="settings-card"><h2>Pipeline Validation</h2><div className="settings-rule"/><button className={`auth-toggle ${settings.enforceInPipeline ? "on" : ""}`} type="button" onClick={() => update("enforceInPipeline", !settings.enforceInPipeline)}><i/><span>Validate architecture rules in CI pipelines<small>Run conformance checks on proposed code changes</small></span></button></section><section className="settings-card"><h2>Repository Access</h2><div className="settings-rule"/><button className={`auth-toggle ${settings.authRequired ? "on" : ""}`} type="button" onClick={() => update("authRequired", !settings.authRequired)}><i/><span>Require authentication for private repositories<small>Uses the GitHub token configured for the server</small></span></button></section>{notice && <p className="settings-notice" role="status">{notice}</p>}</div><footer className="settings-actions"><span>{notice || "Project-wide configuration"}</span><button className="save-config" type="button" onClick={save}>Save Project Settings</button></footer></section></main>;
}
