package akegp.enforcement.conformance;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akegp.shared.api.ConformanceScanService;
import akegp.shared.dto.ScanFindingDto;
import akegp.shared.dto.ScanRequestDto;
import akegp.shared.dto.ScanResultDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedKernelConformanceScannerTest {
  private static ScanRequest request() {
    var e = event(3, "src/A.java", "src/B.java");
    return new ScanRequest(e.repoUrl(), e.commitSha(), e.baseSha(), e.prNumber(), "proj-1", e.changedFiles(), e.changes());
  }

  @Test void sendsTheFullRequestAndMapsFindingsBack() {
    List<ScanRequestDto> seen = new ArrayList<>();
    ConformanceScanService fake = r -> {
      seen.add(r);
      return new ScanResultDto("run-9", List.of(new ScanFindingDto("R-1", "critical", "src/A.java", 12, "bad", "new")));
    };
    ScanReport report = new SharedKernelConformanceScanner(fake).scan(request());

    ScanRequestDto sent = seen.get(0);
    assertEquals("proj-1", sent.projectId());
    assertEquals("abc1234", sent.commitSha());
    assertEquals("def5678", sent.baseSha());
    assertEquals(3, sent.prNumber());
    assertEquals(List.of("src/A.java", "src/B.java"), sent.changes().stream().map(c -> c.path()).toList());
    assertEquals("modified", sent.changes().get(0).status());

    assertEquals(1, report.violations().size());
    assertEquals("R-1", report.violations().get(0).ruleId());
    assertEquals(Integer.valueOf(12), report.violations().get(0).line());
    assertEquals("new", report.violations().get(0).tag());
  }

  @Test void noFindingsMeansNoViolations() {
    ScanReport report = new SharedKernelConformanceScanner(r -> new ScanResultDto("r", null)).scan(request());
    assertEquals(0, report.violations().size());
  }

  @Test void aMissingResultIsAFailureSoThePrStaysHeld() {
    assertThrows(IllegalStateException.class, () -> new SharedKernelConformanceScanner(r -> null).scan(request()));
  }
}