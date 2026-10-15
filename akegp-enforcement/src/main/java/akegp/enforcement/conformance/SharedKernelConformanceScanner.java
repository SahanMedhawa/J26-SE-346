package akegp.enforcement.conformance;

import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.LineChange;
import akegp.enforcement.domain.Violation;
import akegp.shared.api.ConformanceScanService;
import akegp.shared.dto.ChangedFileDto;
import akegp.shared.dto.ChangedLineDto;
import akegp.shared.dto.ScanRequestDto;
import akegp.shared.dto.ScanResultDto;
import java.util.List;

/** The real Component 2: calls the Conformance module through the shared-kernel interface. */
public class SharedKernelConformanceScanner implements ConformanceScanner {
  private final ConformanceScanService conformance;

  public SharedKernelConformanceScanner(ConformanceScanService conformance) { this.conformance = conformance; }

  @Override
  public ScanReport scan(ScanRequest r) {
    ScanResultDto result = conformance.scan(new ScanRequestDto(
        r.repoUrl(), r.commitSha(), r.baseSha(), r.prNumber(), r.projectId(),
        r.changes().stream().map(SharedKernelConformanceScanner::toDto).toList()));
    if (result == null) throw new IllegalStateException("conformance returned no result");
    List<Violation> violations = result.findings() == null ? List.of() : result.findings().stream()
        .map(f -> new Violation(f.ruleId(), f.severity(), f.file(), f.line(), f.message(), f.tag())).toList();
    return new ScanReport(violations);
  }

  static ChangedFileDto toDto(FileChange c) {
    return new ChangedFileDto(c.path(), c.status(), c.oldPath(), c.additions(), c.deletions(), c.binary(),
        lines(c.addedLines()), lines(c.removedLines()), c.patch());
  }

  private static List<ChangedLineDto> lines(List<LineChange> in) {
    return in.stream().map(l -> new ChangedLineDto(l.line(), l.text())).toList();
  }
}