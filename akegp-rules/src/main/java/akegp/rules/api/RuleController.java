package akegp.rules.api;

import akegp.rules.service.RuleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/projects/{projectId}/rules")
public class RuleController {
  private final RuleService ruleService;

  public RuleController(RuleService ruleService) {
    this.ruleService = ruleService;
  }

  @PostMapping
  public ResponseEntity<RuleResponse> create(
      @PathVariable("projectId") UUID projectId, @Valid @RequestBody CreateRuleRequest request) {
    RuleResponse response = ruleService.create(projectId, request);
    return ResponseEntity.created(URI.create("/api/rules/projects/" + projectId + "/rules/" + response.id()))
        .body(response);
  }

  @GetMapping
  public List<RuleResponse> findAll(@PathVariable("projectId") UUID projectId) {
    return ruleService.findAll(projectId);
  }

  @GetMapping("/{ruleUuid}")
  public RuleResponse findById(
      @PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid) {
    return ruleService.findById(projectId, ruleUuid);
  }

  @PutMapping("/{ruleUuid}")
  public RuleResponse update(
      @PathVariable("projectId") UUID projectId,
      @PathVariable("ruleUuid") UUID ruleUuid,
      @Valid @RequestBody UpdateRuleRequest request) {
    return ruleService.update(projectId, ruleUuid, request);
  }

  @DeleteMapping("/{ruleUuid}")
  public ResponseEntity<Void> delete(
      @PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid) {
    ruleService.delete(projectId, ruleUuid);
    return ResponseEntity.noContent().build();
  }
}
