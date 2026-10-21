package akegp.enforcement.api;

import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.LineChange;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.service.EnforcementException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks the JSON sent by the GitHub pipeline and turns it into a {@link PipelineEvent}. Plain Java, no framework. */
public final class PipelineEventValidator {
  private static final Pattern REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
  private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,40}");

  private PipelineEventValidator() {}

  public static PipelineEvent parse(Map<String, Object> body) {
    if (body == null) throw bad("request body is required");
    String repo = text(body, "repo", true);
    if (!REPO.matcher(repo).matches()) throw bad("repo must look like owner/name");
    String commitSha = text(body, "commitSha", true);
    if (!SHA.matcher(commitSha).matches()) throw bad("commitSha must be a 7-40 character hex string");
    String baseSha = text(body, "baseSha", false);
    if (baseSha != null && !SHA.matcher(baseSha).matches()) throw bad("baseSha must be a 7-40 character hex string");
    Object pr = body.get("prNumber");
    if (!(pr instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 1) throw bad("prNumber must be a positive integer");

    Object files = body.get("changedFiles");
    if (!(files instanceof List<?> fl)) throw bad("changedFiles must be an array");
    List<String> changedFiles = new ArrayList<>();
    for (Object f : fl) {
      if (!(f instanceof String s) || s.isBlank()) throw bad("changedFiles must contain non-empty strings");
      changedFiles.add(s);
    }

    List<FileChange> changes = new ArrayList<>();
    Object ch = body.get("changes");
    if (ch != null) {
      if (!(ch instanceof List<?> cl)) throw bad("changes must be an array");
      for (Object c : cl) changes.add(change(c));
      List<String> paths = changes.stream().map(FileChange::path).toList();
      if (!paths.equals(changedFiles)) throw bad("changes[].path must match changedFiles in the same order");
    }
    // The detailed "changes" are optional. Without them, keep each file name as a plain "modified" entry so that the
    // file list is stored with the run (the database keeps files through the changes) and survives a re-scan.
    if (ch == null) {
      for (String path : changedFiles) changes.add(new FileChange(path, "modified", null, 0, 0, false, null, null, null));
    }
    return new PipelineEvent(repo, text(body, "repoUrl", false), n.intValue(), commitSha, baseSha, changedFiles, changes);
  }

  @SuppressWarnings("unchecked")
  private static FileChange change(Object c) {
    if (!(c instanceof Map<?, ?> m)) throw bad("each change must be an object");
    Map<String, Object> map = (Map<String, Object>) m;
    String path = text(map, "path", true);
    String status = text(map, "status", true);
    if (!FileChange.STATUSES.contains(status)) throw bad("change status must be one of " + FileChange.STATUSES);
    return new FileChange(path, status, text(map, "oldPath", false), num(map, "additions"), num(map, "deletions"),
        Boolean.TRUE.equals(map.get("binary")), lines(map.get("addedLines")), lines(map.get("removedLines")), text(map, "patch", false));
  }

  private static List<LineChange> lines(Object o) {
    if (o == null) return null;
    if (!(o instanceof List<?> l)) throw bad("addedLines/removedLines must be arrays");
    List<LineChange> out = new ArrayList<>();
    for (Object x : l) {
      if (!(x instanceof Map<?, ?> m) || !(m.get("line") instanceof Number n)) throw bad("each line entry needs {line, text}");
      out.add(new LineChange(n.intValue(), String.valueOf(m.get("text"))));
    }
    return out;
  }

  private static int num(Map<String, Object> m, String key) {
    Object v = m.get(key);
    return v instanceof Number n ? n.intValue() : 0;
  }

  private static String text(Map<String, Object> m, String key, boolean required) {
    Object v = m.get(key);
    if (v == null || (v instanceof String s && s.isBlank())) {
      if (required) throw bad(key + " is required");
      return null;
    }
    if (!(v instanceof String s)) throw bad(key + " must be a string");
    return s;
  }

  private static EnforcementException bad(String message) { return new EnforcementException(400, message); }
}