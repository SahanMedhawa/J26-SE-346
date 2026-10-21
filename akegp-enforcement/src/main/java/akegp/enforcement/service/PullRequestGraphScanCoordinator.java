package akegp.enforcement.service;

import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.policy.ProjectLinkResolver;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.shared.api.ConformanceGraphScanService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Builds graph snapshots from PR commits stored in enforcement. Graph failures never change enforcement decisions. */
@Component
public class PullRequestGraphScanCoordinator {
  private static final Logger LOG = Logger.getLogger(PullRequestGraphScanCoordinator.class.getName());
  private final EnforcementRepository repository;
  private final ConformanceGraphScanService graphScans;
  private final ProjectLinkResolver projects;

  public PullRequestGraphScanCoordinator(EnforcementRepository repository, ConformanceGraphScanService graphScans,
                                         ProjectLinkResolver projects) {
    this.repository = repository;
    this.graphScans = graphScans;
    this.projects = projects;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void scanExistingPullRequests() {
    repository.latestPerPr().forEach(this::scan);
  }

  public void scan(PipelineRun run) {
    if (run.getClassification() == null || !run.getClassification().needsScan()) return;
    String projectId = projects.projectIdFor(run.getRepo()).orElse(run.getProjectId());
    if (projectId == null || projectId.isBlank()) {
      LOG.warning("Skipping graph scan for " + run.getRepo() + " PR #" + run.getPrNumber() + ": repository is not linked to a project");
      return;
    }
    String repositoryUrl = run.getRepoUrl();
    if (repositoryUrl == null || repositoryUrl.isBlank()) repositoryUrl = projects.repositoryUrlFor(run.getRepo()).orElse(null);
    if (repositoryUrl == null || repositoryUrl.isBlank() || run.getCommitSha() == null || run.getCommitSha().isBlank()) {
      LOG.warning("Skipping graph scan for " + run.getRepo() + " PR #" + run.getPrNumber() + ": repository URL or commit SHA is missing");
      return;
    }
    try {
      graphScans.scanPullRequest(projectId, repositoryUrl, run.getPrNumber(), run.getCommitSha());
    } catch (RuntimeException error) {
      LOG.log(Level.WARNING, "Conformance graph scan failed for " + run.getRepo() + " PR #" + run.getPrNumber()
          + " at " + run.getCommitSha(), error);
    }
  }
}
