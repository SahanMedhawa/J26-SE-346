package akegp.rules.api;

import akegp.rules.domain.ArchitectReviewRecord;

public record ArchitectReviewHistoryResponse(ArchitectReviewRecord review, boolean current,
    boolean reviewerAuthenticated) {}
