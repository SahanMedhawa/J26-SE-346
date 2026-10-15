package akegp.rules.api;

import akegp.rules.domain.ADRStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ADRResponse(
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
    Instant updatedAt,
    Integer linkedRuleCount,
    List<UUID> linkedRuleIds) {}
