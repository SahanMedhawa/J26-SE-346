package akegp.enforcement.service;

import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.github.GithubStatusClient;
import akegp.enforcement.github.StatusState;
import akegp.enforcement.repository.EnforcementRepository;

/** Sets the GitHub status for a run. A GitHub outage is recorded in the audit log and does not lose the decision. */
public class RunStatusPublisher {
  private final GithubStatusClient github;
  private final EnforcementRepository repository;

  public RunStatusPublisher(GithubStatusClient github, EnforcementRepository repository) {
    this.github = github;
    this.repository = repository;
  }

  public void publish(PipelineRun run, StatusState state, String description) {
    try {
      github.setStatus(run.getRepo(), run.getCommitSha(), state, description);
    } catch (RuntimeException e) {
      repository.log("status_error", run.getRepo(), run.getPrNumber(), String.valueOf(e.getMessage()));
    }
  }
}
