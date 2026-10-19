package akegp.enforcement.github;

import akegp.enforcement.domain.FileChange;
import java.util.List;

/** The current state of a pull request on GitHub: its newest commit and what it changes against the base. */
public record PullRequestSnapshot(String repoUrl, String headSha, String baseSha, List<FileChange> changes) {
  public PullRequestSnapshot { changes = changes == null ? List.of() : List.copyOf(changes); }
}
