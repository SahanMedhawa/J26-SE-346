package akegp.enforcement.api;

import akegp.enforcement.config.EnforcementProperties;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.enforcement.review.FixVerificationService;
import akegp.enforcement.review.ReviewService;
import akegp.enforcement.service.DashboardStats;
import akegp.enforcement.service.DashboardStatsService;
import akegp.enforcement.service.EnforcementException;
import akegp.enforcement.service.EnforcementService;
import akegp.enforcement.service.PullRequestGraphScanCoordinator;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry points of the enforcement module: the pipeline posts events here, the dashboard and reviewers read and decide. */
@RestController
@RequestMapping("/api/enforcement")
public class PipelineEnforcementApi {
  private final EnforcementService enforcement;
  private final ReviewService review;
  private final FixVerificationService fixes;
  private final DashboardStatsService stats;
  private final EnforcementRepository repository;
  private final EnforcementProperties props;
  private final PullRequestGraphScanCoordinator graphScans;

  public PipelineEnforcementApi(EnforcementService enforcement, ReviewService review, FixVerificationService fixes,
                                DashboardStatsService stats, EnforcementRepository repository, EnforcementProperties props,
                                PullRequestGraphScanCoordinator graphScans) {
    this.enforcement = enforcement;
    this.review = review;
    this.fixes = fixes;
    this.stats = stats;
    this.repository = repository;
    this.props = props;
    this.graphScans = graphScans;
  }

  @PostMapping("/pipeline-events")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> pipelineEvent(@RequestHeader(value = "x-api-key", required = false) String apiKey,
                                           @RequestBody Map<String, Object> body) {
    if (apiKey == null || !apiKey.equals(props.getApiKey())) throw new EnforcementException(401, "invalid or missing x-api-key");
    PipelineEvent event = PipelineEventValidator.parse(body);
    PipelineRun run = enforcement.handle(event);
    graphScans.scan(run);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("runId", run.getId());
    out.put("state", run.getState().wire());
    out.put("classification", run.getClassification().wire());
    out.put("violations", run.getViolations().stream().map(RunView::violation).toList());
    return out;
  }

  /** The review queue: the latest run of every pull request. */
  @GetMapping("/prs")
  public List<Map<String, Object>> prs(@RequestParam(value="repo", required=false) String repo) {
    return repository.latestPerPr().stream().filter(r -> repo == null || r.getRepo().equalsIgnoreCase(repo)).map(RunView::of).toList();
  }

  /** Every scan of one pull request, newest first (what the dashboard shows behind "earlier"). */
  @GetMapping("/prs/{owner}/{name}/{pr}/history")
  public List<Map<String, Object>> history(@PathVariable("owner") String owner, @PathVariable("name") String name, @PathVariable("pr") int pr) {
    return RunView.history(repository.listRuns(5000), owner + "/" + name, pr);
  }

  @PostMapping("/prs/{owner}/{name}/{pr}/decision")
  public Map<String, Object> decide(@PathVariable("owner") String owner, @PathVariable("name") String name, @PathVariable("pr") int pr,
                                    @RequestBody Map<String, Object> body) {
    PipelineRun run;
    if ("apply_fix".equals(str(body, "action"))) {
      run = fixes.verify(owner + "/" + name, pr, str(body, "reviewer"));   // reads the pull request from GitHub
    } else {
      run = review.decide(owner + "/" + name, pr, str(body, "action"), str(body, "reviewer"),
          str(body, "justification"), str(body, "comment"));
    }
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
  public DashboardStats stats(@RequestParam(value="repo", required=false) String repo) {
    return repo == null ? stats.stats() : stats.statsForRepository(repo);
  }

  private static String str(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    return v == null ? null : v.toString();
  }
}
