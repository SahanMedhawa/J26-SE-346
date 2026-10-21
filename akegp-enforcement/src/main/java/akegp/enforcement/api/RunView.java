package akegp.enforcement.api;

import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.Violation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** How a run is shown in JSON responses. */
final class RunView {
  private RunView() {}

  static Map<String, Object> of(PipelineRun r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("runId", r.getId());
    m.put("repo", r.getRepo());
    m.put("repoUrl", r.getRepoUrl());
    m.put("prNumber", r.getPrNumber());
    m.put("commitSha", r.getCommitSha());
    m.put("projectId", r.getProjectId());
    m.put("classification", r.getClassification().label());
    m.put("state", r.getState().wire());
    m.put("holdReason", r.getHoldReason() == null ? null : r.getHoldReason().name().toLowerCase().replace('_', '-'));
    m.put("files", r.getChangedFiles().size());
    m.put("changedFiles", r.getChangedFiles().stream().limit(10).toList());   // names only, to explain the result
    m.put("violations", violations(r.getViolations()));
    m.put("decision", decision(r));
    return m;
  }

  /** The reviewer's decision, or null. justification and comment are null when not given (never the text "null"). */
  static Map<String, Object> decision(PipelineRun r) {
    if (r.getDecision() == null) return null;
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("action", r.getDecision().action().wire());
    d.put("reviewer", r.getDecision().reviewer());
    d.put("justification", r.getDecision().justification() == null ? null : r.getDecision().justification().wire());
    d.put("comment", r.getDecision().comment());
    d.put("at", r.getDecision().at().toString());
    return d;
  }

  static Map<String, Object> violation(Violation v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ruleId", v.ruleId()); m.put("severity", v.severity()); m.put("file", v.file());
    m.put("line", v.line()); m.put("message", v.message()); m.put("tag", v.tag());
    return m;
  }

  static List<Map<String, Object>> violations(List<Violation> vs) { return vs.stream().map(RunView::violation).toList(); }

  /** Every scan of one pull request, newest first, each with its time. The caller passes the runs newest first. */
  static List<Map<String, Object>> history(List<PipelineRun> runsNewestFirst, String repo, int prNumber) {
    return runsNewestFirst.stream()
        .filter(r -> r.getRepo().equals(repo) && r.getPrNumber() == prNumber)
        .map(r -> {
          Map<String, Object> m = of(r);
          m.put("at", r.getCreatedAt().toString());
          return m;
        })
        .toList();
  }
}
