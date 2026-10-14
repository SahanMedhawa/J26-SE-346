import { Link, Route, Routes } from "react-router-dom";
import { HomePage } from "../modules/home/HomePage";
import "../styles.css";
import { RulesPage } from "../modules/rules/RulesPage";
import { ConformancePage } from "../modules/conformance/ConformancePage";
import { EnforcementPage } from "../modules/enforcement/EnforcementPage";
import { AnalyticsPage } from "../modules/analytics/AnalyticsPage";
export function App() {
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/" aria-label="AKEGP home">
          <span className="brand-mark">A</span>
          <span className="brand-name">AKEGP <span>/ PLATFORM</span></span>
        </Link>
        <span className="topbar-note">ARCHITECTURE KNOWLEDGE SYSTEM</span>
      </header>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/rules/*" element={<RulesPage />} />
        <Route path="/conformance" element={<ConformancePage />} />
        <Route path="/enforcement" element={<EnforcementPage />} />
        <Route path="/analytics/*" element={<AnalyticsPage />} />
        <Route path="*" element={<HomePage />} />
      </Routes>
    </div>
  );
}
