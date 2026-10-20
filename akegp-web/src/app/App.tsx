import { Link, Navigate, Route, Routes, useLocation, useMatch } from "react-router-dom";
import { AnalyticsPage } from "../modules/analytics/AnalyticsPage";
import { ConformancePage } from "../modules/conformance/ConformancePage";
import { RepositoryPage } from "../modules/conformance/RepositoryPage";
import { SettingsPage } from "../modules/conformance/SettingsPage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { HomePage } from "../modules/conformance/HomePage";
import { CreateProjectPage } from "../modules/conformance/CreateProjectPage";
import { ProjectDashboardPage } from "../modules/conformance/ProjectDashboardPage";
import { ScanHistoryPage } from "../modules/conformance/ScanHistoryPage";
import { ADRManagementPage, RulesPage } from "../modules/rules/RulesPage";
import { ArchitectureRulesPage } from "../modules/rules/ArchitectureRulesPage";
import { RuleValidationPage } from "../modules/rules/RuleValidationPage";
import { SelectedProjectProvider } from "../shared/projects/SelectedProject";
import { ArchitectureProjects } from "../shared/projects/ArchitectureProjects";
import { Brand } from "../shared/Brand";
import "../styles.css";

export function App() {
	const location = useLocation();
	const projectRulesMatch = useMatch("/projects/:projectId/*");
	const projectId = projectRulesMatch?.params.projectId;
	const componentOnePage = /^\/(?:rules|analytics|projects\/[^/]+\/(?:rules|analytics))(?:\/|$)/.test(location.pathname);
	const standalonePage = ["/", "/home", "/repository", "/projects", "/projects/new", "/settings", "/conformance", "/conformance/graph"].includes(location.pathname) || (location.pathname.startsWith("/projects/") && !componentOnePage) || location.pathname.startsWith("/conformance/graph/");
	const settingsLink = <Link className="app-settings-link" to="/settings" aria-label="Project settings" title="Project settings">&#9881;</Link>;
	return <SelectedProjectProvider><div className="app-shell">
		{!standalonePage && <header className={componentOnePage ? "topbar component-one-topbar" : "topbar"}>
			{componentOnePage ? <Brand /> : <Link className="brand" to="/" aria-label="AKEGP home"><span className="brand-mark">A</span><span className="brand-name">AKEGP <span>/ PLATFORM</span></span></Link>}
			<span className="topbar-note">ARCHITECTURE KNOWLEDGE SYSTEM</span>
			{componentOnePage ? <div className="component-one-header-actions">
				{projectId && <Link className="project-dashboard-link" to={`/projects/${encodeURIComponent(projectId)}`}>← Project Dashboard</Link>}
				{settingsLink}
			</div> : settingsLink}
		</header>}
		<Routes>
			<Route path="/" element={<HomePage/>}/>
			<Route path="/home" element={<HomePage/>}/>
			<Route path="/projects/new" element={<CreateProjectPage/>}/>
			<Route path="/projects" element={<ArchitectureProjects/>}/>
			<Route path="/projects/:projectId" element={<ProjectDashboardPage/>}/>
			<Route path="/projects/:projectId/analytics/*" element={<AnalyticsPage/>}/>
			<Route path="/projects/:projectId/conformance" element={<ScanHistoryPage/>}/>
			<Route path="/projects/:projectId/enforcement" element={<EnforcementPage/>}/>
			<Route path="/repository" element={<RepositoryPage/>}/>
			<Route path="/rules" element={<RulesPage/>}>
				<Route index element={<Navigate replace to="architecture-rules" />} />
				<Route path="projects" element={<Navigate replace to="/projects" />}/>
				<Route path="adrs" element={<ADRManagementPage />} />
				<Route path="architecture-rules" element={<ArchitectureRulesPage />} />
				<Route path="validation" element={<RuleValidationPage />} />
				<Route path="*" element={<Navigate replace to="architecture-rules" />} />
			</Route>
			<Route path="/projects/:projectId/rules" element={<RulesPage/>}>
				<Route index element={<Navigate replace to="architecture-rules" />} />
				<Route path="adrs" element={<ADRManagementPage />} />
				<Route path="architecture-rules" element={<ArchitectureRulesPage />} />
				<Route path="validation" element={<RuleValidationPage />} />
				<Route path="*" element={<Navigate replace to="architecture-rules" />} />
			</Route>
			<Route path="/settings" element={<SettingsPage/>}/>
			<Route path="/conformance" element={<ScanHistoryPage/>}/>
			<Route path="/conformance/graph" element={<ConformancePage/>}/>
			<Route path="/conformance/graph/:scanId" element={<ConformancePage/>}/>
			<Route path="/enforcement" element={<EnforcementPage/>}/>
			<Route path="/analytics/*" element={<AnalyticsPage/>}/>
			<Route path="*" element={<HomePage/>}/>
		</Routes>
	</div></SelectedProjectProvider>;
}
