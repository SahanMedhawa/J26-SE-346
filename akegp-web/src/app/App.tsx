import { Link, Route, Routes, useLocation } from "react-router-dom";
import { RulesPage } from "../modules/rules/RulesPage";
import { ConformancePage } from "../modules/conformance/ConformancePage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { AnalyticsPage } from "../modules/analytics/AnalyticsPage";
import { SettingsPage } from "../modules/conformance/SettingsPage";
import { HomePage } from "../modules/conformance/HomePage";
import { RepositoryPage } from "../modules/conformance/RepositoryPage";
export function App() {
  const location = useLocation();
  const workflowPage = ["/", "/home", "/repository", "/settings", "/conformance", "/conformance/graph"].includes(location.pathname);
  return <>{!workflowPage && <nav><Link to="/">Home</Link> <Link to="/rules">Rules</Link> <Link to="/conformance">Conformance</Link> <Link to="/enforcement">Enforcement</Link> <Link to="/analytics">Analytics</Link> <Link to="/settings">Settings</Link></nav>}<Routes><Route path="/" element={<HomePage/>}/><Route path="/home" element={<HomePage/>}/><Route path="/repository" element={<RepositoryPage/>}/><Route path="/rules" element={<RulesPage/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="/conformance" element={<RepositoryPage/>}/><Route path="/conformance/graph" element={<ConformancePage/>}/><Route path="/enforcement" element={<EnforcementPage/>}/><Route path="/analytics/*" element={<AnalyticsPage/>}/><Route path="*" element={<HomePage/>}/></Routes></>;
}
