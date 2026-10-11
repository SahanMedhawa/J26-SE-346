import { Link, Route, Routes } from "react-router-dom";
import { RulesPage } from "../modules/rules/RulesPage";
import { ConformancePage } from "../modules/conformance/ConformancePage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { AnalyticsPage } from "../modules/analytics/AnalyticsPage";
export function App() {
  return <><nav><Link to="/rules">Rules</Link> <Link to="/conformance">Conformance</Link> <Link to="/enforcement">Enforcement</Link> <Link to="/analytics">Analytics</Link></nav><Routes><Route path="/rules" element={<RulesPage/>}/><Route path="/conformance" element={<ConformancePage/>}/><Route path="/enforcement" element={<EnforcementPage/>}/><Route path="/analytics/*" element={<AnalyticsPage/>}/><Route path="*" element={<RulesPage/>}/></Routes></>;
}
