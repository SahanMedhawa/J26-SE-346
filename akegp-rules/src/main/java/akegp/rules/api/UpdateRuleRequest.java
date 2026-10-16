package akegp.rules.api;

import akegp.rules.domain.ArchitectureElementType;
import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import akegp.rules.domain.RuleSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateRuleRequest(
    @NotBlank(message = "Rule ID is required")
        @Pattern(regexp = "^R-[0-9]{3,}$", message = "Rule ID must follow the format R-001")
        String ruleId,
    @NotNull(message = "ADR ID is required") UUID adrId,
    @NotBlank(message = "Source element is required")
        @Size(max = 200, message = "Source element must not exceed 200 characters")
        String sourceElement,
    @NotNull(message = "Source element type is required") ArchitectureElementType sourceElementType,
    @NotNull(message = "Relationship type is required") RelationshipType relationshipType,
    @NotBlank(message = "Target element is required")
        @Size(max = 200, message = "Target element must not exceed 200 characters")
        String targetElement,
    @NotNull(message = "Target element type is required") ArchitectureElementType targetElementType,
    @NotNull(message = "Decision is required") RuleDecision decision,
    @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,
    @NotNull(message = "Severity is required") RuleSeverity severity) {}
