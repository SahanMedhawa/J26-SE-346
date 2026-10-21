package akegp.shared.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Version 1 full-snapshot input. Provenance is deliberately absent from caller input. */
public record AnalysisSnapshot(
    int contractVersion, UUID projectId, String repositoryId, String branchRef,
    String externalRunId, long sequence, Instant analyzedAt, String commitRef,
    String ruleSetVersion, String detectorVersion, String mappingVersion,
    String scopeVersion, List<String> analyzedScope, AnalysisStatus analysisStatus,
    ResultMode resultMode, String pipelineOutcome, String gateOutcome,
    ScopedLoc loc, List<Finding> violations) {

  public AnalysisSnapshot {
    analyzedScope = analyzedScope == null ? null : List.copyOf(analyzedScope);
    violations = violations == null ? null : List.copyOf(violations);
  }

  public enum AnalysisStatus { PENDING, COMPLETE, PARTIAL, FAILED, SKIPPED }
  public enum ResultMode { SNAPSHOT, DELTA }
  public record ScopedLoc(long lines, List<String> scope) {
    public ScopedLoc { scope = scope == null ? null : List.copyOf(scope); }
  }
  /** stableUpstreamId may be used only by an adapter with verified identity guarantees. */
  public record Finding(String stableUpstreamId, String ruleKey, String ruleName,
      String entityType, String entityKey, String entityName, String findingKind,
      String sourceKey, String targetKey, String relation, int severity,
      String displayLocation, String message) {}
}
