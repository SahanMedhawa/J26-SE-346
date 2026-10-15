package akegp.enforcement.service;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.Fixtures.Kit;
import akegp.enforcement.conformance.ScanReport;
import akegp.enforcement.domain.AuditEntry;
import akegp.enforcement.domain.ChangeType;
import akegp.enforcement.domain.HoldReason;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.github.StatusState;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnforcementServiceTest {

  @Test void docsOnlyPrPassesWithoutScan() {
    Kit k = Kit.linked();
    PipelineRun r = k.enforcement.handle(event(1, "README.md"));
    assertEquals(RunState.PASSED, r.getState());
    assertEquals(ChangeType.DOCUMENTATION, r.getClassification());
    assertEquals(0, k.scanner.seen.size());                    // Component 2 never called
    assertEquals(1, k.github.sent.size());                     // straight to success, never pending
    assertEquals(StatusState.SUCCESS, k.github.last().state());
  }

  @Test void configOnlyPrPassesWithoutScan() {
    Kit k = Kit.linked();
    assertEquals(RunState.PASSED, k.enforcement.handle(event(1, "application.yml")).getState());
    assertEquals(0, k.scanner.seen.size());
  }

  @Test void cleanCodePrPasses() {
    Kit k = Kit.linked();
    PipelineRun r = k.enforcement.handle(event(1, "src/Service.java"));
    assertEquals(RunState.PASSED, r.getState());
    assertEquals(1, k.scanner.seen.size());
    assertEquals(StatusState.PENDING, k.github.sent.get(0).state());   // scan in progress
    assertEquals(StatusState.SUCCESS, k.github.last().state());
  }

  @Test void violatingCodePrIsHeld() {
    Kit k = Kit.linked();
    PipelineRun r = k.enforcement.handle(event(1, "src/OrderController.java"));
    assertEquals(RunState.HELD, r.getState());
    assertEquals(HoldReason.VIOLATIONS, r.getHoldReason());
    assertEquals(1, r.getViolations().size());
    assertEquals(StatusState.PENDING, k.github.last().state());        // pending blocks the merge button
    assertTrue(k.github.last().description().startsWith("Held"));
  }

  @Test void componentTwoFailureHoldsThePr() {
    Kit k = Kit.linked();
    k.scanner.behaviour = req -> { throw new IllegalStateException("down"); };
    PipelineRun r = k.enforcement.handle(event(1, "src/A.java"));
    assertEquals(RunState.HELD, r.getState());
    assertEquals(HoldReason.SCAN_UNAVAILABLE, r.getHoldReason());
    assertEquals(StatusState.PENDING, k.github.last().state());
  }

  @Test void codePrFromUnlinkedRepoIsHeldAndNotScanned() {
    Kit k = Kit.unlinked();
    PipelineRun r = k.enforcement.handle(event(1, "src/A.java"));
    assertEquals(RunState.HELD, r.getState());
    assertEquals(HoldReason.PROJECT_NOT_LINKED, r.getHoldReason());
    assertEquals(0, k.scanner.seen.size());
    assertTrue(k.github.last().description().contains("not linked"));
  }

  @Test void docsOnlyPrFromUnlinkedRepoStillPasses() {
    assertEquals(RunState.PASSED, Kit.unlinked().enforcement.handle(event(1, "README.md")).getState());
  }

  @Test void scanRequestCarriesEverythingComponentTwoNeeds() {
    Kit k = Kit.linked();
    k.scanner.behaviour = req -> new ScanReport(List.of());
    k.enforcement.handle(event(12, "src/A.java", "src/B.java"));
    var req = k.scanner.seen.get(0);
    assertEquals("proj-1", req.projectId());
    assertEquals("abc1234", req.commitSha());
    assertEquals("def5678", req.baseSha());
    assertEquals(12, req.prNumber());
    assertEquals("https://github.com/o/r", req.repoUrl());
    assertEquals(List.of("src/A.java", "src/B.java"), req.changedFiles());
    assertEquals(2, req.changes().size());
  }

  @Test void newPushOnSamePrKeepsHistoryAndLatestRunWins() {
    Kit k = Kit.linked();
    k.enforcement.handle(event(1, "src/OrderController.java"));
    k.enforcement.handle(event(1, "src/Service.java"));
    assertEquals(2, k.repository.listRuns(10).size());
    List<PipelineRun> latest = k.repository.latestPerPr();
    assertEquals(1, latest.size());
    assertEquals(RunState.PASSED, latest.get(0).getState());
  }

  @Test void auditTrailRecordsWhatHappened() {
    Kit k = Kit.linked();
    k.enforcement.handle(event(1, "src/OrderController.java"));
    List<String> types = k.repository.auditLog().stream().map(AuditEntry::type).toList();
    assertEquals(List.of("pr_received", "pr_held"), types);
  }

  @Test void githubOutageDoesNotLoseTheHold() {
    Kit k = Kit.linked();
    k.github.failWith = new RuntimeException("github down");
    PipelineRun r = k.enforcement.handle(event(1, "src/OrderController.java"));
    assertEquals(RunState.HELD, r.getState());
    assertEquals(RunState.HELD, k.repository.latestRun("o/r", 1).orElseThrow().getState());
    assertTrue(k.repository.auditLog().stream().anyMatch(e -> e.type().equals("status_error")));
  }
}
