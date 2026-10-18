package akegp.rules;

import akegp.rules.domain.ArchitectReviewRecord;
import akegp.rules.domain.StructuralValidationRecord;
import akegp.rules.repository.RuleAssuranceRepository;
import java.util.*;

/** Test-only storage; production uses JDBC and V6 triggers for revision tracking. */
class TestRuleAssuranceRepository implements RuleAssuranceRepository {
  RuntimeException saveFailure;
  final Map<UUID, StructuralValidationRecord> validations = new HashMap<>();
  final Map<UUID, List<ArchitectReviewRecord>> history = new HashMap<>();
  final Map<UUID, Revisions> revisions = new HashMap<>();

  public boolean lockProject(UUID projectId) { return true; }
  public boolean lockRule(UUID projectId, UUID ruleUuid) { return true; }
  public boolean lockAdr(UUID projectId, UUID adrUuid) { return true; }
  public Revisions revisions(UUID ruleUuid) { return revisions.getOrDefault(ruleUuid, new Revisions(0, 0)); }
  public Optional<StructuralValidationRecord> validation(UUID ruleUuid) { return Optional.ofNullable(validations.get(ruleUuid)); }
  public void saveValidation(StructuralValidationRecord value) {
    if (saveFailure != null) throw saveFailure;
    validations.put(value.ruleUuid(), value);
  }
  public void appendReview(ArchitectReviewRecord value) { history.computeIfAbsent(value.ruleUuid(), k -> new ArrayList<>()).addFirst(value); }
  public List<ArchitectReviewRecord> reviews(UUID ruleUuid) { return List.copyOf(history.getOrDefault(ruleUuid, List.of())); }

  void advance(UUID ruleUuid, boolean structural) {
    var old = revisions(ruleUuid);
    revisions.put(ruleUuid, new Revisions(old.structural() + (structural ? 1 : 0), old.review() + 1));
  }
}
