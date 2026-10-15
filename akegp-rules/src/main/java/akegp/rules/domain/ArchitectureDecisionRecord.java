package akegp.rules.domain;

import java.time.Instant;
import java.util.UUID;

public record ArchitectureDecisionRecord(
    UUID id,
    String adrId,
    UUID projectId,
    String title,
    ADRStatus status,
    String context,
    String decision,
    String consequences,
    int version,
    Instant createdAt,
    Instant updatedAt) {}
