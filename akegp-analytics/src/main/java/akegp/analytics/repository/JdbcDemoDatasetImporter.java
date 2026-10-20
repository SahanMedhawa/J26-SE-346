package akegp.analytics.repository;

import akegp.analytics.ingestion.*;
import akegp.shared.analytics.AnalyticsIngestion.Receipt;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import static akegp.analytics.ingestion.IngestionException.Code.*;

/** Explicit DEMO-only bulk import. New runs commit together; existing immutable prefixes are replayed. */
public final class JdbcDemoDatasetImporter {
  private final DataSource database;
  public JdbcDemoDatasetImporter(DataSource database) { this.database = database; }
  public List<Receipt> append(List<CanonicalSnapshot> input) {
    if (input.isEmpty() || input.size() > 1000) throw new IllegalArgumentException("Demo dataset requires 1..1000 snapshots");
    var first = input.getFirst();
    if (first.source().provenance() != AdapterSource.Provenance.DEMO || input.stream().anyMatch(s ->
        !s.source().equals(first.source()) || !s.input().projectId().equals(first.input().projectId())))
      throw new IllegalArgumentException("One project and server-assigned DEMO dataset required");
    var ordered = input.stream().sorted(Comparator.comparing(CanonicalSnapshot::streamKey).thenComparingLong(s -> s.input().sequence())).toList();
    if (input.stream().map(this::key).distinct().count() != input.size()) throw new IllegalArgumentException("Duplicate external run IDs in demo input");
    try (var connection = database.getConnection()) {
      connection.setAutoCommit(false);
      try {
        var receipts = append(connection, ordered);
        connection.commit();
        return input.stream().map(s -> receipts.get(key(s))).toList();
      } catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
    } catch (SQLException e) { throw new IllegalStateException("Analytics demo import failed (SQL state " + e.getSQLState() + ")"); }
  }
  private Map<String, Receipt> append(Connection c, List<CanonicalSnapshot> snapshots) throws SQLException {
    var streams = new TreeMap<String, CanonicalSnapshot>();
    snapshots.forEach(s -> streams.putIfAbsent(s.streamKey(), s));
    var streamRows = new ArrayList<List<Object>>();
    for (var s : streams.values()) streamRows.add(row(s.streamKey(), s.streamTuple(), s.input().projectId(), s.input().repositoryId(),
        s.input().branchRef(), s.source().system(), s.source().provenance().name(), s.source().dataset()));
    insert(c, "streams", "stream_key,stream_tuple,project_id,repository_id,branch_ref,source_system,provenance,dataset", streamRows, " ON CONFLICT (stream_key) DO NOTHING");
    var streamKeys = c.createArrayOf("text", streams.keySet().toArray(String[]::new));
    Map<String, Receipt> receipts = new HashMap<>();
    Map<String, String> payloads = new HashMap<>();
    Map<String, Long> maxSequences = new HashMap<>();
    try {
      // One deterministic stream-lock order also serializes concurrent imports of the same dataset.
      try (var q = c.prepareStatement("SELECT stream_key,stream_tuple FROM analytics_private.streams WHERE stream_key = ANY (?) ORDER BY stream_key FOR UPDATE")) {
        q.setArray(1, streamKeys);
        try (var rs = q.executeQuery()) {
          int found = 0;
          while (rs.next()) {
            var expected = streams.get(rs.getString(1));
            if (expected == null || !expected.streamTuple().equals(rs.getString(2))) throw new IngestionException(CONFLICT, "Stream identity collision");
            found++;
          }
          if (found != streams.size()) throw new IngestionException(CONFLICT, "Missing demo streams");
        }
      }
      try (var q = c.prepareStatement("SELECT id,stream_key,external_run_id,analysis_sequence,payload_hash,canonical_payload,occurrence_count FROM analytics_private.runs WHERE stream_key = ANY (?)")) {
        q.setArray(1, streamKeys);
        try (var rs = q.executeQuery()) {
          while (rs.next()) {
            String key = rs.getString("stream_key") + ":" + rs.getString("external_run_id");
            var source = streams.get(rs.getString("stream_key")).source();
            receipts.put(key, new Receipt(rs.getObject("id", UUID.class), true, rs.getInt("occurrence_count"), "DEMO", source.label()));
            payloads.put(key, rs.getString("canonical_payload"));
            maxSequences.merge(rs.getString("stream_key"), rs.getLong("analysis_sequence"), Math::max);
          }
        }
      }
    } finally { streamKeys.free(); }
    var fresh = new ArrayList<CanonicalSnapshot>();
    for (var s : snapshots) {
      String key = key(s);
      if (receipts.containsKey(key)) {
        if (!s.payload().equals(payloads.get(key))) throw new IngestionException(CONFLICT, "External run ID reused with different content");
        continue;
      }
      Long previous = maxSequences.get(s.streamKey());
      if (previous != null && s.input().sequence() <= previous) throw new IngestionException(OUT_OF_ORDER, "Cannot insert into an immutable historical prefix");
      maxSequences.put(s.streamKey(), s.input().sequence()); fresh.add(s);
      receipts.put(key, new Receipt(UUID.randomUUID(), false, s.occurrences().size(), "DEMO", s.source().label()));
    }
    if (fresh.isEmpty()) return receipts;
    var identityRows = new TreeMap<String, List<Object>>();
    var identityStructures = new HashMap<String, String>();
    var identityNamespaces = new HashMap<String, String>();
    for (var s : fresh) for (var o : s.occurrences()) {
      var f = o.finding();
      String previous = identityStructures.putIfAbsent(o.identityKey(), o.structure());
      String namespace = identityNamespaces.putIfAbsent(o.identityKey(), s.identityNamespace());
      if (previous != null && (!previous.equals(o.structure()) || !namespace.equals(s.identityNamespace())))
        throw new IngestionException(CONFLICT, "Conflicting identity within dataset");
      identityRows.putIfAbsent(o.identityKey(), row(o.identityKey(), s.identityNamespace(), o.structure(), f.stableUpstreamId(), f.ruleKey(),
          f.entityType(), f.entityKey(), f.findingKind(), f.sourceKey(), f.targetKey(), f.relation()));
    }
    insert(c, "identities", "identity_key,identity_namespace,structural_tuple,upstream_id,rule_key,entity_type,entity_key,finding_kind,source_key,target_key,relation",
        new ArrayList<>(identityRows.values()), " ON CONFLICT (identity_key) DO NOTHING");
    if (!identityRows.isEmpty()) {
      var identities = c.createArrayOf("text", identityRows.keySet().toArray(String[]::new));
      try (var q = c.prepareStatement("SELECT identity_key,identity_namespace,structural_tuple FROM analytics_private.identities WHERE identity_key = ANY (?)")) {
        q.setArray(1, identities);
        try (var rs = q.executeQuery()) {
          while (rs.next()) {
            if (!rs.getString(2).equals(identityNamespaces.get(rs.getString(1))) || !rs.getString(3).equals(identityStructures.remove(rs.getString(1))))
              throw new IngestionException(CONFLICT, "Stable identity reused for conflicting structure");
          }
        }
        if (!identityStructures.isEmpty()) throw new IngestionException(CONFLICT, "Missing stable identities");
      } finally { identities.free(); }
    }
    var runRows = new ArrayList<List<Object>>();
    var occurrenceRows = new ArrayList<List<Object>>();
    var arrays = new ArrayList<java.sql.Array>();
    try {
      for (var n : fresh) {
        var s = n.input(); var id = receipts.get(key(n)).runId();
        var scope = c.createArrayOf("text", s.analyzedScope().stream().distinct().sorted().toArray(String[]::new)); arrays.add(scope);
        runRows.add(row(id, n.streamKey(), s.externalRunId(), s.sequence(), Timestamp.from(s.analyzedAt()), s.commitRef(), s.contractVersion(),
            "identity-v1", s.ruleSetVersion(), s.detectorVersion(), s.mappingVersion(), s.scopeVersion(), scope, s.analysisStatus().name(),
            s.resultMode().name(), s.pipelineOutcome(), s.gateOutcome(), s.loc() == null ? null : s.loc().lines(), n.payloadHash(), n.payload(), n.occurrences().size()));
        for (var o : n.occurrences()) { var f = o.finding(); occurrenceRows.add(row(id, o.identityKey(), f.ruleKey(), f.ruleName(), f.entityType(),
            f.entityKey(), f.entityName(), f.severity(), f.displayLocation(), f.message())); }
      }
      insert(c, "runs", "id,stream_key,external_run_id,analysis_sequence,analyzed_at,commit_ref,contract_version,identity_version,rule_set_version,detector_version,mapping_version,scope_version,analyzed_scope,analysis_status,result_mode,pipeline_outcome,gate_outcome,loc,payload_hash,canonical_payload,occurrence_count", runRows, "");
      insert(c, "occurrences", "run_id,identity_key,rule_key,rule_name,entity_type,entity_key,entity_name,severity,display_location,message", occurrenceRows, "");
    } finally { for (var array : arrays) array.free(); }
    return receipts;
  }
  private String key(CanonicalSnapshot s) { return s.streamKey() + ":" + s.input().externalRunId(); }
  private static List<Object> row(Object... values) { return Arrays.asList(values); }
  private static void insert(Connection c, String table, String columns, List<List<Object>> rows, String conflict) throws SQLException {
    for (int start = 0; start < rows.size(); start += 250) {
      var chunk = rows.subList(start, Math.min(rows.size(), start + 250));
      String tuple = "(" + String.join(",", Collections.nCopies(chunk.getFirst().size(), "?")) + ")";
      // All identifiers and conflict clauses are internal constants, never caller input.
      String sql = "INSERT INTO analytics_private." + table + " (" + columns + ") VALUES " + String.join(",", Collections.nCopies(chunk.size(), tuple)) + conflict;
      try (var q = c.prepareStatement(sql)) {
        int parameter = 1;
        for (var row : chunk) for (var value : row) q.setObject(parameter++, value);
        q.executeUpdate();
      }
    }
  }
}
