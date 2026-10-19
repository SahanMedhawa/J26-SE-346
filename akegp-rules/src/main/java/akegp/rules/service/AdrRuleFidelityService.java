package akegp.rules.service;

import akegp.rules.api.AdrRuleFidelityResponse;
import akegp.rules.domain.AdrRuleFidelityStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import akegp.rules.domain.ArchitectureRule;
import akegp.rules.validation.AdrDecisionFidelityParser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdrRuleFidelityService {
  private final RuleEvidenceService evidence;

  public AdrRuleFidelityService(RuleEvidenceService evidence) {
    this.evidence = evidence;
  }

  @Transactional(value = "rulesTransactionManager", readOnly = true)
  public AdrRuleFidelityResponse evaluate(UUID projectId, UUID ruleUuid) {
    return evaluate(evidence.read(projectId, ruleUuid));
  }

  public AdrRuleFidelityResponse evaluate(RuleEvidenceService.Evidence snapshot) {
    ArchitectureRule rule = snapshot.rule();
    ArchitectureDecisionRecord adr = snapshot.adr();
    if (adr == null) {
      return response(rule, null, AdrRuleFidelityStatus.REVIEW_REQUIRED,
          "Linked ADR is missing; ADR–rule fidelity cannot be evaluated.");
    }
    String decisionText = adr.decision();
    if (decisionText == null || decisionText.isBlank()) {
      return response(rule, adr, AdrRuleFidelityStatus.REVIEW_REQUIRED,
          "Linked ADR has no decision text; ADR–rule fidelity cannot be evaluated.");
    }
    var interpreted = AdrDecisionFidelityParser.parse(decisionText);
    if (interpreted.isEmpty()) {
      return response(rule, adr, AdrRuleFidelityStatus.REVIEW_REQUIRED,
          "ADR decision text is unsupported or ambiguous for PP1. Only the explicit pattern "
              + "'<Source> must not directly access <Target>' is recognized, with sources "
              + "optionally joined by 'and' or commas and separate constraint sentences.");
    }
    var relevant = interpreted.stream()
        .filter(constraint -> constraint.sourceElement().equals(rule.sourceElement())
            && constraint.relationshipType() == rule.relationshipType()
            && constraint.targetElement().equals(rule.targetElement()))
        .toList();
    if (relevant.isEmpty()) {
      return response(rule, adr, AdrRuleFidelityStatus.REVIEW_REQUIRED,
          "No recognized ADR constraint matches the linked rule's source element, relationship, "
              + "and target element; review is required.");
    }
    if (relevant.stream().allMatch(constraint -> constraint.decision() == rule.decision())) {
      return response(rule, adr, AdrRuleFidelityStatus.MATCH,
          "ADR decision text matches the linked rule for the supported PP1 pattern "
              + "(source, DIRECT_DATABASE_ACCESS, target, FORBIDDEN).");
    }
    return response(rule, adr, AdrRuleFidelityStatus.MISMATCH,
        "Recognized ADR constraint forbids direct database access for the same source and target, "
            + "contradicting the linked rule's decision: " + rule.decision() + ".");
  }

  private static AdrRuleFidelityResponse response(
      ArchitectureRule rule, ArchitectureDecisionRecord adr,
      AdrRuleFidelityStatus status, String explanation) {
    return new AdrRuleFidelityResponse(
        rule.id(), rule.ruleId(),
        adr == null ? null : adr.id(),
        adr == null ? null : adr.adrId(),
        status, explanation);
  }
}
