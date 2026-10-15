package akegp.enforcement.repository;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.domain.AuditEntry;
import akegp.enforcement.domain.ChangeType;
import akegp.enforcement.domain.Decision;
import akegp.enforcement.domain.DecisionAction;
import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.HoldReason;
import akegp.enforcement.domain.Justification;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.domain.Violation;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the JDBC store against a real PostgreSQL. Skipped unless ENF_TEST_JDBC_URL is set, for example
 *   ENF_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/akegp_test ENF_TEST_JDBC_USER=akegp ENF_TEST_JDBC_PASSWORD=akegp mvn test
 * It only touches tables named enftest_* (and empties them).
 */
@EnabledIfEnvironmentVariable(named = "ENF_TEST_JDBC_URL", matches = ".+")
class JdbcEnforcementRepositoryDbTest {
  protected JdbcEnforcementRepository repo;

  protected DataSource dataSource() {
    String url = System.getenv("ENF_TEST_JDBC_URL");
    String user = System.getenv("ENF_TEST_JDBC_USER");
    String password = System.getenv("ENF_TEST_JDBC_PASSWORD");
    return new DataSource() {
      public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, password); }
      public Connection getConnection(String u, String p) throws SQLException { return DriverManager.getConnection(url, u, p); }
      public PrintWriter getLogWriter() { return null; }
      public void setLogWriter(PrintWriter out) {}
      public void setLoginTimeout(int seconds) {}
      public int getLoginTimeout() { return 0; }
      public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
      public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
      public boolean isWrapperFor(Class<?> iface) { return false; }
    };
  }

  @BeforeEach void setUp() {
    System.setProperty("akegp.enforcement.allow-reset", "true");
    repo = new JdbcEnforcementRepository(dataSource(), "enftest_");
    repo.init();
    repo.reset();
  }

  private static PipelineRun newRun(int pr, String... files) {
    PipelineEvent e = event(pr, files);
    return PipelineRun.from(e, ChangeType.SOURCE_CODE, "proj-1");
  }

  @Test void initCanRunTwice() { repo.init(); repo.init(); }

  @Test void createRunStoresRunChangesAndAssignsIdAndTime() {
    PipelineRun saved = repo.createRun(newRun(5, "src/A.java", "src/B.java"));
    assertTrue(saved.getId() > 0);
    assertTrue(saved.getCreatedAt() != null);
    PipelineRun back = repo.latestRun("o/r", 5).orElseThrow();
    assertEquals(saved.getId(), back.getId());
    assertEquals("abc1234", back.getCommitSha());
    assertEquals("def5678", back.getBaseSha());
    assertEquals("proj-1", back.getProjectId());
    assertEquals(ChangeType.SOURCE_CODE, back.getClassification());
    assertEquals(RunState.RECEIVED, back.getState());
    assertEquals(List.of("src/A.java", "src/B.java"), back.getChangedFiles());
    assertEquals("modified", back.getChanges().get(0).status());
    assertEquals(1, back.getChanges().get(0).additions());
  }

  @Test void renamedFileKeepsItsOldPath() {
    PipelineEvent e = new PipelineEvent("o/r", null, 6, "abc1234", null, List.of("B.java"),
        List.of(new FileChange("B.java", "renamed", "A.java", 0, 0, false, null, null, null)));
    repo.createRun(PipelineRun.from(e, ChangeType.SOURCE_CODE, null));
    PipelineRun back = repo.latestRun("o/r", 6).orElseThrow();
    assertEquals("A.java", back.getChanges().get(0).oldPath());
    assertNull(back.getBaseSha());
    assertNull(back.getProjectId());
    assertNull(back.getRepoUrl());
  }

  @Test void updateRunSavesStateDecisionAndReplacesViolations() {
    PipelineRun r = repo.createRun(newRun(1, "src/OrderController.java"));
    r.setState(RunState.HELD);
    r.setHoldReason(HoldReason.VIOLATIONS);
    r.setViolations(List.of(
        new Violation("R-014", "critical", "src/OrderController.java", 22, "direct repository call", "new"),
        new Violation("R-009", "major", "src/Dto.java", null, "no line known", "existing")));
    repo.updateRun(r);
    PipelineRun held = repo.latestRun("o/r", 1).orElseThrow();
    assertEquals(RunState.HELD, held.getState());
    assertEquals(HoldReason.VIOLATIONS, held.getHoldReason());
    assertEquals(2, held.getViolations().size());
    assertEquals(Integer.valueOf(22), held.getViolations().get(0).line());
    assertNull(held.getViolations().get(1).line());
    assertNull(held.getDecision());

    Instant at = Instant.parse("2026-10-14T10:15:30Z");
    held.setViolations(List.of());                                   // e.g. a fix was applied and the re-scan is clean
    held.setState(RunState.APPROVED_EXCEPTION);
    held.setDecision(new Decision(DecisionAction.APPROVE_EXCEPTION, "kaushalya",
        Justification.KNOWN_TECHNICAL_CONSTRAINT, "it's \"temporary\"", at));
    repo.updateRun(held);
    PipelineRun done = repo.latestRun("o/r", 1).orElseThrow();
    assertEquals(RunState.APPROVED_EXCEPTION, done.getState());
    assertEquals(0, done.getViolations().size());
    assertEquals(DecisionAction.APPROVE_EXCEPTION, done.getDecision().action());
    assertEquals("kaushalya", done.getDecision().reviewer());
    assertEquals(Justification.KNOWN_TECHNICAL_CONSTRAINT, done.getDecision().justification());
    assertEquals("it's \"temporary\"", done.getDecision().comment());
    assertEquals(at, done.getDecision().at());
  }

  @Test void latestRunPicksNewestAndLatestPerPrDeduplicates() {
    repo.createRun(newRun(1, "src/A.java"));
    PipelineRun second = repo.createRun(newRun(1, "src/B.java"));
    repo.createRun(newRun(2, "src/C.java"));
    assertEquals(second.getId(), repo.latestRun("o/r", 1).orElseThrow().getId());
    assertTrue(repo.latestRun("o/r", 99).isEmpty());
    List<PipelineRun> latest = repo.latestPerPr();
    assertEquals(2, latest.size());
    assertEquals(2, latest.get(0).getPrNumber());                     // newest first
  }

  @Test void listRunsLoadsChildrenOfEveryRunAndHonoursTheLimit() {
    for (int pr = 1; pr <= 5; pr++) {
      PipelineRun r = repo.createRun(newRun(pr, "src/F" + pr + ".java"));
      r.setViolations(List.of(new Violation("R-1", "minor", "src/F" + pr + ".java", pr, "m", "new")));
      repo.updateRun(r);
    }
    List<PipelineRun> all = repo.listRuns(500);
    assertEquals(5, all.size());
    assertEquals(5, all.get(0).getPrNumber());
    for (PipelineRun r : all) {
      assertEquals(1, r.getChangedFiles().size());
      assertEquals("src/F" + r.getPrNumber() + ".java", r.getChangedFiles().get(0));
      assertEquals(1, r.getViolations().size());
    }
    List<PipelineRun> two = repo.listRuns(2);
    assertEquals(List.of(5, 4), two.stream().map(PipelineRun::getPrNumber).toList());
    assertEquals(1, two.get(1).getChangedFiles().size());
  }

  @Test void auditLogKeepsOrderAndText() {
    AuditEntry a = repo.log("pr_received", "o/r", 1, "sha=abc classification=code change");
    repo.log("pr_held", "o/r", 1, "reason=violations count=2 — ünïcode \"quoted\"");
    assertTrue(a.id() > 0);
    List<AuditEntry> log = repo.auditLog();
    assertEquals(List.of("pr_received", "pr_held"), log.stream().map(AuditEntry::type).toList());
    assertEquals("reason=violations count=2 — ünïcode \"quoted\"", log.get(1).detail());
    assertEquals(1, log.get(1).prNumber());
  }

  @Test void dataIsSharedAcrossRepositoryInstances() {
    repo.createRun(newRun(7, "src/A.java"));
    var other = new JdbcEnforcementRepository(dataSource(), "enftest_");
    assertEquals(7, other.latestRun("o/r", 7).orElseThrow().getPrNumber());
  }

  @Test void updatingAMissingRunFails() {
    PipelineRun ghost = newRun(1, "src/A.java");
    ghost.setId(987654321L);
    try {
      repo.updateRun(ghost);
      throw new AssertionError("expected an exception");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("not found"));
    }
  }
}
