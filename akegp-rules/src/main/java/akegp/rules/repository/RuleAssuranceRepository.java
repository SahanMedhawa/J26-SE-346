package akegp.rules.repository;

import akegp.rules.domain.ArchitectReviewRecord;
import akegp.rules.domain.StructuralValidationRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleAssuranceRepository {
  boolean lockProject(UUID projectId);
  boolean lockRule(UUID projectId, UUID ruleUuid);
  boolean lockAdr(UUID projectId, UUID adrUuid);
  Revisions revisions(UUID ruleUuid);
  Optional<StructuralValidationRecord> validation(UUID ruleUuid);
  void saveValidation(StructuralValidationRecord record);
  void appendReview(ArchitectReviewRecord record);
  List<ArchitectReviewRecord> reviews(UUID ruleUuid);

  record Revisions(long structural, long review) {}
}
