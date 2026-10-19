package akegp.rules.api;

import akegp.rules.domain.ArchitectureElementType;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import akegp.rules.validation.StructuralValidationStatus;
import java.util.UUID;

public record StructuralValidationResponse(
    String ruleId,
    UUID ruleUuid,
    UUID projectId,
    ArchitectureStyle architectureStyle,
    ArchitectureElementType sourceElementType,
    RelationshipType relationshipType,
    ArchitectureElementType targetElementType,
    RuleDecision decision,
    StructuralValidationStatus validationStatus,
    String matchedSignatureId,
    String message) {}
