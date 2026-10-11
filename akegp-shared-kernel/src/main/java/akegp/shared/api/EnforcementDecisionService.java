package akegp.shared.api;
import akegp.shared.dto.GovernanceDecisionDto;
public interface EnforcementDecisionService { GovernanceDecisionDto decide(String runId); }
