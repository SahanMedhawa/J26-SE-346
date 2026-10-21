package akegp.rules.validation;

import akegp.rules.api.UpdateRuleRequest;
import akegp.rules.api.StructuralValidationResponse;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.repository.RuleRepository;
import akegp.rules.service.ADRService;
import akegp.rules.service.ProjectRegistrationService;
import akegp.rules.service.RuleNotFoundException;
import jakarta.validation.Validator;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RuleStructureValidationService {
  private final RuleRepository rules;
  private final ProjectRegistrationService projects;
  private final ADRService adrs;
  private final Validator validator;
  private final ArvmCatalogue catalogue;

  public RuleStructureValidationService(
      RuleRepository rules, ProjectRegistrationService projects, ADRService adrs,
      Validator validator, ArvmCatalogue catalogue) {
    this.rules = rules;
    this.projects = projects;
    this.adrs = adrs;
    this.validator = validator;
    this.catalogue = catalogue;
  }

  public StructuralValidationResponse validate(
      UUID projectId, UUID ruleUuid, ArchitectureStyle requestedStyle) {
    var project = projects.findById(projectId);
    var rule = rules.findById(projectId, ruleUuid)
        .orElseThrow(() -> new RuleNotFoundException(ruleUuid));
    if (!projectId.equals(rule.projectId())) {
      throw new RuleNotFoundException(ruleUuid);
    }
    if (rule.id() == null || !ruleUuid.equals(rule.id())) {
      throw new RuleValidationException("Saved rule UUID is missing or invalid.");
    }
    // Reuse the existing CRUD constraints, including required enums and text limits.
    if (rule.ruleId() == null || rule.ruleId().isBlank()) {
      throw new RuleValidationException("Saved rule contains invalid or missing required information: ruleId");
    }
    var fields = new UpdateRuleRequest(
        rule.ruleId(), rule.adrId(), rule.sourceElement(), rule.sourceElementType(),
        rule.relationshipType(), rule.targetElement(), rule.targetElementType(),
        rule.decision(), rule.description(), rule.severity());
    var violations = validator.validate(fields);
    if (!violations.isEmpty()) {
      throw new RuleValidationException("Saved rule contains invalid or missing required information: "
          + violations.stream().map(v -> v.getPropertyPath().toString()).sorted()
              .collect(java.util.stream.Collectors.joining(", ")));
    }
    adrs.findById(projectId, rule.adrId());
    var style = resolveStyle(project.architectureStyles(), requestedStyle);
    var match = catalogue.signatures(style).stream()
        .filter(s -> s.sourceElementType() == rule.sourceElementType()
            && s.relationshipType() == rule.relationshipType()
            && s.targetElementType() == rule.targetElementType())
        .findFirst();
    return new StructuralValidationResponse(
        rule.ruleId(), rule.id(), projectId, style, rule.sourceElementType(),
        rule.relationshipType(), rule.targetElementType(), rule.decision(),
        match.isPresent() ? StructuralValidationStatus.SUPPORTED : StructuralValidationStatus.UNSUPPORTED,
        match.map(ArvmSignature::signatureId).orElse(null),
        match.isPresent() ? "Structural signature is recognized by ARVM."
            : "No matching structural signature exists in the current ARVM catalogue.");
  }

  private static ArchitectureStyle resolveStyle(
      Set<ArchitectureStyle> registered, ArchitectureStyle requested) {
    ArvmCatalogue.requireSupportedStyle(requested);
    var applicable = EnumSet.noneOf(ArchitectureStyle.class);
    applicable.addAll(registered);
    if (applicable.remove(ArchitectureStyle.MICROSERVICES_AND_LAYERED)) {
      applicable.add(ArchitectureStyle.MICROSERVICES);
      applicable.add(ArchitectureStyle.LAYERED);
    }
    if (requested != null) {
      if (!applicable.contains(requested)) {
        throw new RuleValidationException("Architecture style is not registered for this project.");
      }
      return requested;
    }
    if (applicable.size() != 1) {
      throw new RuleValidationException(
          "An architectureStyle selection is required when the project has multiple or no styles.");
    }
    return applicable.iterator().next();
  }
}
