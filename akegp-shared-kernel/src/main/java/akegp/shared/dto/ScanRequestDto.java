package akegp.shared.dto;

import java.util.List;

/** Enforcement -> Conformance: "check this pull request against the rules of this project". No source code is sent. */
public record ScanRequestDto(
    String repoUrl,
    String commitSha,      // code after the change (PR head)
    String baseSha,        // code before the change; lets Conformance mark violations as new or existing (may be null)
    int prNumber,
    String projectId,      // the Rules project whose READY rules apply
    List<ChangedFileDto> changes) {}