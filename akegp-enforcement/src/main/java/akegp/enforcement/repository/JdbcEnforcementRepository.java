package akegp.enforcement.repository;

import akegp.enforcement.domain.AuditEntry;
import akegp.enforcement.domain.ChangeType;
import akegp.enforcement.domain.Decision;
import akegp.enforcement.domain.DecisionAction;
import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.HoldReason;
import akegp.enforcement.domain.Justification;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.domain.Violation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * PostgreSQL storage using plain JDBC, so it needs no entity scanning or schema tooling that could clash with other
 * modules. Tables are named with a prefix (akegp.schemas.enforcement, default "enf_"), for example enf_pipeline_run.
 * Line-level change detail (added/removed lines, diff text) is passed on to Component 2 but is not stored.
 */
public class JdbcEnforcementRepository implements EnforcementRepository, AutoCloseable {
  private static final Pattern SAFE_PREFIX = Pattern.compile("[a-z_][a-z0-9_]*");

  private final DataSource dataSource;
  private final String run, change, violation, audit;

  public JdbcEnforcementRepository(DataSource dataSource, String tablePrefix) {
    if (!SAFE_PREFIX.matcher(tablePrefix).matches())
      throw new IllegalArgumentException("table prefix must match [a-z_][a-z0-9_]*: " + tablePrefix);
    this.dataSource = dataSource;
    this.run = tablePrefix + "pipeline_run";
    this.change = tablePrefix + "run_change";
    this.violation = tablePrefix + "run_violation";
    this.audit = tablePrefix + "audit_log";
  }

  /** Creates the tables if they do not exist. Safe to call on every start-up. */
  public void init() {
    for (String sql : schemaStatements()) execute(sql);
  }

  List<String> schemaStatements() {
    return List.of(
        "CREATE TABLE IF NOT EXISTS " + run + " ("
            + "id BIGSERIAL PRIMARY KEY, repo TEXT NOT NULL, repo_url TEXT, pr_number INTEGER NOT NULL,"
            + " commit_sha TEXT NOT NULL, base_sha TEXT, project_id TEXT, classification TEXT NOT NULL,"
            + " state TEXT NOT NULL, hold_reason TEXT, decision_action TEXT, decision_reviewer TEXT,"
            + " decision_justification TEXT, decision_comment TEXT, decision_at TIMESTAMPTZ,"
            + " created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",
        "CREATE INDEX IF NOT EXISTS " + run + "_pr_idx ON " + run + " (repo, pr_number, id DESC)",
        "CREATE TABLE IF NOT EXISTS " + change + " ("
            + "run_id BIGINT NOT NULL REFERENCES " + run + "(id) ON DELETE CASCADE, path TEXT NOT NULL,"
            + " status TEXT NOT NULL, old_path TEXT, additions INTEGER NOT NULL DEFAULT 0,"
            + " deletions INTEGER NOT NULL DEFAULT 0, is_binary BOOLEAN NOT NULL DEFAULT FALSE)",
        "CREATE INDEX IF NOT EXISTS " + change + "_run_idx ON " + change + " (run_id)",
        "CREATE TABLE IF NOT EXISTS " + violation + " ("
            + "run_id BIGINT NOT NULL REFERENCES " + run + "(id) ON DELETE CASCADE, rule_id TEXT,"
            + " severity TEXT, file TEXT, line INTEGER, message TEXT, tag TEXT)",
        "CREATE INDEX IF NOT EXISTS " + violation + "_run_idx ON " + violation + " (run_id)",
        "CREATE TABLE IF NOT EXISTS " + audit + " ("
            + "id BIGSERIAL PRIMARY KEY, at TIMESTAMPTZ NOT NULL DEFAULT now(), type TEXT NOT NULL,"
            + " repo TEXT, pr_number INTEGER, detail TEXT)");
  }

