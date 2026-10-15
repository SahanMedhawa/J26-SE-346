package akegp.enforcement.service;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;

import akegp.enforcement.Fixtures.Kit;
import org.junit.jupiter.api.Test;

class DashboardStatsServiceTest {

  @Test void countsRunsAnalysisSkippedAndViolations() {
    Kit k = Kit.linked();
    k.enforcement.handle(event(1, "README.md"));                     // skipped
    k.enforcement.handle(event(2, "application.yml"));               // skipped
    k.enforcement.handle(event(3, "src/Service.java"));              // passed
    k.enforcement.handle(event(4, "src/OrderController.java"));      // violation found
    DashboardStats s = new DashboardStatsService(k.repository).stats();
    assertEquals(4, s.runs());
    assertEquals(2, s.analysisTriggered());
    assertEquals(2, s.skipped());
    assertEquals(1, s.violations());
    assertEquals("Violation found", s.recent().get(0).result());      // newest first
    assertEquals("Source code", s.recent().get(0).changeType());
    assertEquals("Passed", s.recent().get(1).result());
    assertEquals("Skipped", s.recent().get(3).result());
    assertEquals("Documentation", s.recent().get(3).changeType());
  }

  @Test void labelsHumanDecisions() {
    Kit k = Kit.linked();
    k.enforcement.handle(event(1, "src/OrderController.java"));
    k.review.decide("o/r", 1, "reject", "k", null, null);
    assertEquals("Rejected", new DashboardStatsService(k.repository).stats().recent().get(0).result());
  }

  @Test void unlinkedRepoShowsAsHeldNotViolation() {
    Kit k = Kit.unlinked();
    k.enforcement.handle(event(1, "src/A.java"));
    assertEquals("Held", new DashboardStatsService(k.repository).stats().recent().get(0).result());
  }

  @Test void recentListIsCapped() {
    Kit k = Kit.linked();
    for (int i = 1; i <= 25; i++) k.enforcement.handle(event(i, "README.md"));
    DashboardStats s = new DashboardStatsService(k.repository).stats();
    assertEquals(20, s.recent().size());
    assertEquals(25, s.runs());
  }

  @Test void emptyState() {
    DashboardStats s = new DashboardStatsService(Kit.linked().repository).stats();
    assertEquals(0, s.runs());
    assertEquals(0, s.recent().size());
  }
}
