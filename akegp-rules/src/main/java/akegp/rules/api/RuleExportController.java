package akegp.rules.api;

import akegp.rules.service.RuleExportService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/projects/{projectId}/export")
public class RuleExportController {
  private final RuleExportService service;

  public RuleExportController(RuleExportService service) { this.service = service; }

  @GetMapping
  public RuleExportResponse export(@PathVariable("projectId") UUID projectId) {
    return service.export(projectId);
  }
}
