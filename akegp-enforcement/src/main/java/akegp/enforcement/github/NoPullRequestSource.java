package akegp.enforcement.github;

import akegp.enforcement.service.EnforcementException;

/** Used when no GitHub token is configured: the pull request cannot be re-read, so a fix cannot be verified. */
public class NoPullRequestSource implements PullRequestSource {
  @Override
  public PullRequestSnapshot fetch(String repo, int prNumber) {
    throw new EnforcementException(503, "GitHub token is not configured, so the pull request cannot be re-read. "
        + "Set ENFORCEMENT_GITHUB_TOKEN and restart the app.");
  }
}
