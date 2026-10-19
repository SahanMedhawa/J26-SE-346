package akegp.conformance.domain;

import java.time.Instant;

public record ConformanceScan(
    String scanId,
    String repositoryId,
    String repositoryUrl,
    Instant scannedAt,
    int fileCount,
    int dependencyCount) {}
