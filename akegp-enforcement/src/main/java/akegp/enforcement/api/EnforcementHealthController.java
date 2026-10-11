package akegp.enforcement.api;
import akegp.shared.api.EnforcementDecisionService;
import akegp.shared.dto.GovernanceDecisionDto;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
@RestController
public class EnforcementHealthController {
  @GetMapping("/api/enforcement/health") public String health() { return "ok"; }
}
@Service
class EnforcementDecisionServiceImpl implements EnforcementDecisionService {
  public GovernanceDecisionDto decide(String runId) { return new GovernanceDecisionDto("", "UNDECIDED", "skeleton"); }
}
