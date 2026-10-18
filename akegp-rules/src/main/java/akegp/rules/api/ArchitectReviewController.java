package akegp.rules.api;

import akegp.rules.service.ArchitectReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rules/projects/{projectId}/rules/{ruleUuid}/architect-review")
public class ArchitectReviewController {
  private final ArchitectReviewService service;

  public ArchitectReviewController(ArchitectReviewService service) { this.service = service; }

  @GetMapping
  public ArchitectReviewResponse find(@PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid) {
    return service.find(projectId, ruleUuid);
  }

  @PostMapping
  public ArchitectReviewResponse submit(@PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid,
      @Valid @RequestBody ArchitectReviewRequest request) {
    return service.submit(projectId, ruleUuid, request);
  }

  @GetMapping("/history")
  public List<ArchitectReviewHistoryResponse> history(@PathVariable("projectId") UUID projectId, @PathVariable("ruleUuid") UUID ruleUuid) {
    return service.history(projectId, ruleUuid);
  }
}
