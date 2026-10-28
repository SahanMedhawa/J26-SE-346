import { lazy, Suspense, useState } from "react";
import { ProjectSidebar } from "../shared/auth/ProjectSidebar";
import { readSidebarMode, SidebarMode } from "../shared/auth/SidebarFrame";
import { PlatformSidebar } from "../shared/auth/PlatformSidebar";
import { projectModuleRoute, routedProjectId, useProjectNavigation } from "../shared/auth/useProjectNavigation";
import { AccountNavigation } from "../shared/auth/AccountNavigation";
import { WorkspaceProjectRedirect } from "../modules/workspace/WorkspaceProjectRedirect";
import { LoginPage } from "../modules/auth/LoginPage";
import { AuthCallback } from "../modules/auth/AuthCallback";
import { ProfilePage } from "../modules/profile/ProfilePage";
import { WorkspaceListPage } from "../modules/workspace/WorkspaceListPage";
import { CreateWorkspacePage } from "../modules/workspace/CreateWorkspacePage";
import { WorkspaceDashboard, WorkspaceSettingsPage } from "../modules/workspace/WorkspaceDashboard";
import { WorkspaceMembersPage } from "../modules/workspace/WorkspaceMembersPage";
import { InvitationAcceptPage } from "../modules/workspace/InvitationAcceptPage";
import { ProtectedRoute } from "../shared/auth/ProtectedRoute";
import { useAuth } from "../shared/auth/useAuth";
import "../modules/workspace/accounts.css";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
const AnalyticsPage = lazy(async () => ({ default: (await import("../modules/analytics/AnalyticsPage")).AnalyticsPage }));
const ConformancePage = lazy(async () => ({ default: (await import("../modules/conformance/ConformancePage")).ConformancePage }));
import { RepositoryPage } from "../modules/conformance/RepositoryPage";
import { SettingsPage } from "../modules/conformance/SettingsPage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { HomePage } from "../modules/conformance/HomePage";
import { CreateProjectPage } from "../modules/conformance/CreateProjectPage";
import { ProjectDashboardPage } from "../modules/conformance/ProjectDashboardPage";
import { ScanHistoryPage } from "../modules/conformance/ScanHistoryPage";
import { ConformanceViolationsPage } from "../modules/conformance/ConformanceViolationsPage";
import { ADRManagementPage, RulesPage } from "../modules/rules/RulesPage";
import { ArchitectureRulesPage } from "../modules/rules/ArchitectureRulesPage";
import { RuleValidationPage } from "../modules/rules/RuleValidationPage";
import { SelectedProjectProvider } from "../shared/projects/SelectedProject";
import { ArchitectureProjects } from "../shared/projects/ArchitectureProjects";
import "../styles.css";
import { WorkspaceProvider, useWorkspaces } from "../shared/workspaces/WorkspaceContext";
import "../shared/ui/Platform.css";

export function App() {
  return <SelectedProjectProvider><WorkspaceProvider><AppContent/></WorkspaceProvider></SelectedProjectProvider>;
}
function AppContent() {
  const location = useLocation();
  const { user, logout } = useAuth();
  const workspace = useWorkspaces();
  const projectNavigation = useProjectNavigation(workspace, user?.id);
  const insideProject = !!routedProjectId(location.pathname) || projectModuleRoute.test(location.pathname);
  const showHeader = !!user && !["/login", "/auth/callback"].includes(location.pathname);
  const showSidebar = showHeader && !insideProject;
  const [sidebarModes, setSidebarModes] = useState(() => ({ platform: readSidebarMode("platform"), project: readSidebarMode("project") }));
  const sidebarKind = insideProject ? "project" : "platform";
  const sidebarMode = sidebarModes[sidebarKind];
  function changeSidebarMode(mode: SidebarMode) {
    setSidebarModes(previous => ({ ...previous, [sidebarKind]: mode }));
    try { localStorage.setItem(`akegp.sidebar.${sidebarKind}`, mode); } catch { /* Keep the preference for this session. */ }
  }
  return <div className={`app-shell${showHeader ? " has-system-header" : ""}${showSidebar ? " has-platform-sidebar" : ""}${showHeader ? " has-navigation-sidebar" : ""}`} data-sidebar-mode={sidebarMode}>
    {showHeader && <AccountNavigation user={user} onLogout={logout} workspace={workspace} projects={projectNavigation}/>}
    {showSidebar && <PlatformSidebar workspaceId={workspace.current?.id} mode={sidebarMode} onModeChange={changeSidebarMode}/>}
    {showHeader && insideProject && <ProjectSidebar projectId={routedProjectId(location.pathname) || projectNavigation.current?.id} mode={sidebarMode} onModeChange={changeSidebarMode}/>}
    <div id="main-content" className="app-route-content" tabIndex={-1}>

		<Suspense fallback={<div className="platform-page" role="status">Loading workspace view…</div>}>
    <Routes>
      <Route path="/login" element={<LoginPage/>}/>
      <Route path="/auth/callback" element={<AuthCallback/>}/>
      <Route path="/invitations/:token" element={<InvitationAcceptPage/>}/>
      <Route element={<ProtectedRoute/>}>
      <Route path="/profile" element={<ProfilePage/>}/>
      <Route path="/workspaces" element={<WorkspaceListPage/>}/>
      <Route path="/workspaces/new" element={<CreateWorkspacePage/>}/>
      <Route path="/workspaces/:workspaceId" element={<WorkspaceDashboard/>}/>
      <Route path="/workspaces/:workspaceId/projects/:projectId/*" element={<WorkspaceProjectRedirect/>}/>
      <Route path="/workspaces/:workspaceId/settings" element={<WorkspaceSettingsPage/>}/>
      <Route path="/workspaces/:workspaceId/members" element={<WorkspaceMembersPage/>}/>
			<Route path="/" element={<Navigate to="/workspaces" replace/>}/>
			<Route path="/home" element={<HomePage/>}/>
			<Route path="/projects/new" element={<CreateProjectPage/>}/>
			<Route path="/projects" element={<ArchitectureProjects/>}/>
			<Route path="/projects/:projectId" element={<ProjectDashboardPage/>}/>
			<Route path="/projects/:projectId/analytics/*" element={<AnalyticsPage/>}/>
			<Route path="/projects/:projectId/conformance" element={<ScanHistoryPage/>}/>
			<Route path="/projects/:projectId/conformance/violations" element={<ConformanceViolationsPage/>}/>
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
			<Route path="/conformance/violations" element={<ConformanceViolationsPage/>}/>
			<Route path="/conformance/graph" element={<ConformancePage/>}/>
			<Route path="/conformance/graph/:scanId" element={<ConformancePage/>}/>
			<Route path="/enforcement" element={<EnforcementPage/>}/>
			<Route path="/analytics/*" element={<AnalyticsPage/>}/>
			<Route path="*" element={<HomePage/>}/>
		</Route>
		</Routes>
    </Suspense>
	</div>
  </div>;
}
