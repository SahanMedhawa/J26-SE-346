package akegp.conformance.api;

import akegp.conformance.domain.CodeGraph;
import akegp.conformance.domain.ConformanceScan;
import akegp.conformance.domain.ConformanceScanResult;
import akegp.conformance.service.CodeGraphService;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/conformance/graphs")
public class CodeGraphController {
  private final CodeGraphService service;

  public CodeGraphController(CodeGraphService service) {
    this.service = service;
  }

  @PostMapping("/import")
  @ResponseStatus(HttpStatus.CREATED)
  public ConformanceScanResult importRepository(@RequestBody ImportRequest request) throws IOException, InterruptedException {
    if (request.repositoryId() == null || request.repositoryId().isBlank()
        || request.repositoryUrl() == null || request.repositoryUrl().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repositoryId and repositoryUrl are required");
    }
    if (request.languages() != null && request.languages().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one language must be selected");
    }
    return service.importRepository(request.repositoryId(), request.repositoryUrl(), request.languages());
  }

  @GetMapping("/history")
  public List<ConformanceScan> history() {
    return service.listScans();
  }

  @GetMapping("/{repositoryId}")
  public CodeGraph graph(@PathVariable("repositoryId") String repositoryId) {
    return service.find(repositoryId);
  }

  public record ImportRequest(String repositoryId, String repositoryUrl, Set<String> languages) {}
}
