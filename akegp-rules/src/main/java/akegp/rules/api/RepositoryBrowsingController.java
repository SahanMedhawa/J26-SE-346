package akegp.rules.api;

import akegp.rules.service.RepositoryBrowsingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/projects/{projectId}/repository/files")
public class RepositoryBrowsingController {
  private final RepositoryBrowsingService service;

  public RepositoryBrowsingController(RepositoryBrowsingService service) {
    this.service = service;
  }

  @GetMapping
  public RepositoryFilesResponse files(@PathVariable("projectId") UUID projectId) {
    return service.files(projectId);
  }
}
