package akegp.enforcement.github;

/** Reads a pull request as it is on GitHub right now. Used to verify a fix against the code that was really pushed. */
public interface PullRequestSource {
  /**
   * @param repo "owner/name"
   * @throws akegp.enforcement.service.EnforcementException with a message safe to show to the reviewer
   */
  PullRequestSnapshot fetch(String repo, int prNumber);
}
