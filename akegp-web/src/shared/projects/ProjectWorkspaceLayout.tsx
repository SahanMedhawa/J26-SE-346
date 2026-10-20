import { ReactNode } from "react";
import { Link, NavLink } from "react-router-dom";

type WorkspaceModule = { label: string; to: string; icon: ReactNode };

/** Shared by rule engineering and analytics; retains the established workspace styles. */
export function ProjectWorkspaceLayout({ component, title, subtitle, status, projectId, projectName, modules, children, sidebarTitle, navLabel, statusIndicator = false }: {
  component: string; title: string; subtitle: string; status: string; projectId: string;
  projectName: string; modules: WorkspaceModule[]; children: ReactNode; sidebarTitle?: string; navLabel?: string; statusIndicator?: boolean;
}) {
  return <div className="rules-workspace">
    <aside className="rules-sidebar">
      <div className="sidebar-label">Component {component}</div>
      <div className="sidebar-title">{sidebarTitle || title}<br /><span>{subtitle}</span></div>
      <nav className="module-nav" aria-label={navLabel || `${title} modules`}>
        {modules.map(module => <NavLink className={({ isActive }) => `module-link${isActive ? " active" : ""}`} end key={module.to} to={module.to}>
          <span className="module-icon">{module.icon}</span><span>{module.label}</span><span className="module-arrow" aria-hidden="true">-&gt;</span>
        </NavLink>)}
      </nav>
      <div className="selected-project"><span className="selected-project-label">Selected project</span><strong>{projectName}</strong>
        <Link to={projectId ? `/projects/${encodeURIComponent(projectId)}` : "/projects"}>Project workspace</Link>
      </div>
      <div className="sidebar-footer"><span className="status-dot" /> AKEGP / PLATFORM</div>
    </aside>
    <div className="rules-main">
      <div className="rules-header"><div><p className="rules-kicker">Component {component} / Workspace</p>
        <h1>{title} <span>{subtitle}</span></h1></div><div className="workspace-status">{statusIndicator && <span />}{status}</div></div>
      {children}
    </div>
  </div>;
}
