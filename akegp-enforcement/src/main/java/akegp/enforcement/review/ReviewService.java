package akegp.enforcement.review;

import akegp.enforcement.conformance.ConformanceScanner;
import akegp.enforcement.conformance.ScanReport;
import akegp.enforcement.conformance.ScanRequest;
import akegp.enforcement.domain.Decision;
import akegp.enforcement.domain.DecisionAction;
import akegp.enforcement.domain.Justification;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.github.StatusState;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.enforcement.service.EnforcementException;
import akegp.enforcement.service.RunStatusPublisher;
import java.time.Instant;
import java.util.List;

/** A human reviewer's decision on a held pull request. The only way a held PR is released or failed. */
public class ReviewService {
  private final EnforcementRepository repository;
  private final ConformanceScanner scanner;
  private final RunStatusPublisher status;

  public ReviewService(EnforcementRepository repository, ConformanceScanner scanner, RunStatusPublisher status) {
    this.repository = repository;
    this.scanner = scanner;
    this.status = status;
  }

  public PipelineRun decide(String repo, int prNumber, String actionWire, String reviewer,
                            String justificationWire, String comment) {
    PipelineRun run = repository.latestRun(repo, prNumber)
        .orElseThrow(() -> new EnforcementException(404, "PR not found"));
    if (run.getState() != RunState.HELD)
      throw new EnforcementException(409, "PR is " + run.getState().wire() + ", not held");
    if (reviewer == null || reviewer.isBlank()) throw new EnforcementException(400, "reviewer is required");
    DecisionAction action = DecisionAction.fromWire(actionWire).orElseThrow(() -> new EnforcementException(400,
        "action must be apply_fix | request_changes | approve_exception | reject"));

    Justification justification = null;
    switch (action) {
      case APPROVE_EXCEPTION -> {
        justification = Justification.fromWire(justificationWire).orElseThrow(() -> new EnforcementException(400,
            "justification must be one of: " + Justification.allowedValues()));
        run.setState(RunState.APPROVED_EXCEPTION);
        status.publish(run, StatusState.SUCCESS, "Exception approved by " + reviewer + ": " + justification.wire());
      }
      case APPLY_FIX -> {
        // After a fix is applied the code changed, so Component 2 must re-verify before anything is released.
        ScanReport report = rescan(run);
        if (report != null && report.violations().isEmpty()) {
          run.setViolations(List.of());
          run.setState(RunState.PASSED);
          status.publish(run, StatusState.SUCCESS, "Fix applied by " + reviewer + ", re-scan clean");
        } else {
          run.setState(RunState.HELD);
          status.publish(run, StatusState.PENDING, "Fix applied but re-scan not clean or unavailable, still held");
        }
      }
      case REQUEST_CHANGES -> {
        run.setState(RunState.CHANGES_REQUESTED);
        status.publish(run, StatusState.FAILURE, "Changes requested by " + reviewer);
      }
      case REJECT -> {
        run.setState(RunState.REJECTED);
        status.publish(run, StatusState.FAILURE, "Rejected by " + reviewer);
      }
    }
    run.setDecision(new Decision(action, reviewer, justification, comment, Instant.now()));
    repository.updateRun(run);
    repository.log("decision", run.getRepo(), run.getPrNumber(), "action=" + action.wire() + " reviewer=" + reviewer
        + (justification != null ? " justification=" + justification.wire() : "")
        + " resultingState=" + run.getState().wire());
    return run;
  }

  private ScanReport rescan(PipelineRun run) {
    try {
      return scanner.scan(new ScanRequest(run.getRepoUrl(), run.getCommitSha(), run.getBaseSha(), run.getPrNumber(),
          run.getProjectId(), run.getChangedFiles(), run.getChanges()));
    } catch (RuntimeException e) {
      return null;
    }
  }
}
