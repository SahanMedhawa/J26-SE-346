package akegp.app;

import org.springframework.web.bind.annotation.*;

@RestController
public class RepositoryActivityController {
  private final PublicRepositoryHistory history;
  public RepositoryActivityController(PublicRepositoryHistory history) { this.history = history; }
  @GetMapping("/api/analytics/repository/activity")
  public PublicRepositoryHistory.Overview activity(@RequestParam("url") String url) { return history.read(url); }
  @ExceptionHandler(akegp.rules.service.RepositoryBrowsingException.class)
  org.springframework.http.ResponseEntity<java.util.Map<String, String>> invalid(akegp.rules.service.RepositoryBrowsingException e) {
    return org.springframework.http.ResponseEntity.status(e.status()).body(java.util.Map.of("message", e.getMessage()));
  }
}
