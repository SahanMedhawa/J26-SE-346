package akegp.rules.api;

import akegp.rules.domain.*;
import akegp.rules.validation.StructuralValidationStatus;
import java.util.List;

public record ExportedRuleResponse(String ruleId, String adrId, String sourceElement,
    ArchitectureElementType sourceElementType, RelationshipType relationshipType, String targetElement,
    ArchitectureElementType targetElementType, RuleDecision decision, RuleSeverity severity,
    StructuralValidationStatus validationStatus, ArchitectReviewStatus architectReviewStatus,
    List<String> sourceFiles, List<String> targetFiles) {}
