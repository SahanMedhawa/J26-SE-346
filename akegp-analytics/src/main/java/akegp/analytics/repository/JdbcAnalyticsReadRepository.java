package akegp.analytics.repository;

import akegp.analytics.domain.AnalyticsRun;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;

public final class JdbcAnalyticsReadRepository implements AnalyticsReadRepository {
  private final DataSource dataSource;
  public JdbcAnalyticsReadRepository(DataSource dataSource) { this.dataSource = dataSource; }
  @Override public List<AnalyticsRun> readProject(UUID projectId) {
    try (var c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      c.setReadOnly(true);
      c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      try {
        Map<UUID, Map<String, AnalyticsRun.Finding>> findings = new HashMap<>();
        try (var q = c.prepareStatement("""
            SELECT o.* FROM analytics_private.occurrences o
            JOIN analytics_private.runs r ON r.id=o.run_id
            JOIN analytics_private.streams s ON s.stream_key=r.stream_key WHERE s.project_id=?
            """)) {
          q.setObject(1, projectId);
          try (var rs = q.executeQuery()) {
            while (rs.next()) {
              String key = rs.getString("identity_key");
              findings.computeIfAbsent(rs.getObject("run_id", UUID.class), ignored -> new HashMap<>()).put(key,
                  new AnalyticsRun.Finding(key, rs.getString("rule_key"), rs.getString("rule_name"),
                      rs.getString("entity_type"), rs.getString("entity_key"), rs.getString("entity_name"),
                      rs.getInt("severity"), rs.getString("display_location"), rs.getString("message")));
            }
          }
        }
        List<AnalyticsRun> result = new ArrayList<>();
        try (var q = c.prepareStatement("""
            SELECT r.id,r.external_run_id,r.analysis_sequence,r.analyzed_at,r.commit_ref,r.analysis_status,
              r.pipeline_outcome,r.gate_outcome,r.rule_set_version,r.detector_version,r.mapping_version,
              r.scope_version,r.identity_version,r.analyzed_scope,r.loc,
              s.stream_key,s.project_id,s.repository_id,s.branch_ref,s.provenance,s.dataset
            FROM analytics_private.runs r JOIN analytics_private.streams s USING(stream_key)
            WHERE s.project_id=? ORDER BY r.stream_key,r.analysis_sequence
            """)) {
          q.setObject(1, projectId);
          try (var rs = q.executeQuery()) {
            while (rs.next()) {
              UUID id = rs.getObject("id", UUID.class);
              var scopeArray = rs.getArray("analyzed_scope");
              List<String> scope;
              try { scope = Arrays.asList((String[]) scopeArray.getArray()); }
              finally { scopeArray.free(); }
              result.add(new AnalyticsRun(id, projectId, rs.getString("stream_key"), rs.getString("external_run_id"),
                  rs.getLong("analysis_sequence"), rs.getTimestamp("analyzed_at").toInstant(),
                  rs.getString("repository_id"), rs.getString("branch_ref"), rs.getString("provenance"),
                  rs.getString("dataset"), rs.getString("commit_ref"), rs.getString("analysis_status"),
                  rs.getString("pipeline_outcome"), rs.getString("gate_outcome"), rs.getString("rule_set_version"),
                  rs.getString("detector_version"), rs.getString("mapping_version"), rs.getString("scope_version"),
                  rs.getString("identity_version"), scope, rs.getObject("loc", Long.class), findings.getOrDefault(id, Map.of())));
            }
          }
        }
        c.commit();
        return List.copyOf(result);
      } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
    } catch (SQLException e) {
      throw new IllegalStateException("Analytics database read failed (SQL state " + e.getSQLState() + ")");
    }
  }
}
