package akegp.enforcement.api;

import akegp.enforcement.config.EnforcementProperties;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.enforcement.review.ReviewService;
import akegp.enforcement.service.DashboardStats;
import akegp.enforcement.service.DashboardStatsService;
import akegp.enforcement.service.EnforcementException;
import akegp.enforcement.service.EnforcementService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry points of the enforcement module: the pipeline posts events here, the dashboard and reviewers read and decide. */
@RestController
@RequestMapping("/api/enforcement")
public class PipelineEnforcementApi {
  private final EnforcementService enforcement;
  private final ReviewService review;
  private final DashboardStatsService stats;
  private final EnforcementRepository repository;
  private final EnforcementProperties props;

  public PipelineEnforcementApi(EnforcementService enforcement, ReviewService review, DashboardStatsService stats,
                                EnforcementRepository repository, EnforcementProperties props) {
    this.enforcement = enforcement;
    this.review = review;
    this.stats = stats;
    this.repository = repository;
    this.props = props;
  }

  @PostMapping("/pipeline-events")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> pipelineEvent(@RequestHeader(value = "x-api-key", required = false) String apiKey,
                                           @RequestBody Map<String, Object> body) {
    if (apiKey == null || !apiKey.equals(props.getApiKey())) throw new EnforcementException(401, "invalid or missing x-api-key");
    PipelineEvent event = PipelineEventValidator.parse(body);
    PipelineRun run = enforcement.handle(event);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("runId", run.getId());
    out.put("state", run.getState().wire());
    out.put("classification", run.getClassification().wire());
    out.put("violations", run.getViolations().stream().map(RunView::violation).toList());
    return out;
  }

  /** The review queue: the latest run of every pull request. */
  @GetMapping("/prs")
  public List<Map<String, Object>> prs() {
    return repository.latestPerPr().stream().map(RunView::of).toList();
  }

  @PostMapping("/prs/{owner}/{name}/{pr}/decision")
  public Map<String, Object> decide(@PathVariable String owner, @PathVariable String name, @PathVariable int pr,
                                    @RequestBody Map<String, Object> body) {
    PipelineRun run = review.decide(owner + "/" + name, pr, str(body, "action"), str(body, "reviewer"),
        str(body, "justification"), str(body, "comment"));
    return RunView.of(run);
  }

  @GetMapping("/audit")
  public List<Map<String, Object>> audit() {
    return repository.auditLog().stream().map(a -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", a.id());
      m.put("at", a.at().toString());
      m.put("type", a.type());
      m.put("repo", a.repo());
      m.put("prNumber", a.prNumber());
      m.put("detail", a.detail());
      return m;
    }).toList();
  }

  @GetMapping("/stats")
  public DashboardStats stats() { return stats.stats(); }

  private static String str(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    return v == null ? null : v.toString();
  }
}