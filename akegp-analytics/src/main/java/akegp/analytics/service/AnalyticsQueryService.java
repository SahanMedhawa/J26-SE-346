package akegp.analytics.service;

import akegp.analytics.domain.AnalyticsRun;
import akegp.analytics.hotspot.HotspotRankingService;
import akegp.analytics.lifecycle.RunComparisonService;
import akegp.analytics.repository.AnalyticsReadRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

public final class AnalyticsQueryService {
  private final AnalyticsReadRepository repository;
  private final ProjectReadAuthorizer authorization;
  private final RunComparisonService comparisons = new RunComparisonService();
  private final HotspotRankingService hotspots = new HotspotRankingService();
  public AnalyticsQueryService(AnalyticsReadRepository repository, ProjectReadAuthorizer authorization) {
    this.repository = repository; this.authorization = authorization;
  }
  public record RunSummary(UUID id, String streamKey, String externalRunId, long sequence, Instant analyzedAt,
      String branchRef, String repositoryId, String commitRef, String analysisStatus, String gateOutcome,
      String provenance, String label, Integer total, String ruleSetVersion, String detectorVersion,
      String mappingVersion, String scopeVersion, List<String> scope, String dataset, String pipelineOutcome) {}
  public List<RunSummary> runs(Principal principal, UUID project) {
    return load(principal, project).stream().map(r -> new RunSummary(r.id(), r.streamKey(), r.externalRunId(),
        r.sequence(), r.analyzedAt(), r.branchRef(), r.repositoryId(), r.commitRef(), r.analysisStatus(), r.gateOutcome(),
        r.provenance(), r.label(), r.complete() ? r.findings().size() : null, r.ruleSetVersion(), r.detectorVersion(),
        r.mappingVersion(), r.scopeVersion(), r.scope(), r.dataset(), r.pipelineOutcome())).toList();
  }
  public RunComparisonService.Comparison compare(Principal principal, UUID project, UUID current,
      UUID baseline, RunComparisonService.State state, int offset, int limit) {
    return comparisons.compare(load(principal, project), current, baseline, state, offset, limit);
  }
  public List<RunComparisonService.Comparison> trend(Principal principal, UUID project, UUID current) {
    var all = load(principal, project);
    var anchor = RunComparisonService.find(all, current);
    var stream = all.stream().filter(r -> r.streamKey().equals(anchor.streamKey()) && r.sequence() <= anchor.sequence())
        .sorted(Comparator.comparingLong(AnalyticsRun::sequence)).toList();
    return stream.stream().map(r -> comparisons.compare(stream, r.id(), null, null, 0, 1))
        .map(c -> new RunComparisonService.Comparison(c.currentRunId(), c.predecessorRunId(), c.baseline(), c.comparable(),
            c.reason(), c.nonAdjacent(), c.currentTotal(), c.previousTotal(), c.newCount(), c.resolvedCount(), c.persistingCount(),
            c.netChange(), c.direction(), c.densityPerKloc(), c.label(), List.of(), 0, 0, 0)).toList();
  }
  public HotspotRankingService.Ranking hotspots(Principal principal, UUID project, UUID current, int n,
      Instant from, Instant to, HotspotRankingService.Grouping grouping, int offset, int limit) {
    return hotspots.rank(load(principal, project), current, n, from, to, grouping, offset, limit);
  }
  private List<AnalyticsRun> load(Principal principal, UUID project) {
    authorization.requireRead(principal, project);
    return repository.readProject(project);
  }
  public HistoryExplorer.Result explore(Principal principal, UUID project, UUID current, String service, String rule,
      Integer severity, String search, Instant from, Instant to, int offset, int limit) {
    return new HistoryExplorer().explore(load(principal, project), current, service, rule, severity, search, from, to, offset, limit);
  }
  public HistoryExplorer.Result exploreWindow(Principal principal, UUID project, UUID current, String service, String rule,
      Integer severity, String search, Instant from, Instant to, int offset, int limit, Integer runs) {
    return new HistoryExplorer().explore(load(principal, project), current, service, rule, severity, search, from, to, offset, limit, runs);
  }
  public RunComparisonService.Comparison compareFiltered(Principal principal, UUID project, UUID current, UUID baseline,
      RunComparisonService.State state, int offset, int limit, String service, String rule, Integer severity, String search) {
    return comparisons.compare(HistoryExplorer.filterFindings(load(principal, project), service, rule, severity, search), current, baseline, state, offset, limit);
  }
  public HotspotRankingService.Ranking hotspotsFiltered(Principal principal, UUID project, UUID current, int n, Instant from, Instant to,
      HotspotRankingService.Grouping grouping, int offset, int limit, String service, String rule, Integer severity, String search) {
    return hotspots.rank(HistoryExplorer.filterFindings(load(principal, project), service, rule, severity, search), current, n, from, to, grouping, offset, limit);
  }
}
