package akegp.rules.api;

import akegp.rules.domain.ArchitectReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ArchitectReviewRequest(@NotNull ArchitectReviewDecision decision,
    @NotBlank @Size(max = 150) String reviewer, @Size(max = 4000) String comments,
    @Pattern(regexp = "[a-f0-9]{64}", message = "Expected evidence fingerprint must be a SHA-256 value")
    String expectedEvidenceFingerprint) {}
