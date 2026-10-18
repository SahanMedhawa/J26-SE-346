import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AnalyticsPage } from "../modules/analytics/AnalyticsPage";
import { ConformancePage } from "../modules/conformance/ConformancePage";
import { RepositoryPage } from "../modules/conformance/RepositoryPage";
import { SettingsPage } from "../modules/conformance/SettingsPage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { HomePage } from "../modules/conformance/HomePage";
import { ADRManagementPage, RulesPage } from "../modules/rules/RulesPage";
import { ArchitectureRulesPage } from "../modules/rules/ArchitectureRulesPage";
import { RuleValidationPage } from "../modules/rules/RuleValidationPage";
import { SelectedProjectProvider } from "../shared/projects/SelectedProject";
import { ArchitectureProjects } from "../shared/projects/ArchitectureProjects";
import "../styles.css";

export function App() {
	const location = useLocation();
	const standalonePage = ["/", "/home", "/repository", "/settings", "/conformance", "/conformance/graph"].includes(location.pathname);
	return <SelectedProjectProvider><div className="app-shell">
		{!standalonePage && <header className="topbar">
			<Link className="brand" to="/" aria-label="AKEGP home"><span className="brand-mark">A</span><span className="brand-name">AKEGP <span>/ PLATFORM</span></span></Link>
			<span className="topbar-note">ARCHITECTURE KNOWLEDGE SYSTEM</span>
			<Link className="app-settings-link" to="/settings" aria-label="Project settings" title="Project settings">&#9881;</Link>
		</header>}
		<Routes>
			<Route path="/" element={<HomePage/>}/>
			<Route path="/home" element={<HomePage/>}/>
			<Route path="/projects" element={<ArchitectureProjects/>}/>
			<Route path="/repository" element={<RepositoryPage/>}/>
			<Route path="/rules" element={<RulesPage/>}>
				<Route index element={<Navigate replace to="architecture-rules" />} />
				<Route path="projects" element={<Navigate replace to="/projects" />}/>
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
	</div></SelectedProjectProvider>;
}
