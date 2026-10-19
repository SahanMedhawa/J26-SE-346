package akegp.rules.service;

import akegp.rules.api.ImplementationMappingsRequest;
import akegp.rules.api.ImplementationMappingsResponse;
import akegp.rules.domain.ImplementationMappingSide;
import akegp.rules.domain.RuleImplementationMapping;
import akegp.rules.repository.ImplementationMappingRepository;
import akegp.rules.repository.RuleRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImplementationMappingServiceImpl implements ImplementationMappingService {
  private final ImplementationMappingRepository mappings;
  private final RuleRepository rules;
  private final ProjectRegistrationService projects;

  public ImplementationMappingServiceImpl(ImplementationMappingRepository mappings,
      RuleRepository rules, ProjectRegistrationService projects) {
    this.mappings = mappings;
    this.rules = rules;
    this.projects = projects;
  }

  @Override
  @Transactional(value = "rulesTransactionManager", readOnly = true)
  public ImplementationMappingsResponse findByRule(UUID projectId, UUID ruleUuid) {
    projects.findById(projectId);
    if (rules.findById(projectId, ruleUuid).isEmpty()) {
      throw new RuleNotFoundException(ruleUuid);
    }
    return response(ruleUuid, mappings.findByRule(ruleUuid));
  }

  @Override
  @Transactional("rulesTransactionManager")
  public ImplementationMappingsResponse replace(
      UUID projectId, UUID ruleUuid, ImplementationMappingsRequest request) {
    projects.findById(projectId);
    // Serialize complete replacements for this rule, including when it has no mappings yet.
    if (!mappings.lockRule(projectId, ruleUuid)) {
      throw new RuleNotFoundException(ruleUuid);
    }
    List<String> sources = normalize(request.sourceFiles(), "sourceFiles");
    List<String> targets = normalize(request.targetFiles(), "targetFiles");
    List<RuleImplementationMapping> existing = mappings.findByRule(ruleUuid);
    List<RuleImplementationMapping> desired = new ArrayList<>();
    addDesired(ruleUuid, ImplementationMappingSide.SOURCE, sources, existing, desired);
    addDesired(ruleUuid, ImplementationMappingSide.TARGET, targets, existing, desired);
    existing.stream().filter(mapping -> !desired.contains(mapping))
        .forEach(mapping -> mappings.delete(ruleUuid, mapping.id()));
    desired.stream().filter(mapping -> !existing.contains(mapping)).forEach(mappings::insert);
    return response(ruleUuid, desired);
  }

  private static void addDesired(UUID ruleUuid, ImplementationMappingSide side, List<String> paths,
      List<RuleImplementationMapping> existing, List<RuleImplementationMapping> desired) {
    for (String path : paths) {
      desired.add(existing.stream()
          .filter(mapping -> mapping.side() == side && mapping.filePath().equals(path))
          .findFirst().orElseGet(() -> new RuleImplementationMapping(UUID.randomUUID(), ruleUuid, side, path)));
    }
  }

  private static List<String> normalize(List<String> paths, String field) {
    if (paths == null) {
      throw new ImplementationMappingValidationException(field + " must be an array; use [] to clear it");
    }
    Set<String> normalized = new HashSet<>();
    for (String raw : paths) {
      if (raw == null || raw.isBlank()) {
        throw new ImplementationMappingValidationException(field + " must not contain null or blank paths");
      }
      String path = raw.strip().replace('\\', '/');
      if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")
          || path.contains(":") || path.chars().anyMatch(Character::isISOControl)) {
        throw new ImplementationMappingValidationException(field + " requires repository-relative file paths: " + path);
      }
      List<String> segments = new ArrayList<>();
      for (String segment : path.split("/", -1)) {
        if (segment.equals("..")) {
          throw new ImplementationMappingValidationException(field + " must not contain parent-directory traversal: " + path);
        }
        if (!segment.isEmpty() && !segment.equals(".")) segments.add(segment);
      }
      if (segments.isEmpty() || path.endsWith("/") || path.endsWith("/.")) {
        throw new ImplementationMappingValidationException(field + " requires a file path: " + path);
      }
      path = String.join("/", segments);
      if (!normalized.add(path)) {
        throw new ImplementationMappingValidationException(field + " contains duplicate path: " + path);
      }
    }
    return normalized.stream().sorted().toList();
  }

  private static ImplementationMappingsResponse response(
      UUID ruleUuid, List<RuleImplementationMapping> values) {
    return new ImplementationMappingsResponse(ruleUuid,
        values.stream().filter(m -> m.side() == ImplementationMappingSide.SOURCE)
            .map(RuleImplementationMapping::filePath).sorted().toList(),
        values.stream().filter(m -> m.side() == ImplementationMappingSide.TARGET)
            .map(RuleImplementationMapping::filePath).sorted().toList());
  }
}
