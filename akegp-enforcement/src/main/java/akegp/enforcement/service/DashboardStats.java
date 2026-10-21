package akegp.enforcement.service;

import java.time.Instant;
import java.util.List;

/** Numbers and rows for the pipeline monitoring dashboard. */
public record DashboardStats(int runs, int analysisTriggered, int skipped, int violations, List<RecentRun> recent) {

  public record RecentRun(long runId, String repo, int pr, String changeType, String result, int files, Instant at) {}
}
