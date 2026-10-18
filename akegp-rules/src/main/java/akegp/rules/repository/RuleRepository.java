package akegp.rules.repository;

import akegp.rules.domain.ArchitectureRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleRepository {
  ArchitectureRule insert(ArchitectureRule rule);

  List<ArchitectureRule> findByProject(UUID projectId);

  Optional<ArchitectureRule> findById(UUID projectId, UUID id);

  boolean existsByRuleId(UUID projectId, String ruleId);

  ArchitectureRule update(ArchitectureRule rule);

  boolean delete(UUID projectId, UUID id);
}
