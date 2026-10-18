package akegp.rules.api;

import akegp.rules.domain.ArchitectureElementType;
import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import akegp.rules.domain.RuleSeverity;
import java.time.Instant;
import java.util.UUID;

public record RuleResponse(
    UUID id,
    String ruleId,
    UUID projectId,
    UUID adrId,
    String adrDisplayId,
    String sourceElement,
    ArchitectureElementType sourceElementType,
    RelationshipType relationshipType,
    String targetElement,
    ArchitectureElementType targetElementType,
    RuleDecision decision,
    String description,
    RuleSeverity severity,
    Instant createdAt,
    Instant updatedAt) {}
