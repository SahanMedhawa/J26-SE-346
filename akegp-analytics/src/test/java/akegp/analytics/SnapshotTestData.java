package akegp.analytics;

import akegp.analytics.ingestion.ModelsDemoFixture;
import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalysisSnapshot.Finding;
import java.util.List;
import java.util.UUID;

final class SnapshotTestData {
  private SnapshotTestData() {}
  static AnalysisSnapshot snapshot(UUID project, String branch, String run, long sequence,
      AnalysisSnapshot.AnalysisStatus status, AnalysisSnapshot.ResultMode mode, List<Finding> findings) {
    var b = ModelsDemoFixture.snapshots().getFirst();
    return new AnalysisSnapshot(1, project, b.repositoryId(), branch, run, sequence,
        b.analyzedAt(), b.commitRef(), b.ruleSetVersion(), b.detectorVersion(), b.mappingVersion(),
        b.scopeVersion(), b.analyzedScope(), status, mode, "FAILED", "FAILED", null, findings);
  }
  static AnalysisSnapshot snapshot(String run, long sequence, List<Finding> findings) {
    return snapshot(ModelsDemoFixture.PROJECT_ID, "refs/heads/demo", run, sequence,
        AnalysisSnapshot.AnalysisStatus.COMPLETE, AnalysisSnapshot.ResultMode.SNAPSHOT, findings);
  }
  static Finding change(Finding f, String upstreamId, int severity, String source, String location) {
    return new Finding(upstreamId, f.ruleKey(), f.ruleName(), f.entityType(), f.entityKey(), f.entityName(),
        f.findingKind(), source, f.targetKey(), f.relation(), severity, location, f.message());
  }
}
