package akegp.enforcement.conformance;

import akegp.enforcement.domain.FileChange;
import java.util.List;

/** What Component 3 asks Component 2 (conformance) to analyse. No source code is sent, only what changed. */
public record ScanRequest(
    String repoUrl,
    String commitSha,        // code after the change
    String baseSha,          // code before the change; lets Component 2 tell new violations from existing ones
    int prNumber,
    String projectId,        // the Component 1 project whose READY rules apply
    List<String> changedFiles,
    List<FileChange> changes) {}
