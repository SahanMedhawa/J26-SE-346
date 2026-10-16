package akegp.rules.service;

import akegp.rules.api.CreateRuleRequest;
import akegp.rules.api.RuleResponse;
import akegp.rules.api.UpdateRuleRequest;
import akegp.rules.domain.ArchitectureRule;
import akegp.rules.domain.ProjectStatus;
import akegp.rules.repository.RuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleServiceImpl implements RuleService {
  private final RuleRepository ruleRepository;
  private final ProjectRegistrationService projectService;
  private final ADRService adrService;

  public RuleServiceImpl(
      RuleRepository ruleRepository,
      ProjectRegistrationService projectService,
      ADRService adrService) {
    this.ruleRepository = ruleRepository;
    this.projectService = projectService;
    this.adrService = adrService;
  }

  @Override
  @Transactional
  public RuleResponse create(UUID projectId, CreateRuleRequest request) {
    requireActiveProject(projectId);
    String ruleId = normalize(request.ruleId());
    if (ruleRepository.existsByRuleId(projectId, ruleId)) {
      throw new DuplicateRuleException(ruleId);
    }
    var adr = adrService.findById(projectId, request.adrId());
    Instant now = Instant.now();
    return toResponse(
        ruleRepository.insert(
            new ArchitectureRule(
                UUID.randomUUID(), ruleId, projectId, adr.id(), normalize(request.sourceElement()),
                request.sourceElementType(), request.relationshipType(), normalize(request.targetElement()),
                request.targetElementType(), request.decision(), normalize(request.description()),
                request.severity(), now, now)),
        adr.adrId());
  }

  @Override
  public List<RuleResponse> findAll(UUID projectId) {
    projectService.findById(projectId);
    return ruleRepository.findByProject(projectId).stream()
        .map(rule -> toResponse(rule, adrService.findById(projectId, rule.adrId()).adrId()))
        .toList();
  }

  @Override
  public RuleResponse findById(UUID projectId, UUID ruleUuid) {
    projectService.findById(projectId);
    ArchitectureRule rule =
        ruleRepository.findById(projectId, ruleUuid).orElseThrow(() -> new RuleNotFoundException(ruleUuid));
    return toResponse(rule, adrService.findById(projectId, rule.adrId()).adrId());
  }

  @Override
  @Transactional
  public RuleResponse update(UUID projectId, UUID ruleUuid, UpdateRuleRequest request) {
    requireActiveProject(projectId);
    ArchitectureRule existing =
        ruleRepository.findById(projectId, ruleUuid).orElseThrow(() -> new RuleNotFoundException(ruleUuid));
    String ruleId = normalize(request.ruleId());
    if (!existing.ruleId().equals(ruleId) && ruleRepository.existsByRuleId(projectId, ruleId)) {
      throw new DuplicateRuleException(ruleId);
    }
    var adr = adrService.findById(projectId, request.adrId());
    ArchitectureRule updated =
        new ArchitectureRule(
            existing.id(), ruleId, existing.projectId(), adr.id(), normalize(request.sourceElement()),
            request.sourceElementType(), request.relationshipType(), normalize(request.targetElement()),
            request.targetElementType(), request.decision(), normalize(request.description()),
            request.severity(), existing.createdAt(), Instant.now());
    return toResponse(ruleRepository.update(updated), adr.adrId());
  }

  @Override
  @Transactional
  public void delete(UUID projectId, UUID ruleUuid) {
    requireActiveProject(projectId);
    if (ruleRepository.findById(projectId, ruleUuid).isEmpty()
        || !ruleRepository.delete(projectId, ruleUuid)) {
      throw new RuleNotFoundException(ruleUuid);
    }
  }

  private void requireActiveProject(UUID projectId) {
    if (projectService.findById(projectId).status() != ProjectStatus.ACTIVE) {
      throw new InactiveProjectException();
    }
  }

  private static String normalize(String value) {
    return value.trim();
  }

  private static RuleResponse toResponse(ArchitectureRule rule, String adrDisplayId) {
    return new RuleResponse(
        rule.id(), rule.ruleId(), rule.projectId(), rule.adrId(), adrDisplayId, rule.sourceElement(),
        rule.sourceElementType(), rule.relationshipType(), rule.targetElement(),
        rule.targetElementType(), rule.decision(), rule.description(), rule.severity(),
        rule.createdAt(), rule.updatedAt());
  }
}