  @Override
  public PipelineRun createRun(PipelineRun r) {
    return inTransaction(c -> {
      PipelineRun stored = r.copy();
      try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + run
          + " (repo, repo_url, pr_number, commit_sha, base_sha, project_id, classification, state, hold_reason)"
          + " VALUES (?,?,?,?,?,?,?,?,?) RETURNING id, created_at")) {
        ps.setString(1, r.getRepo());
        ps.setString(2, r.getRepoUrl());
        ps.setInt(3, r.getPrNumber());
        ps.setString(4, r.getCommitSha());
        ps.setString(5, r.getBaseSha());
        ps.setString(6, r.getProjectId());
        ps.setString(7, r.getClassification().name());
        ps.setString(8, r.getState().name());
        ps.setString(9, r.getHoldReason() == null ? null : r.getHoldReason().name());
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          stored.setId(rs.getLong("id"));
          stored.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        }
      }
      for (FileChange f : r.getChanges()) {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + change
            + " (run_id, path, status, old_path, additions, deletions, is_binary) VALUES (?,?,?,?,?,?,?)")) {
          ps.setLong(1, stored.getId());
          ps.setString(2, f.path());
          ps.setString(3, f.status());
          ps.setString(4, f.oldPath());
          ps.setInt(5, f.additions());
          ps.setInt(6, f.deletions());
          ps.setBoolean(7, f.binary());
          ps.executeUpdate();
        }
      }
      insertViolations(c, stored.getId(), r.getViolations());
      return stored;
    });
  }

  @Override
  public void updateRun(PipelineRun r) {
    inTransaction(c -> {
      Decision d = r.getDecision();
      try (PreparedStatement ps = c.prepareStatement("UPDATE " + run + " SET project_id=?, state=?, hold_reason=?,"
          + " decision_action=?, decision_reviewer=?, decision_justification=?, decision_comment=?, decision_at=?,"
          + " updated_at=now() WHERE id=?")) {
        ps.setString(1, r.getProjectId());
        ps.setString(2, r.getState().name());
        ps.setString(3, r.getHoldReason() == null ? null : r.getHoldReason().name());
        ps.setString(4, d == null ? null : d.action().name());
        ps.setString(5, d == null ? null : d.reviewer());
        ps.setString(6, d == null || d.justification() == null ? null : d.justification().name());
        ps.setString(7, d == null ? null : d.comment());
        if (d == null || d.at() == null) ps.setNull(8, Types.TIMESTAMP); else ps.setTimestamp(8, Timestamp.from(d.at()));
        ps.setLong(9, r.getId());
        if (ps.executeUpdate() == 0) throw new IllegalStateException("run " + r.getId() + " not found");
      }
      try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + violation + " WHERE run_id=?")) {
        ps.setLong(1, r.getId());
        ps.executeUpdate();
      }
      insertViolations(c, r.getId(), r.getViolations());
      return null;
    });
  }

  private void insertViolations(Connection c, long runId, List<Violation> violations) throws SQLException {
    for (Violation v : violations) {
      try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + violation
          + " (run_id, rule_id, severity, file, line, message, tag) VALUES (?,?,?,?,?,?,?)")) {
        ps.setLong(1, runId);
        ps.setString(2, v.ruleId());
        ps.setString(3, v.severity());
        ps.setString(4, v.file());
        if (v.line() == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, v.line());
        ps.setString(6, v.message());
        ps.setString(7, v.tag());
        ps.executeUpdate();
      }
    }
  }

  @Override
  public Optional<PipelineRun> latestRun(String repo, int prNumber) {
    return inTransaction(c -> {
      List<PipelineRun> runs = new ArrayList<>();
      try (PreparedStatement ps = c.prepareStatement(
          "SELECT * FROM " + run + " WHERE repo=? AND pr_number=? ORDER BY id DESC LIMIT 1")) {
        ps.setString(1, repo);
        ps.setInt(2, prNumber);
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) runs.add(mapRun(rs)); }
      }
      if (runs.isEmpty()) return Optional.<PipelineRun>empty();
      PipelineRun r = runs.get(0);
      attachChildren(c, runs, "SELECT * FROM " + change + " WHERE run_id=?", "SELECT * FROM " + violation + " WHERE run_id=?",
          ps -> ps.setLong(1, r.getId()));
      return Optional.of(r);
    });
  }

  @Override
  public List<PipelineRun> listRuns(int limit) {
    return inTransaction(c -> {
      List<PipelineRun> runs = new ArrayList<>();
      try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + run + " ORDER BY id DESC LIMIT ?")) {
        ps.setInt(1, limit);
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) runs.add(mapRun(rs)); }
      }
      if (runs.isEmpty()) return runs;
      String latest = "(SELECT id FROM " + run + " ORDER BY id DESC LIMIT ?)";
      attachChildren(c, runs, "SELECT * FROM " + change + " WHERE run_id IN " + latest,
          "SELECT * FROM " + violation + " WHERE run_id IN " + latest, ps -> ps.setInt(1, limit));
      return runs;
    });
  }

  private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

  /** Loads the changes and violations of the given runs with two queries (not one per run). */
  private void attachChildren(Connection c, List<PipelineRun> runs, String changeSql, String violationSql, Binder binder)
      throws SQLException {
    Map<Long, PipelineRun> byId = new HashMap<>();
    for (PipelineRun r : runs) byId.put(r.getId(), r);
    try (PreparedStatement ps = c.prepareStatement(changeSql)) {
      binder.bind(ps);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          PipelineRun r = byId.get(rs.getLong("run_id"));
          if (r == null) continue;
          FileChange f = new FileChange(rs.getString("path"), rs.getString("status"), rs.getString("old_path"),
              rs.getInt("additions"), rs.getInt("deletions"), rs.getBoolean("is_binary"), null, null, null);
          r.getChanges().add(f);
          r.getChangedFiles().add(f.path());
        }
      }
    }
    try (PreparedStatement ps = c.prepareStatement(violationSql)) {
      binder.bind(ps);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          PipelineRun r = byId.get(rs.getLong("run_id"));
          if (r == null) continue;
          int line = rs.getInt("line");
          Integer lineOrNull = rs.wasNull() ? null : line;
          r.getViolations().add(new Violation(rs.getString("rule_id"), rs.getString("severity"), rs.getString("file"),
              lineOrNull, rs.getString("message"), rs.getString("tag")));
        }
      }
    }
  }

  private PipelineRun mapRun(ResultSet rs) throws SQLException {
    PipelineRun r = new PipelineRun();
    r.setId(rs.getLong("id"));
    r.setCreatedAt(rs.getTimestamp("created_at").toInstant());
    r.setRepo(rs.getString("repo"));
    r.setRepoUrl(rs.getString("repo_url"));
    r.setPrNumber(rs.getInt("pr_number"));
    r.setCommitSha(rs.getString("commit_sha"));
    r.setBaseSha(rs.getString("base_sha"));
    r.setProjectId(rs.getString("project_id"));
    r.setClassification(ChangeType.valueOf(rs.getString("classification")));
    r.setState(RunState.valueOf(rs.getString("state")));
    String hold = rs.getString("hold_reason");
    r.setHoldReason(hold == null ? null : HoldReason.valueOf(hold));
    String action = rs.getString("decision_action");
    if (action != null) {
      String just = rs.getString("decision_justification");
      Timestamp at = rs.getTimestamp("decision_at");
      r.setDecision(new Decision(DecisionAction.valueOf(action), rs.getString("decision_reviewer"),
          just == null ? null : Justification.valueOf(just), rs.getString("decision_comment"),
          at == null ? null : at.toInstant()));
    }
    return r;
  }

  @Override
  public AuditEntry log(String type, String repo, int prNumber, String detail) {
    return inTransaction(c -> {
      try (PreparedStatement ps = c.prepareStatement(
          "INSERT INTO " + audit + " (type, repo, pr_number, detail) VALUES (?,?,?,?) RETURNING id, at")) {
        ps.setString(1, type);
        ps.setString(2, repo);
        ps.setInt(3, prNumber);
        ps.setString(4, detail);
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          return new AuditEntry(rs.getLong("id"), rs.getTimestamp("at").toInstant(), type, repo, prNumber, detail);
        }
      }
    });
  }

  @Override
  public List<AuditEntry> auditLog() {
    return inTransaction(c -> {
      List<AuditEntry> out = new ArrayList<>();
      try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + audit + " ORDER BY id");
           ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new AuditEntry(rs.getLong("id"), rs.getTimestamp("at").toInstant(), rs.getString("type"),
              rs.getString("repo"), rs.getInt("pr_number"), rs.getString("detail")));
        }
      }
      return out;
    });
  }

  @Override
  public void reset() {
    if (!"true".equals(System.getProperty("akegp.enforcement.allow-reset")))
      throw new IllegalStateException("reset() on a real database needs -Dakegp.enforcement.allow-reset=true");
    execute("TRUNCATE " + run + ", " + change + ", " + violation + ", " + audit + " RESTART IDENTITY");
  }

  /** Closes the connection pool when the application shuts down. */
  @Override
  public void close() {
    if (dataSource instanceof AutoCloseable closeable) {
      try {
        closeable.close(); // e.g. the Hikari pool
      } catch (Exception e) {
        throw new IllegalStateException("could not close data source: " + e.getMessage(), e);
      }
    }
  }

  // ---- small JDBC helpers ----

  private interface Work<T> { T run(Connection c) throws SQLException; }

  private <T> T inTransaction(Work<T> work) {
    try (Connection c = dataSource.getConnection()) {
      boolean previous = c.getAutoCommit();
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(previous);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("database error: " + e.getMessage(), e);
    }
  }

  private void execute(String sql) {
    inTransaction(c -> {
      try (PreparedStatement ps = c.prepareStatement(sql)) { ps.execute(); }
      return null;
    });
  }
}
