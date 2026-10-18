package akegp.rules.domain;

import akegp.rules.validation.StructuralValidationStatus;
import java.time.Instant;
import java.util.UUID;

public record StructuralValidationRecord(UUID id, UUID ruleUuid, StructuralValidationStatus status,
    String reason, String matchedSignatureId, ArchitectureStyle architectureStyle, String catalogueVersion,
    String structuralFingerprint, long structuralRevision, Instant validatedAt) {}
