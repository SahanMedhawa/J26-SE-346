package akegp.enforcement.review;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.Fixtures.Kit;
import akegp.enforcement.conformance.ScanReport;
import akegp.enforcement.domain.AuditEntry;
import akegp.enforcement.domain.Justification;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.github.StatusState;
import akegp.enforcement.service.EnforcementException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

  private static Kit heldKit() {
    Kit k = Kit.linked();
    k.enforcement.handle(event(1, "src/OrderController.java"));
    return k;
  }

  private static int statusOf(Runnable r) {
    return assertThrows(EnforcementException.class, r::run).status();
  }

  @Test void exceptionNeedsAValidJustificationCategory() {
    Kit k = heldKit();
    assertEquals(400, statusOf(() -> k.review.decide("o/r", 1, "approve_exception", "kaushalya", null, null)));
    assertEquals(400, statusOf(() -> k.review.decide("o/r", 1, "approve_exception", "kaushalya", "because", null)));
    assertEquals(RunState.HELD, k.repository.latestRun("o/r", 1).orElseThrow().getState()); // nothing changed
  }

  @Test void approvedExceptionReleasesThePrAndRecordsTheReason() {
    Kit k = heldKit();
    PipelineRun r = k.review.decide("o/r", 1, "approve_exception", "kaushalya", "known-technical-constraint", "ok");
    assertEquals(RunState.APPROVED_EXCEPTION, r.getState());
    assertEquals(StatusState.SUCCESS, k.github.last().state());
    assertEquals(Justification.KNOWN_TECHNICAL_CONSTRAINT, r.getDecision().justification());
    assertEquals("kaushalya", r.getDecision().reviewer());
    assertEquals(RunState.APPROVED_EXCEPTION, k.repository.latestRun("o/r", 1).orElseThrow().getState());
  }

  @Test void rejectAndRequestChangesFailTheStatus() {
    Kit k = heldKit();
    k.review.decide("o/r", 1, "reject", "k", null, null);
    assertEquals(StatusState.FAILURE, k.github.last().state());
    k.enforcement.handle(event(2, "src/OrderController.java"));
    PipelineRun r = k.review.decide("o/r", 2, "request_changes", "k", null, null);
    assertEquals(RunState.CHANGES_REQUESTED, r.getState());
    assertEquals(StatusState.FAILURE, k.github.last().state());
  }

  @Test void applyFixStaysHeldWhileRescanStillFindsViolations() {
    Kit k = heldKit();
    PipelineRun r = k.review.decide("o/r", 1, "apply_fix", "k", null, null);
    assertEquals(RunState.HELD, r.getState());
    assertEquals(StatusState.PENDING, k.github.last().state());
  }

  @Test void applyFixReleasesThePrWhenRescanIsClean() {
    Kit k = heldKit();
    k.scanner.behaviour = req -> new ScanReport(List.of());
    PipelineRun r = k.review.decide("o/r", 1, "apply_fix", "k", null, null);
    assertEquals(RunState.PASSED, r.getState());
    assertTrue(r.getViolations().isEmpty());
    assertEquals(StatusState.SUCCESS, k.github.last().state());
  }

  @Test void applyFixStaysHeldWhenRescanIsUnavailable() {
    Kit k = heldKit();
    k.scanner.behaviour = req -> { throw new IllegalStateException("down"); };
    assertEquals(RunState.HELD, k.review.decide("o/r", 1, "apply_fix", "k", null, null).getState());
  }

  @Test void cannotDecideOnAPrThatIsNotHeldOrDoesNotExist() {
    Kit k = Kit.linked();
    k.enforcement.handle(event(1, "README.md"));
    assertEquals(409, statusOf(() -> k.review.decide("o/r", 1, "reject", "k", null, null)));
    assertEquals(404, statusOf(() -> k.review.decide("o/r", 99, "reject", "k", null, null)));
  }

  @Test void reviewerAndActionAreRequired() {
    Kit k = heldKit();
    assertEquals(400, statusOf(() -> k.review.decide("o/r", 1, "reject", " ", null, null)));
    assertEquals(400, statusOf(() -> k.review.decide("o/r", 1, "reject", null, null, null)));
    assertEquals(400, statusOf(() -> k.review.decide("o/r", 1, "merge", "k", null, null)));
  }

  @Test void decisionIsInTheAuditTrail() {
    Kit k = heldKit();
    k.review.decide("o/r", 1, "reject", "k", null, "wrong layer");
    List<AuditEntry> log = k.repository.auditLog();
    assertEquals(List.of("pr_received", "pr_held", "decision"), log.stream().map(AuditEntry::type).toList());
    assertTrue(log.get(2).detail().contains("action=reject"));
    assertTrue(log.get(2).detail().contains("resultingState=rejected"));
  }
}
