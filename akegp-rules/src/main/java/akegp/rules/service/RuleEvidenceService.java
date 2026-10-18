package akegp.rules.service;

import akegp.rules.domain.*;
import akegp.rules.repository.ImplementationMappingRepository;
import akegp.rules.repository.RuleAssuranceRepository;
import akegp.rules.repository.RuleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleEvidenceService {
  private final ProjectRegistrationService projects;
  private final RuleRepository rules;
  private final ADRService adrs;
  private final ImplementationMappingRepository mappings;
  private final RuleAssuranceRepository assurance;

  public RuleEvidenceService(ProjectRegistrationService projects, RuleRepository rules, ADRService adrs,
      ImplementationMappingRepository mappings, RuleAssuranceRepository assurance) {
    this.projects = projects; this.rules = rules; this.adrs = adrs;
    this.mappings = mappings; this.assurance = assurance;
  }

  @Transactional(value = "rulesTransactionManager", propagation = Propagation.MANDATORY)
  public Evidence load(UUID projectId, UUID ruleUuid) {
    return snapshot(projectId, ruleUuid, true);
  }

  /** Read-only consumers must provide a consistent transaction snapshot instead of taking write locks. */
  @Transactional(value = "rulesTransactionManager", propagation = Propagation.MANDATORY)
  public Evidence read(UUID projectId, UUID ruleUuid) {
    return snapshot(projectId, ruleUuid, false);
  }

  private Evidence snapshot(UUID projectId, UUID ruleUuid, boolean lock) {
    // Stable lock order: project, rule, ADR. Existing UPDATE/DELETE statements conflict with these locks.
    if (lock && !assurance.lockProject(projectId)) throw new ProjectNotFoundException(projectId);
    var project = projects.findById(projectId);
    if (lock && !assurance.lockRule(projectId, ruleUuid)) throw new RuleNotFoundException(ruleUuid);
    var rule = rules.findById(projectId, ruleUuid).orElseThrow(() -> new RuleNotFoundException(ruleUuid));
    ArchitectureDecisionRecord adr = null;
    if (rule.adrId() != null && (!lock || assurance.lockAdr(projectId, rule.adrId()))) {
      try { adr = adrs.findById(projectId, rule.adrId()); }
      catch (ADRNotFoundException ignored) { /* Review workspace must explain missing evidence. */ }
    }
    return new Evidence(project, rule, adr, mappings.findByRule(ruleUuid), assurance.revisions(ruleUuid));
  }

  public record Evidence(RegisteredProject project, ArchitectureRule rule, ArchitectureDecisionRecord adr,
      List<RuleImplementationMapping> mappings, RuleAssuranceRepository.Revisions revisions) {}
}
