package akegp.rules.api;

import akegp.rules.domain.ArchitectReviewStatus;
import akegp.rules.validation.StructuralValidationStatus;
import java.util.List;
import java.util.UUID;

public record ArchitectReviewResponse(UUID ruleUuid, String ruleId, UUID adrUuid, String adrDisplayId,
    Integer adrVersion, StructuralValidationStatus structuralValidationStatus, boolean structuralValidationCurrent,
    ArchitectReviewStatus reviewStatus, boolean confirmationEligible, List<String> blockedReasons,
    String evidenceFingerprint, long evidenceRevision, int sourceFileCount, int targetFileCount,
    ArchitectReviewHistoryResponse latestReview) {}
