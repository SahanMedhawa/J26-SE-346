package akegp.enforcement.service;

import akegp.enforcement.conformance.ConformanceScanner;
import akegp.enforcement.conformance.ScanReport;
import akegp.enforcement.conformance.ScanRequest;
import akegp.enforcement.domain.ChangeType;
import akegp.enforcement.domain.HoldReason;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.github.StatusState;
import akegp.enforcement.policy.ProjectLinkResolver;
import akegp.enforcement.repository.EnforcementRepository;
import java.util.Optional;

/**
 * Entry point of Component 3: a pull request event arrives from the pipeline, and the PR is either passed or held.
 * The system never decides to merge or reject anything; only a human reviewer does (see ReviewService).
 */
public class EnforcementService {
  private final EnforcementRepository repository;
  private final ChangeClassifier classifier;
  private final ProjectLinkResolver projects;
  private final ConformanceScanner scanner;
  private final RunStatusPublisher status;

  public EnforcementService(EnforcementRepository repository, ChangeClassifier classifier, ProjectLinkResolver projects,
                            ConformanceScanner scanner, RunStatusPublisher status) {
    this.repository = repository;
    this.classifier = classifier;
    this.projects = projects;
    this.scanner = scanner;
    this.status = status;
  }

  public PipelineRun handle(PipelineEvent event) {
    ChangeType type = classifier.classify(event.changedFiles());   // re-checked here, never trusted from the client
    Optional<String> projectId = projects.projectIdFor(event.repo());
    PipelineRun run = repository.createRun(PipelineRun.from(event, type, projectId.orElse(null)));
    repository.log("pr_received", run.getRepo(), run.getPrNumber(),
        "sha=" + run.getCommitSha() + " classification=" + type.wire());

    if (!type.needsScan()) {
      run.setState(RunState.PASSED);
      repository.log("pr_passed", run.getRepo(), run.getPrNumber(), "reason=" + type.wire() + " only, scan skipped");
      repository.updateRun(run);
      status.publish(run, StatusState.SUCCESS, "No scan needed: " + type.wire() + " change");
      return run;
    }

    if (projectId.isEmpty()) {
      // No rules can be chosen for this repo. Fail closed so the setup gap is visible.
      return hold(run, HoldReason.PROJECT_NOT_LINKED, "reason=project-not-linked",
          "Held: this repository is not linked to a project, so no rules can be applied");
    }

    status.publish(run, StatusState.PENDING, "Architecture scan in progress");
    ScanReport report;
    try {
      report = scanner.scan(new ScanRequest(run.getRepoUrl(), run.getCommitSha(), run.getBaseSha(), run.getPrNumber(),
          run.getProjectId(), run.getChangedFiles(), run.getChanges()));
    } catch (RuntimeException e) {
      // Fail closed: if we cannot verify, we do not let it through silently.
      return hold(run, HoldReason.SCAN_UNAVAILABLE, "reason=scan-unavailable: " + e.getMessage(),
          "Held: architecture scan unavailable, needs manual review");
    }

    run.setViolations(report.violations());
    if (report.violations().isEmpty()) {
      run.setState(RunState.PASSED);
      repository.log("pr_passed", run.getRepo(), run.getPrNumber(), "reason=no violations");
      repository.updateRun(run);
      status.publish(run, StatusState.SUCCESS, "No architecture violations");
      return run;
    }
    int n = report.violations().size();
    return hold(run, HoldReason.VIOLATIONS, "reason=violations count=" + n,
        "Held: " + n + " architecture violation(s) awaiting review");
  }

  private PipelineRun hold(PipelineRun run, HoldReason reason, String auditDetail, String githubDescription) {
    run.setState(RunState.HELD);
    run.setHoldReason(reason);
    repository.log("pr_held", run.getRepo(), run.getPrNumber(), auditDetail);
    repository.updateRun(run);
    status.publish(run, StatusState.PENDING, githubDescription);
    return run;
  }
}
