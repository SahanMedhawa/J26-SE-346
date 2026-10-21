package akegp.analytics.api;

import akegp.analytics.hotspot.HotspotRankingService;
import akegp.analytics.lifecycle.RunComparisonService;
import akegp.analytics.service.AnalyticsQueryService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics/projects/{projectId}")
public class AnalyticsController {
  private final AnalyticsQueryService queries;
  public AnalyticsController(AnalyticsQueryService queries) { this.queries = queries; }
  @GetMapping("/runs") public List<AnalyticsQueryService.RunSummary> runs(Principal principal,
      @PathVariable("projectId") UUID project) { return queries.runs(principal, project); }
  @GetMapping({"/comparisons", "/violations", "/dashboard"}) public RunComparisonService.Comparison compare(
      Principal principal, @PathVariable("projectId") UUID project,
      @RequestParam("currentRunId") UUID current, @RequestParam(value="baselineRunId", required=false) UUID baseline,
      @RequestParam(value="state", required=false) RunComparisonService.State state,
      @RequestParam(value="offset", defaultValue="0") int offset, @RequestParam(value="limit", defaultValue="50") int limit,
      @RequestParam(value="service", required=false) String service, @RequestParam(value="rule", required=false) String rule,
      @RequestParam(value="severity", required=false) Integer severity, @RequestParam(value="search", required=false) String search) {
    return queries.compareFiltered(principal, project, current, baseline, state, offset, limit, service, rule, severity, search);
  }
  @GetMapping("/trends") public List<RunComparisonService.Comparison> trend(Principal principal,
      @PathVariable("projectId") UUID project, @RequestParam("currentRunId") UUID current) {
    return queries.trend(principal, project, current);
  }
  @GetMapping("/hotspots") public HotspotRankingService.Ranking hotspots(Principal principal,
      @PathVariable("projectId") UUID project, @RequestParam("currentRunId") UUID current,
      @RequestParam(value="runs", defaultValue="5") int n,
      @RequestParam(value="from", required=false) Instant from, @RequestParam(value="to", required=false) Instant to,
      @RequestParam(value="grouping", defaultValue="SERVICE") HotspotRankingService.Grouping grouping,
      @RequestParam(value="offset", defaultValue="0") int offset, @RequestParam(value="limit", defaultValue="50") int limit,
      @RequestParam(value="service", required=false) String service, @RequestParam(value="rule", required=false) String rule,
      @RequestParam(value="severity", required=false) Integer severity, @RequestParam(value="search", required=false) String search) {
    return queries.hotspotsFiltered(principal, project, current, n, from, to, grouping, offset, limit, service, rule, severity, search);
  }
  @ExceptionHandler(SecurityException.class) ResponseEntity<Map<String, String>> forbidden() {
    return ResponseEntity.status(403).body(Map.of("message", "Analytics access denied for this project."));
  }
  @GetMapping("/explorer") public akegp.analytics.service.HistoryExplorer.Result explorer(Principal principal,
      @PathVariable("projectId") UUID project, @RequestParam("currentRunId") UUID current,
      @RequestParam(value="service", required=false) String service, @RequestParam(value="rule", required=false) String rule,
      @RequestParam(value="severity", required=false) Integer severity, @RequestParam(value="search", required=false) String search,
      @RequestParam(value="from", required=false) Instant from, @RequestParam(value="to", required=false) Instant to,
      @RequestParam(value="offset", defaultValue="0") int offset, @RequestParam(value="limit", defaultValue="50") int limit,
      @RequestParam(value="runs", required=false) Integer runs) {
    return queries.exploreWindow(principal, project, current, service, rule, severity, search, from, to, offset, limit, runs);
  }
  @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }
  @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503).body(Map.of("message", "Analytics storage is unavailable. Check backend configuration and migration status."));
  }
}
