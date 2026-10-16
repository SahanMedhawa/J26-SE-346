package akegp.rules.domain;

import java.time.Instant;
import java.util.UUID;

public record ArchitectureRule(
    UUID id,
    String ruleId,
    UUID projectId,
    UUID adrId,
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
