package akegp.rules.api;

import akegp.rules.domain.AdrRuleFidelityStatus;
import java.util.UUID;

public record AdrRuleFidelityResponse(
    UUID ruleUuid,
    String ruleId,
    UUID adrUuid,
    String adrDisplayId,
    AdrRuleFidelityStatus status,
    String explanation) {}
