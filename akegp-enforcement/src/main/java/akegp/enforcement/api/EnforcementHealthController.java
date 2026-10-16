package akegp.enforcement.api;

import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.shared.api.EnforcementDecisionService;
import akegp.shared.dto.GovernanceDecisionDto;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnforcementHealthController {
  @GetMapping("/api/enforcement/health")
  public String health() { return "ok"; }
}

/**
 * The enforcement module's implementation of the shared EnforcementDecisionService.
 * runId is the id of an enforcement run (the runId returned by POST /api/enforcement/pipeline-events).
 */
@Service
class EnforcementDecisionServiceImpl implements EnforcementDecisionService {
  private final EnforcementRepository repository;

  EnforcementDecisionServiceImpl(EnforcementRepository repository) { this.repository = repository; }

  @Override
  public GovernanceDecisionDto decide(String runId) {
    long id;
    try { id = Long.parseLong(runId == null ? "" : runId.trim()); }
    catch (NumberFormatException e) { return new GovernanceDecisionDto(String.valueOf(runId), "UNKNOWN", "runId is not a number"); }
    return repository.findRun(id).map(EnforcementDecisionServiceImpl::toDto)
        .orElseGet(() -> new GovernanceDecisionDto(runId, "UNKNOWN", "no such run"));
  }

  /** decision = where the run stands (PASSED, HELD, APPROVED_EXCEPTION, ...); reason = why, in short. */
  static GovernanceDecisionDto toDto(PipelineRun r) {
    String reason;
    if (r.getDecision() != null) {
      reason = r.getDecision().action().wire() + " by " + r.getDecision().reviewer()
          + (r.getDecision().justification() == null ? "" : ": " + r.getDecision().justification().wire());
    } else if (r.getHoldReason() != null) {
      reason = "held: " + r.getHoldReason().name().toLowerCase().replace('_', '-');
    } else {
      reason = r.getClassification().label();
    }
    return new GovernanceDecisionDto(String.valueOf(r.getId()), r.getState().name(), reason);
  }
}