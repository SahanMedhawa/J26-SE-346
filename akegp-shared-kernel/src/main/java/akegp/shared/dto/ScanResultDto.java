package akegp.shared.dto;

import java.util.List;

/** Conformance -> Enforcement. runId lets ConformanceResultService.findByRun(runId) return the stored violations later. */
public record ScanResultDto(String runId, List<ScanFindingDto> findings) {}