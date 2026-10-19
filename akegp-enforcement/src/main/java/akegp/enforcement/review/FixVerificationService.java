package akegp.enforcement.review;

import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.github.PullRequestSnapshot;
import akegp.enforcement.github.PullRequestSource;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.enforcement.service.EnforcementException;
import akegp.enforcement.service.EnforcementService;
import java.util.List;

/**
 * "I pushed a fix": checks whether the fix really worked. It never trusts what was stored when the pull request was
 * first scanned. It reads the pull request from GitHub as it is now and sends that through the normal pipeline, which
 * creates a new run: passed when the code is clean, held again (with the violations that remain) when it is not.
 * Without a new commit there is nothing to verify, and the pull request stays held.
 */
public class FixVerificationService {
  private final EnforcementRepository repository;
  private final PullRequestSource source;
  private final EnforcementService enforcement;

  public FixVerificationService(EnforcementRepository repository, PullRequestSource source, EnforcementService enforcement) {
    this.repository = repository;
    this.source = source;
    this.enforcement = enforcement;
  }

  /** @return the run for the pull request's current commit (a new run, or the existing one when that commit was already scanned) */
  public PipelineRun verify(String repo, int prNumber, String reviewer) {
    PipelineRun held = repository.latestRun(repo, prNumber).orElseThrow(() -> new EnforcementException(404, "PR not found"));
    if (held.getState() != RunState.HELD)
      throw new EnforcementException(409, "PR is " + held.getState().wire() + ", not held");
    if (reviewer == null || reviewer.isBlank()) throw new EnforcementException(400, "reviewer is required");

    PullRequestSnapshot now = source.fetch(repo, prNumber);
    if (now.headSha().equals(held.getCommitSha()) && !held.getViolations().isEmpty()) {
      if (fixWasPushedBefore(held)) {
        // The reviewer's fix commit was already scanned (by the pipeline) and still breaks the rules: nothing new to scan.
        repository.log("decision", repo, prNumber, "action=apply_fix reviewer=" + reviewer + " previousRun=" + held.getId()
            + " newRun=" + held.getId() + " commit=" + shortSha(now.headSha()) + " resultingState=held (already scanned)");
        return held;
      }
      repository.log("fix_not_found", repo, prNumber, "reviewer=" + reviewer + " no new commit since " + shortSha(held.getCommitSha()));
      throw new EnforcementException(409, "No new commit has been pushed since the scan (commit " + shortSha(held.getCommitSha())
          + "). Push your fix to the pull request first, then verify again.");
    }

    List<String> files = now.changes().stream().map(c -> c.path()).toList();
    PipelineEvent event = new PipelineEvent(repo, now.repoUrl() != null ? now.repoUrl() : held.getRepoUrl(), prNumber,
        now.headSha(), now.baseSha() != null ? now.baseSha() : held.getBaseSha(), files, now.changes());
    PipelineRun result = enforcement.handle(event);
    repository.log("decision", repo, prNumber, "action=apply_fix reviewer=" + reviewer + " previousRun=" + held.getId()
        + " newRun=" + result.getId() + " commit=" + shortSha(now.headSha()) + " resultingState=" + result.getState().wire());
    return result;
  }

  /** True when an earlier run of this pull request was held on a different commit, so a later commit was pushed after it. */
  private boolean fixWasPushedBefore(PipelineRun current) {
    return repository.listRuns(5000).stream().anyMatch(r -> r.getRepo().equals(current.getRepo())
        && r.getPrNumber() == current.getPrNumber() && r.getId() != current.getId()
        && r.getState() == RunState.HELD && r.getCommitSha() != null && !r.getCommitSha().equals(current.getCommitSha()));
  }

  private static String shortSha(String sha) { return sha == null ? "?" : sha.substring(0, Math.min(7, sha.length())); }
}
