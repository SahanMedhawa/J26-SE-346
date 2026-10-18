package akegp.rules.api;

import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.validation.ArvmCatalogue;
import akegp.rules.validation.ArvmSignature;
import akegp.rules.validation.RuleStructureValidationService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleStructureValidationController {
  private final RuleStructureValidationService validation;
  private final ArvmCatalogue catalogue;

  public RuleStructureValidationController(
      RuleStructureValidationService validation, ArvmCatalogue catalogue) {
    this.validation = validation;
    this.catalogue = catalogue;
  }

  @PostMapping("/projects/{projectId}/rules/{ruleUuid}/validate-structure")
  public StructuralValidationResponse validate(
      @PathVariable("projectId") UUID projectId,
      @PathVariable("ruleUuid") UUID ruleUuid,
      @RequestBody(required = false) ValidateStructureRequest request) {
    return validation.validate(
        projectId, ruleUuid, request == null ? null : request.architectureStyle());
  }

  @GetMapping("/arvm/signatures")
  public List<ArvmSignature> signatures(
      @RequestParam(name = "architectureStyle", required = false) ArchitectureStyle style) {
    return catalogue.signatures(style);
  }
}
