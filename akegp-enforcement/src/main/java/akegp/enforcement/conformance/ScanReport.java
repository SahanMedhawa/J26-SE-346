package akegp.enforcement.conformance;

import akegp.enforcement.domain.Violation;
import java.util.List;

public record ScanReport(List<Violation> violations) {
  public ScanReport { violations = violations == null ? List.of() : List.copyOf(violations); }
}
