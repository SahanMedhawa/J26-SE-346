import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AnalyticsPage } from "../modules/analytics/AnalyticsPage";
import { ConformancePage } from "../modules/conformance/ConformancePage";
import { RepositoryPage } from "../modules/conformance/RepositoryPage";
import { SettingsPage } from "../modules/conformance/SettingsPage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { HomePage } from "../modules/conformance/HomePage";
import { CreateProjectPage } from "../modules/conformance/CreateProjectPage";
import { ProjectDashboardPage } from "../modules/conformance/ProjectDashboardPage";
import { ProjectLibraryPage } from "../modules/conformance/ProjectLibraryPage";
import { ADRManagementPage, ProjectsPage, RulesPage } from "../modules/rules/RulesPage";
import { ArchitectureRulesPage } from "../modules/rules/ArchitectureRulesPage";
import { RuleValidationPage } from "../modules/rules/RuleValidationPage";
import "../styles.css";

export function App() {
	const location = useLocation();
	const standalonePage = ["/", "/home", "/repository", "/projects", "/projects/new", "/settings", "/conformance", "/conformance/graph"].includes(location.pathname) || location.pathname.startsWith("/projects/");
	return <div className="app-shell">
		{!standalonePage && <header className="topbar">
			<Link className="brand" to="/" aria-label="AKEGP home"><span className="brand-mark">A</span><span className="brand-name">AKEGP <span>/ PLATFORM</span></span></Link>
			<span className="topbar-note">ARCHITECTURE KNOWLEDGE SYSTEM</span>
			<Link className="app-settings-link" to="/settings" aria-label="Project settings" title="Project settings">&#9881;</Link>
		</header>}
		<Routes>
			<Route path="/" element={<HomePage/>}/>
			<Route path="/home" element={<HomePage/>}/>
			<Route path="/projects/new" element={<CreateProjectPage/>}/>
			<Route path="/projects" element={<ProjectLibraryPage/>}/>
			<Route path="/projects/:projectId" element={<ProjectDashboardPage/>}/>
			<Route path="/repository" element={<RepositoryPage/>}/>
			<Route path="/rules" element={<RulesPage/>}>
				<Route index element={<Navigate replace to="architecture-rules" />} />
				<Route path="projects" element={<ProjectsPage/>}/>
				<Route path="adrs" element={<ADRManagementPage />} />
				<Route path="architecture-rules" element={<ArchitectureRulesPage />} />
				<Route path="validation" element={<RuleValidationPage />} />
				<Route path="*" element={<Navigate replace to="architecture-rules" />} />
			</Route>
			<Route path="/settings" element={<SettingsPage/>}/>
			<Route path="/conformance" element={<RepositoryPage/>}/>
			<Route path="/conformance/graph" element={<ConformancePage/>}/>
			<Route path="/enforcement" element={<EnforcementPage/>}/>
			<Route path="/analytics/*" element={<AnalyticsPage/>}/>
			<Route path="*" element={<HomePage/>}/>
		</Routes>
	</div>;
}
