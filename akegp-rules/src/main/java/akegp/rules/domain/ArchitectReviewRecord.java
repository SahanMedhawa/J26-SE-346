package akegp.rules.domain;

import akegp.rules.validation.StructuralValidationStatus;
import java.time.Instant;
import java.util.UUID;

public record ArchitectReviewRecord(UUID id, UUID ruleUuid, UUID projectUuid,
    ArchitectReviewDecision decision, String reviewer, String comments, Instant decidedAt,
    String ruleFingerprint, UUID adrUuid, Integer adrVersion, String adrFingerprint,
    String mappingFingerprint, String evidenceFingerprint, long reviewRevision,
    StructuralValidationStatus validationStatus, String validationFingerprint) {}
