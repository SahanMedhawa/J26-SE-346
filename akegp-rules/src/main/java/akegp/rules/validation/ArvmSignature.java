package akegp.rules.validation;

import akegp.rules.domain.ArchitectureElementType;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.RelationshipType;

public record ArvmSignature(
    String signatureId,
    ArchitectureStyle architectureStyle,
    ArchitectureElementType sourceElementType,
    RelationshipType relationshipType,
    ArchitectureElementType targetElementType,
    String description) {}
