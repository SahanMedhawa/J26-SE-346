package akegp.rules.api;

import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.validation.StructuralValidationStatus;
import java.time.Instant;
import java.util.UUID;

public record StoredStructuralValidationResponse(UUID ruleUuid, StructuralValidationStatus status,
    String reason, String matchedSignatureId, ArchitectureStyle architectureStyle, String catalogueVersion,
    String validatedFingerprint, Instant validatedAt, boolean current,
    StructuralValidationStatus lastValidationStatus, Long validatedRevision, long currentRevision) {}
