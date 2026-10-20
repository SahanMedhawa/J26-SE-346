package akegp.analytics.repository;

import akegp.analytics.ingestion.CanonicalSnapshot;
import akegp.analytics.ingestion.IngestionException;
import akegp.shared.analytics.AnalyticsIngestion.Receipt;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import javax.sql.DataSource;
import static akegp.analytics.ingestion.IngestionException.Code.*;

/** Owns a fresh JDBC transaction per import. No Spring startup or datasource auto-selection. */
public final class JdbcSnapshotRepository implements SnapshotRepository {
  private final DataSource dataSource;
  public JdbcSnapshotRepository(DataSource dataSource) { this.dataSource = dataSource; }

  @Override public Receipt append(CanonicalSnapshot snapshot) {
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      try {
        Receipt result = append(c, snapshot);
        c.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      // Do not propagate driver messages: connection strings or payloads may contain secrets.
      throw new IllegalStateException("Analytics database operation failed (SQL state " + e.getSQLState() + ")");
    }
  }

  private Receipt append(Connection c, CanonicalSnapshot n) throws SQLException {
    var s = n.input();
    var source = n.source();
    execute(c, """
        INSERT INTO analytics_private.streams
        (stream_key,stream_tuple,project_id,repository_id,branch_ref,source_system,provenance,dataset)
        VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (stream_key) DO NOTHING
        """, n.streamKey(), n.streamTuple(), s.projectId(), s.repositoryId(), s.branchRef(),
        source.system(), source.provenance().name(), source.dataset());
    // Serialize all writers to this stream, including the first concurrent append.
    try (var q = statement(c, "SELECT stream_tuple FROM analytics_private.streams WHERE stream_key=? FOR UPDATE", n.streamKey());
         var rs = q.executeQuery()) {
      if (!rs.next() || !rs.getString(1).equals(n.streamTuple()))
        throw new IngestionException(CONFLICT, "Stream identity collision");
    }
    // Replays of old runs remain valid after newer runs have been ingested.
    try (var q = statement(c, """
        SELECT id,payload_hash,canonical_payload,occurrence_count FROM analytics_private.runs
        WHERE stream_key=? AND external_run_id=?
        """, n.streamKey(), s.externalRunId()); var rs = q.executeQuery()) {
      if (rs.next()) {
        if (!rs.getString(2).equals(n.payloadHash()) || !rs.getString(3).equals(n.payload()))
          throw new IngestionException(CONFLICT, "External run ID reused with different content");
        return new Receipt(rs.getObject(1, UUID.class), true, rs.getInt(4), source.provenance().name(), source.label());
      }
    }
    try (var q = statement(c, "SELECT max(analysis_sequence) FROM analytics_private.runs WHERE stream_key=?", n.streamKey());
         var rs = q.executeQuery()) {
      rs.next();
      long previous = rs.getLong(1);
      if (!rs.wasNull() && s.sequence() <= previous)
        throw new IngestionException(OUT_OF_ORDER, "Sequence must exceed the latest stored run; suffix recomputation is unavailable");
    }
    UUID runId = UUID.randomUUID();
    var scope = c.createArrayOf("text", s.analyzedScope().stream().distinct().sorted().toArray(String[]::new));
    try {
      execute(c, """
          INSERT INTO analytics_private.runs
          (id,stream_key,external_run_id,analysis_sequence,analyzed_at,commit_ref,contract_version,
          identity_version,rule_set_version,detector_version,mapping_version,scope_version,
          analyzed_scope,analysis_status,result_mode,pipeline_outcome,gate_outcome,loc,
          payload_hash,canonical_payload,occurrence_count)
          VALUES (?,?,?,?,?,?,?,'identity-v1',?,?,?,?,?,?,?,?,?,?,?,?,?)
          """, runId, n.streamKey(), s.externalRunId(), s.sequence(), Timestamp.from(s.analyzedAt()),
          s.commitRef(), s.contractVersion(), s.ruleSetVersion(), s.detectorVersion(), s.mappingVersion(),
          s.scopeVersion(), scope, s.analysisStatus().name(), s.resultMode().name(), s.pipelineOutcome(),
          s.gateOutcome(), s.loc() == null ? null : s.loc().lines(), n.payloadHash(), n.payload(), n.occurrences().size());
    } finally { scope.free(); }
    // Batch writes avoid a cloud round trip per finding. Sorted identities preserve lock order.
    try (var identities = c.prepareStatement("""
        INSERT INTO analytics_private.identities
        (identity_key,identity_namespace,structural_tuple,upstream_id,rule_key,entity_type,
        entity_key,finding_kind,source_key,target_key,relation)
        VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (identity_key) DO NOTHING
        """)) {
      for (var o : n.occurrences()) {
        var f = o.finding();
        bind(identities, o.identityKey(), n.identityNamespace(), o.structure(), f.stableUpstreamId(), f.ruleKey(),
            f.entityType(), f.entityKey(), f.findingKind(), f.sourceKey(), f.targetKey(), f.relation());
        identities.addBatch();
      }
      identities.executeBatch();
    }
    if (!n.occurrences().isEmpty()) {
      var keys = c.createArrayOf("text", n.occurrences().stream().map(CanonicalSnapshot.Occurrence::identityKey).toArray(String[]::new));
      try (var q = c.prepareStatement("SELECT identity_key,identity_namespace,structural_tuple FROM analytics_private.identities WHERE identity_key = ANY (?)")) {
        q.setArray(1, keys);
        var expected = new java.util.HashMap<String, String>();
        n.occurrences().forEach(o -> expected.put(o.identityKey(), o.structure()));
        try (var rs = q.executeQuery()) {
          while (rs.next()) {
            String structure = expected.remove(rs.getString(1));
            if (!n.identityNamespace().equals(rs.getString(2)) || !rs.getString(3).equals(structure))
              throw new IngestionException(CONFLICT, "Stable identity reused for conflicting structure");
          }
        }
        if (!expected.isEmpty()) throw new IngestionException(CONFLICT, "Missing stable identities");
      } finally { keys.free(); }
    }
    try (var occurrences = c.prepareStatement("""
        INSERT INTO analytics_private.occurrences
        (run_id,identity_key,rule_key,rule_name,entity_type,entity_key,entity_name,severity,display_location,message)
        VALUES (?,?,?,?,?,?,?,?,?,?)
        """)) {
      for (var o : n.occurrences()) {
        var f = o.finding();
        bind(occurrences, runId, o.identityKey(), f.ruleKey(), f.ruleName(), f.entityType(), f.entityKey(),
            f.entityName(), f.severity(), f.displayLocation(), f.message());
        occurrences.addBatch();
      }
      occurrences.executeBatch();
    }
    return new Receipt(runId, false, n.occurrences().size(), source.provenance().name(), source.label());
  }

  private static PreparedStatement statement(Connection c, String sql, Object... args) throws SQLException {
    var result = c.prepareStatement(sql);
    try {
      bind(result, args);
      return result;
    } catch (SQLException e) { result.close(); throw e; }
  }
  private static void bind(PreparedStatement statement, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
  }
  private static void execute(Connection c, String sql, Object... args) throws SQLException {
    try (var q = statement(c, sql, args)) { q.executeUpdate(); }
  }
}
