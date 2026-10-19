package akegp.rules.api;

import akegp.rules.service.ImplementationMappingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/projects/{projectId}/rules/{ruleUuid}/implementation-mappings")
public class ImplementationMappingController {
  private final ImplementationMappingService service;

  public ImplementationMappingController(ImplementationMappingService service) {
    this.service = service;
  }

  @GetMapping
  public ImplementationMappingsResponse findByRule(
      @PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid) {
    return service.findByRule(projectId, ruleUuid);
  }

  @PutMapping
  public ImplementationMappingsResponse replace(
      @PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid,
      @Valid @RequestBody ImplementationMappingsRequest request) {
    return service.replace(projectId, ruleUuid, request);
  }
}
