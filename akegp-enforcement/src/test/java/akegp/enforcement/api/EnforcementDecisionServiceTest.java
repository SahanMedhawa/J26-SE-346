package akegp.enforcement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.Fixtures.Kit;
import akegp.shared.dto.GovernanceDecisionDto;
import org.junit.jupiter.api.Test;
import static akegp.enforcement.Fixtures.event;

class EnforcementDecisionServiceTest {
  @Test void reportsTheStateOfARunByItsId() {
    Kit kit = Kit.linked();
    var run = kit.enforcement.handle(event(1, "src/OrderController.java"));   // mock scanner: held
    var service = new EnforcementDecisionServiceImpl(kit.repository);

    GovernanceDecisionDto held = service.decide(String.valueOf(run.getId()));
    assertEquals("HELD", held.decision());
    assertTrue(held.reason().contains("violations"));

    kit.review.decide("o/r", 1, "approve_exception", "rev", "known-technical-constraint", "ok");
    GovernanceDecisionDto approved = service.decide(String.valueOf(run.getId()));
    assertEquals("APPROVED_EXCEPTION", approved.decision());
    assertTrue(approved.reason().contains("rev"));
  }

  @Test void unknownOrBadIdsAreReportedNotThrown() {
    var service = new EnforcementDecisionServiceImpl(Kit.linked().repository);
    assertEquals("UNKNOWN", service.decide("999").decision());
    assertEquals("UNKNOWN", service.decide("abc").decision());
    assertEquals("UNKNOWN", service.decide(null).decision());
  }
}