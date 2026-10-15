package akegp.enforcement.conformance;

import akegp.enforcement.domain.Violation;
import java.util.List;
import java.util.Locale;

/** Stand-in for Component 2 until it is connected: any changed file with "controller" in its path is one violation. */
public class MockConformanceScanner implements ConformanceScanner {
  @Override
  public ScanReport scan(ScanRequest request) {
    List<Violation> violations = request.changedFiles().stream()
        .filter(f -> f.toLowerCase(Locale.ROOT).contains("controller"))
        .map(f -> new Violation("R-014", "critical", f, 42, "Controller accesses the database layer directly", "new"))
        .toList();
    return new ScanReport(violations);
  }
}
