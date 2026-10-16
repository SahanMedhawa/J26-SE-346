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
    m.put("prNumber", r.getPrNumber());
    m.put("commitSha", r.getCommitSha());
    m.put("projectId", r.getProjectId());
    m.put("classification", r.getClassification().label());
    m.put("state", r.getState().wire());
    m.put("holdReason", r.getHoldReason() == null ? null : r.getHoldReason().name().toLowerCase().replace('_', '-'));
    m.put("files", r.getChangedFiles().size());
    m.put("violations", violations(r.getViolations()));
    m.put("decision", r.getDecision() == null ? null : Map.of(
        "action", r.getDecision().action().wire(), "reviewer", r.getDecision().reviewer(),
        "justification", String.valueOf(r.getDecision().justification()), "at", r.getDecision().at().toString()));
    return m;
  }

  static Map<String, Object> violation(Violation v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ruleId", v.ruleId()); m.put("severity", v.severity()); m.put("file", v.file());
    m.put("line", v.line()); m.put("message", v.message()); m.put("tag", v.tag());
    return m;
  }

  static List<Map<String, Object>> violations(List<Violation> vs) { return vs.stream().map(RunView::violation).toList(); }
}