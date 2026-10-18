package akegp.rules.repository;

import akegp.rules.domain.RuleImplementationMapping;
import java.util.List;
import java.util.UUID;

public interface ImplementationMappingRepository {
  boolean lockRule(UUID projectId, UUID ruleUuid);

  List<RuleImplementationMapping> findByRule(UUID ruleUuid);

  void insert(RuleImplementationMapping mapping);

  void delete(UUID ruleUuid, UUID mappingId);
}
