package akegp.analytics.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable historical view read in one database snapshot. */
public record AnalyticsRun(UUID id, UUID projectId, String streamKey, String externalRunId,
    long sequence, Instant analyzedAt, String repositoryId, String branchRef, String provenance,
    String dataset, String commitRef, String analysisStatus, String pipelineOutcome,
    String gateOutcome, String ruleSetVersion, String detectorVersion, String mappingVersion,
    String scopeVersion, String identityVersion, List<String> scope, Long loc,
    Map<String, Finding> findings) {
  public AnalyticsRun { scope = List.copyOf(scope); findings = Map.copyOf(findings); }
  public record Finding(String identity, String ruleKey, String ruleName, String entityType,
      String entityKey, String entityName, int severity, String location, String message) {}
  public boolean complete() { return "COMPLETE".equals(analysisStatus); }
  public boolean compatible(AnalyticsRun other) {
    return projectId.equals(other.projectId) && streamKey.equals(other.streamKey)
        && ruleSetVersion.equals(other.ruleSetVersion) && detectorVersion.equals(other.detectorVersion)
        && mappingVersion.equals(other.mappingVersion) && scopeVersion.equals(other.scopeVersion)
        && identityVersion.equals(other.identityVersion)
        && scope.stream().sorted().toList().equals(other.scope.stream().sorted().toList());
  }
  public String label() { return "DEMO".equals(provenance) ? "Demo snapshot - not upstream analysis" : "Upstream analysis"; }
}
