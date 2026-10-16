package akegp.enforcement.domain;

import java.util.List;
import java.util.Set;

/** What happened to one file in a pull request. Mirrors the "changes" entries sent by the pipeline. */
public record FileChange(
    String path,
    String status,            // added | modified | deleted | renamed | copied
    String oldPath,           // only for renamed / copied
    int additions,
    int deletions,
    boolean binary,
    List<LineChange> addedLines,    // only when the pipeline runs with INCLUDE_LINES=true
    List<LineChange> removedLines,
    String patch) {            // only when the pipeline runs with INCLUDE_PATCH=true

  public static final Set<String> STATUSES = Set.of("added", "modified", "deleted", "renamed", "copied");

  public FileChange {
    addedLines = addedLines == null ? List.of() : List.copyOf(addedLines);
    removedLines = removedLines == null ? List.of() : List.copyOf(removedLines);
  }
}
