package akegp.enforcement.domain;

import java.util.List;

/** A validated "a pull request was opened or updated" event coming from the GitHub pipeline. */
public record PipelineEvent(
    String repo,             // owner/name
    String repoUrl,
    int prNumber,
    String commitSha,        // PR head: the code after the change
    String baseSha,          // commit the PR branched from: the code before the change (may be null)
    List<String> changedFiles,
    List<FileChange> changes) {

  public PipelineEvent {
    changedFiles = List.copyOf(changedFiles);
    changes = changes == null ? List.of() : List.copyOf(changes);
  }
}
