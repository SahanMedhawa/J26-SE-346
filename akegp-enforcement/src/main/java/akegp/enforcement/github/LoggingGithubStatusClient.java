package akegp.enforcement.github;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dry-run client used when no GitHub token is configured: statuses are only logged. */
public class LoggingGithubStatusClient implements GithubStatusClient {
  private static final System.Logger LOG = System.getLogger(LoggingGithubStatusClient.class.getName());
  private final List<String> sent = Collections.synchronizedList(new ArrayList<>());

  @Override
  public void setStatus(String repo, String sha, StatusState state, String description) {
    String line = repo + "@" + sha.substring(0, Math.min(7, sha.length())) + " -> " + state.wire() + ": " + description;
    sent.add(line);
    LOG.log(System.Logger.Level.INFO, "[dry-run] GitHub status {0}", line);
  }

  public List<String> sent() { return List.copyOf(sent); }
}
