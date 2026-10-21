package akegp.enforcement.conformance;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akegp.enforcement.domain.Violation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridConformanceScannerTest {
  private static ScanRequest request() {
    var e = event(3, "src/A.java");
    return new ScanRequest(e.repoUrl(), e.commitSha(), e.baseSha(), e.prNumber(), "proj-1", e.changedFiles(), e.changes());
  }

  private static ScanReport one(String rule) {
    return new ScanReport(List.of(new Violation(rule, "critical", "src/A.java", 1, "x", "new")));
  }

  @Test void decisionComesFromTheStandInAndComponentTwoIsStillCalled() {
    List<String> calls = new ArrayList<>();
    ConformanceScanner standIn = r -> { calls.add("standin"); return one("R-014"); };
    ConformanceScanner c2 = r -> { calls.add("c2"); return new ScanReport(List.of()); };
    ScanReport out = new HybridConformanceScanner(standIn, c2).scan(request());
    assertEquals(List.of("standin", "c2"), calls);
    assertEquals("R-014", out.violations().get(0).ruleId());
  }

  @Test void componentTwoFailureDoesNotChangeTheDecision() {
    ConformanceScanner standIn = r -> one("R-014");
    ConformanceScanner c2 = r -> { throw new IllegalStateException("graph store down"); };
    ScanReport out = new HybridConformanceScanner(standIn, c2).scan(request());
    assertEquals(1, out.violations().size());
  }

  @Test void componentTwoFindingsAreIgnoredForTheDecision() {
    ConformanceScanner standIn = r -> new ScanReport(List.of());
    ConformanceScanner c2 = r -> one("R-99");
    assertEquals(0, new HybridConformanceScanner(standIn, c2).scan(request()).violations().size());
  }

  @Test void standInFailureStillHoldsThePullRequest() {
    ConformanceScanner standIn = r -> { throw new IllegalStateException("boom"); };
    ConformanceScanner c2 = r -> new ScanReport(List.of());
    assertThrows(IllegalStateException.class, () -> new HybridConformanceScanner(standIn, c2).scan(request()));
  }
}
