package akegp.enforcement.github;

/**
 * Posts a commit status on a pull request's head commit. A branch protection rule that requires this status
 * is what actually stops the merge button while a PR is held.
 */
public interface GithubStatusClient {
  /** @throws GithubStatusException if GitHub could not be reached or refused the request */
  void setStatus(String repo, String sha, StatusState state, String description);
}
