package akegp.enforcement.service;

import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.repository.EnforcementRepository;
import java.util.List;

public class DashboardStatsService {
  static final int RECENT_LIMIT = 20;
  private final EnforcementRepository repository;

  public DashboardStatsService(EnforcementRepository repository) { this.repository = repository; }

  public DashboardStats stats() { return build(repository.listRuns(500)); }

  /** @param runs newest first */
  static DashboardStats build(List<PipelineRun> runs) {
    int analysed = (int) runs.stream().filter(r -> r.getClassification().needsScan()).count();
    int violations = runs.stream().mapToInt(r -> r.getViolations().size()).sum();
    List<DashboardStats.RecentRun> recent = runs.stream().limit(RECENT_LIMIT)
        .map(r -> new DashboardStats.RecentRun(r.getId(), r.getRepo(), r.getPrNumber(),
            r.getClassification().label(), resultLabel(r), r.getChangedFiles().size(), r.getCreatedAt()))
        .toList();
    return new DashboardStats(runs.size(), analysed, runs.size() - analysed, violations, recent);
  }

  static String resultLabel(PipelineRun run) {
    if (!run.getClassification().needsScan()) return "Skipped"; // docs/config: no analysis needed
    return switch (run.getState()) {
      case PASSED -> "Passed";
      case HELD -> run.getHoldReason() == akegp.enforcement.domain.HoldReason.VIOLATIONS ? "Violation found" : "Held";
      case APPROVED_EXCEPTION -> "Exception approved";
      case CHANGES_REQUESTED -> "Changes requested";
      case REJECTED -> "Rejected";
      case RECEIVED -> "In progress";
    };
  }
}
