package akegp.shared.api;

/** Creates an immutable conformance graph snapshot for a pull request commit. */
public interface ConformanceGraphScanService {
  void scanPullRequest(String projectId, String repositoryUrl, int prNumber, String commitSha);
}
