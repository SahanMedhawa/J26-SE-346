package akegp.rules.repository;

import akegp.rules.domain.ArchitectureRule;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;

@Profile("test")
public class InMemoryRuleRepository implements RuleRepository {
  private final Map<UUID, ArchitectureRule> records = new ConcurrentHashMap<>();

  @Override
  public ArchitectureRule insert(ArchitectureRule rule) {
    records.put(rule.id(), rule);
    return rule;
  }

  @Override
  public List<ArchitectureRule> findByProject(UUID projectId) {
    return records.values().stream()
        .filter(rule -> rule.projectId().equals(projectId))
        .sorted(Comparator.comparing(ArchitectureRule::updatedAt).reversed())
        .toList();
  }

  @Override
  public Optional<ArchitectureRule> findById(UUID projectId, UUID id) {
    return Optional.ofNullable(records.get(id)).filter(rule -> rule.projectId().equals(projectId));
  }

  @Override
  public boolean existsByRuleId(UUID projectId, String ruleId) {
    return records.values().stream()
        .anyMatch(rule -> rule.projectId().equals(projectId) && rule.ruleId().equals(ruleId));
  }

  @Override
  public ArchitectureRule update(ArchitectureRule rule) {
    records.put(rule.id(), rule);
    return rule;
  }

  @Override
  public boolean delete(UUID projectId, UUID id) {
    return findById(projectId, id).map(rule -> records.remove(id, rule)).orElse(false);
  }
}
