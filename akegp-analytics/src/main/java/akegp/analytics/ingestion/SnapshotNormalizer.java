package akegp.analytics.ingestion;

import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalysisSnapshot.Finding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import static akegp.analytics.ingestion.IngestionException.Code.*;

public final class SnapshotNormalizer {
  public CanonicalSnapshot normalize(AnalysisSnapshot s, AdapterSource source) {
    require(s != null, "Snapshot required");
    require(s.contractVersion() == 1, "Unsupported contract version");
    require(s.projectId() != null, "Project required");
    for (String value : new String[] {s.repositoryId(), s.branchRef(), s.externalRunId(),
        s.commitRef(), s.ruleSetVersion(), s.detectorVersion(), s.mappingVersion(), s.scopeVersion()}) {
      required(value);
    }
    require(s.sequence() >= 0 && s.analyzedAt() != null, "Order and timestamp required");
    require(s.resultMode() == AnalysisSnapshot.ResultMode.SNAPSHOT, "Only full SNAPSHOT input supported");
    require(s.analysisStatus() != null, "Explicit analysis completeness required");
    require(s.violations() != null, "Explicit violations array required");
    List<String> scope = scope(s.analyzedScope());
    if (s.loc() != null) {
      require(s.loc().lines() >= 0, "LOC cannot be negative");
      require(scope(s.loc().scope()).equals(scope), "LOC must cover exactly the analyzed scope");
    }
    // Identity namespace excludes branch, run, commit, severity and evidence.
    String namespace = tuple("identity-v1", s.projectId(), s.repositoryId(),
        source.system(), source.provenance(), source.dataset());
    String stream = tuple("stream-v1", namespace, s.branchRef());
    var findings = new TreeMap<String, CanonicalSnapshot.Occurrence>();
    for (Finding f : s.violations()) {
      require(f != null, "Null finding");
      for (String value : new String[] {f.ruleKey(), f.ruleName(), f.entityType(), f.entityKey(),
          f.entityName(), f.findingKind(), f.sourceKey()}) required(value);
      require(scope.contains(tuple(f.entityType(), f.entityKey())), "Finding entity outside declared scope");
      require(f.severity() >= 1 && f.severity() <= 3, "Severity must be 1..3");
      require((f.targetKey() == null) == (f.relation() == null), "Target and relation must be supplied together");
      if (f.targetKey() != null) { required(f.targetKey()); required(f.relation()); }
      String structure = tuple("structure-v1", f.ruleKey(), f.entityType(), f.entityKey(),
          f.findingKind(), f.sourceKey(), f.targetKey(), f.relation());
      String key;
      if (f.stableUpstreamId() != null) {
        required(f.stableUpstreamId());
        require(source.verifiedStableUpstreamIds(), "Adapter has not verified upstream identity stability");
        key = hash(tuple(namespace, "upstream", f.stableUpstreamId()));
      } else key = hash(tuple(namespace, "structural", structure));
      var occurrence = new CanonicalSnapshot.Occurrence(key, structure, f);
      var previous = findings.putIfAbsent(key, occurrence);
      if (previous != null && !previous.equals(occurrence)) {
        throw new IngestionException(CONFLICT, "Conflicting duplicate finding");
      }
    }
    StringBuilder payload = new StringBuilder(tuple("payload-v1", stream, s.contractVersion(),
        s.externalRunId(), s.sequence(), s.analyzedAt(), s.commitRef(), s.ruleSetVersion(),
        s.detectorVersion(), s.mappingVersion(), s.scopeVersion(), tuple(scope.toArray()),
        s.analysisStatus(), s.resultMode(), s.pipelineOutcome(), s.gateOutcome(),
        s.loc() == null ? null : s.loc().lines()));
    for (var o : findings.values()) {
      Finding f = o.finding();
      payload.append(tuple(o.identityKey(), o.structure(), f.stableUpstreamId(), f.ruleName(),
          f.entityName(), f.severity(), f.displayLocation(), f.message()));
    }
    return new CanonicalSnapshot(s, source, hash(stream), stream, namespace,
        hash(payload.toString()), payload.toString(), List.copyOf(findings.values()));
  }

  /** Scope entries use the same unambiguous entity-type/key encoding as identity. */
  public static String scopeEntity(String type, String key) { return tuple(type, key); }
  private static List<String> scope(List<String> input) {
    require(input != null && !input.isEmpty(), "Explicit analyzed scope required");
    input.forEach(SnapshotNormalizer::required);
    return input.stream().distinct().sorted().toList();
  }
  private static void required(String value) {
    require(value != null && !value.isBlank() && value.equals(value.strip())
        && value.length() <= 4096 && value.indexOf('\0') < 0, "Nonblank canonical field required (max 4096 chars)");
  }
  private static void require(boolean condition, String message) {
    if (!condition) throw new IngestionException(INVALID, message);
  }
  public static String tuple(Object... fields) {
    var result = new StringBuilder();
    for (Object field : fields) {
      if (field == null) result.append("-1:");
      else { String value = field.toString(); result.append(value.length()).append(':').append(value); }
    }
    return result.toString();
  }
  public static String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
