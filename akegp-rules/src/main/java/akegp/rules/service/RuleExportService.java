package akegp.rules.service;

import akegp.rules.api.ExportedRuleResponse;
import akegp.rules.api.RuleExportResponse;
import akegp.rules.domain.*;
import akegp.rules.repository.RuleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleExportService {
  private final ProjectRegistrationService projects;
  private final RuleRepository rules;
  private final RuleEvidenceService evidence;
  private final ArchitectReviewService reviews;

  public RuleExportService(ProjectRegistrationService projects, RuleRepository rules,
      RuleEvidenceService evidence, ArchitectReviewService reviews) {
    this.projects = projects; this.rules = rules; this.evidence = evidence; this.reviews = reviews;
  }

  /** Every exported field and eligibility check sees the same PostgreSQL snapshot. Nothing is persisted. */
  @Transactional(value = "rulesTransactionManager", readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RuleExportResponse export(UUID projectId) {
    var project = projects.findById(projectId);
    var exported = new ArrayList<ExportedRuleResponse>();
    if (project.status() == ProjectStatus.ACTIVE) {
      for (var candidate : rules.findByProject(projectId)) {
        var snapshot = evidence.read(projectId, candidate.id());
        var review = reviews.current(snapshot);
        // Review eligibility already covers current supported validation, linked ADR, and both mapping sides.
        // PP1 accepts proposed or accepted ADR evidence already reviewed by the architect.
        // Retired ADRs remain excluded even if their current contents were explicitly reviewed.
        if (!review.confirmationEligible() || review.reviewStatus() != ArchitectReviewStatus.CONFIRMED
            || snapshot.adr() == null || snapshot.adr().status() == ADRStatus.DEPRECATED
            || snapshot.adr().status() == ADRStatus.SUPERSEDED) continue;
        var rule = snapshot.rule();
        exported.add(new ExportedRuleResponse(rule.ruleId(), snapshot.adr().adrId(), rule.sourceElement(),
            rule.sourceElementType(), rule.relationshipType(), rule.targetElement(), rule.targetElementType(),
            rule.decision(), rule.severity(), review.structuralValidationStatus(), review.reviewStatus(),
            paths(snapshot.mappings(), ImplementationMappingSide.SOURCE), paths(snapshot.mappings(), ImplementationMappingSide.TARGET)));
      }
    }
    return new RuleExportResponse(project.id(), project.name(), project.repositoryUrl(), style(project.architectureStyles()), exported);
  }

  private static List<String> paths(List<RuleImplementationMapping> mappings, ImplementationMappingSide side) {
    // Retain the existing repository order and exact stored strings; do not normalize or re-sort for export.
    return mappings.stream().filter(mapping -> mapping.side() == side).map(RuleImplementationMapping::filePath).toList();
  }

  private static ArchitectureStyle style(Set<ArchitectureStyle> styles) {
    if (styles.isEmpty()) return null;
    if (styles.size() == 1) return styles.iterator().next();
    return ArchitectureStyle.MICROSERVICES_AND_LAYERED;
  }
}
