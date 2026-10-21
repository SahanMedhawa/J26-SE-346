package akegp.analytics.service;

import akegp.analytics.domain.AnalyticsRun;
import akegp.analytics.lifecycle.RunComparisonService;
import java.time.Instant;
import java.util.*;

/** All aggregates operate on full snapshots, before response pagination. */
public final class HistoryExplorer {
  public record Point(UUID runId, String run, Instant at, String status, String gateOutcome,
      Integer total, Integer high, Integer medium, Integer low, Integer added, Integer resolved,
      Integer persistent, Double density, String reason, Map<String, Integer> areas) {}
  public record Observation(UUID runId, String run, Instant at, String state, Integer severity) {}
  public record Lifecycle(AnalyticsRun.Finding finding, Instant firstSeen, Instant lastSeen,
      int occurrences, int observedRuns, int currentStreak, double recurrence, boolean active,
      List<Observation> timeline) {}
  public record Result(List<Point> points, List<Lifecycle> lifecycles, int totalLifecycles,
      int completeRuns, int incompleteRuns, List<String> services, List<String> rules,
      String label, String densityNote, int offset, int limit) {}

  public Result explore(List<AnalyticsRun> history, UUID currentId, String service, String rule,
      Integer severity, String search, Instant from, Instant to, int offset, int limit) {
    return explore(history, currentId, service, rule, severity, search, from, to, offset, limit, null);
  }
  public Result explore(List<AnalyticsRun> history, UUID currentId, String service, String rule,
      Integer severity, String search, Instant from, Instant to, int offset, int limit, Integer requestedRuns) {
    if (offset < 0 || limit < 1 || limit > 200 || severity != null && (severity < 1 || severity > 3))
      throw new IllegalArgumentException("Invalid filter or pagination");
    if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("Date range requires from < to");
    var requested = RunComparisonService.find(history, currentId);
    var window = requestedRuns == null ? null : new akegp.analytics.hotspot.HotspotRankingService().rank(history, currentId,
        requestedRuns, from, to, akegp.analytics.hotspot.HotspotRankingService.Grouping.RULE, 0, 1).window();
    var anchor = window != null && window.currentRunId() != null ? RunComparisonService.find(history, window.currentRunId()) : requested;
    var stream = history.stream().filter(r -> r.streamKey().equals(anchor.streamKey()) && r.sequence() <= anchor.sequence())
        .sorted(Comparator.comparingLong(AnalyticsRun::sequence)).toList();
    String needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    boolean filtered = service != null && !service.isBlank() || rule != null && !rule.isBlank() || severity != null || !needle.isEmpty();
    var narrowed = filterFindings(stream, service, rule, severity, search);
    var visible = narrowed.stream().filter(r -> window != null ? window.runIds().contains(r.id())
        : (from == null || !r.analyzedAt().isBefore(from)) && (to == null || r.analyzedAt().isBefore(to))).toList();
    var effectiveAnchor = visible.isEmpty() ? anchor : visible.getLast();
    var engine = new RunComparisonService();
    var points = visible.stream().map(r -> {
      var c = engine.compare(narrowed, r.id(), null, null, 0, 1);
      var areas = new TreeMap<String, Integer>();
      if (r.complete()) r.findings().values().forEach(f -> areas.merge(f.entityName(), 1, Integer::sum));
      return new Point(r.id(), r.externalRunId(), r.analyzedAt(), r.analysisStatus(), r.gateOutcome(), c.currentTotal(),
          count(r, 3), count(r, 2), count(r, 1), c.newCount(), c.resolvedCount(), c.persistingCount(), c.densityPerKloc(), c.reason(), areas);
    }).toList();
    var comparisonByRun = new HashMap<UUID, RunComparisonService.Comparison>();
    for (var r : visible) comparisonByRun.put(r.id(), engine.compare(narrowed, r.id(), null, null, 0, 1));
    var identities = new TreeMap<String, AnalyticsRun.Finding>();
    visible.stream().filter(AnalyticsRun::complete).forEach(r -> identities.putAll(r.findings()));
    var lifecycles = new ArrayList<Lifecycle>();
    for (var entry : identities.entrySet()) {
      String identity = entry.getKey();
      var occurrences = visible.stream().filter(r -> r.complete() && r.findings().containsKey(identity)).toList();
      int streak = 0;
      for (int i = visible.size() - 1; i >= 0; i--) {
        var r = visible.get(i);
        if (!r.complete() || !effectiveAnchor.compatible(r) || !r.findings().containsKey(identity)) break;
        streak++;
      }
      var timeline = new ArrayList<Observation>();
      for (var r : visible) {
        var c = comparisonByRun.get(r.id());
        var f = r.findings().get(identity);
        String state = !r.complete() ? "UNKNOWN" : !c.comparable() ? (f == null ? "BASELINE_ABSENT" : "BASELINE")
            : f != null ? (RunComparisonService.find(narrowed, c.predecessorRunId()).findings().containsKey(identity) ? "PERSISTING" : "NEW")
            : RunComparisonService.find(narrowed, c.predecessorRunId()).findings().containsKey(identity) ? "RESOLVED" : "ABSENT";
        timeline.add(new Observation(r.id(), r.externalRunId(), r.analyzedAt(), state, f == null ? null : f.severity()));
      }
      int observed = (int) visible.stream().filter(AnalyticsRun::complete).count();
      lifecycles.add(new Lifecycle(entry.getValue(), occurrences.getFirst().analyzedAt(), occurrences.getLast().analyzedAt(),
          occurrences.size(), observed, streak, observed == 0 ? 0 : (double) occurrences.size() / observed,
          effectiveAnchor.complete() && effectiveAnchor.findings().containsKey(identity), List.copyOf(timeline)));
    }
    lifecycles.sort(Comparator.comparing(Lifecycle::active).reversed().thenComparing(Comparator.comparingInt(Lifecycle::currentStreak).reversed())
        .thenComparing(l -> l.finding().identity()));
    return new Result(points, lifecycles.stream().skip(offset).limit(limit).toList(), lifecycles.size(),
        (int) visible.stream().filter(AnalyticsRun::complete).count(), (int) visible.stream().filter(r -> !r.complete()).count(),
        stream.stream().flatMap(r -> r.findings().values().stream()).map(AnalyticsRun.Finding::entityKey).distinct().sorted().toList(),
        stream.stream().flatMap(r -> r.findings().values().stream()).map(AnalyticsRun.Finding::ruleKey).distinct().sorted().toList(),
        anchor.label(), filtered ? "Density unavailable for finding filters: matching scoped LOC is not supplied." : "Violations per 1,000 lines in the analyzed scope.", offset, limit);
  }
  private Integer count(AnalyticsRun r, int severity) {
    return r.complete() ? (int) r.findings().values().stream().filter(f -> f.severity() == severity).count() : null;
  }
  public static List<AnalyticsRun> filterFindings(List<AnalyticsRun> history, String service, String rule, Integer severity, String search) {
    if (severity != null && (severity < 1 || severity > 3)) throw new IllegalArgumentException("Severity must be 1..3");
    String needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    boolean filtered = service != null && !service.isBlank() || rule != null && !rule.isBlank() || severity != null || !needle.isEmpty();
    if (!filtered) return history;
    return history.stream().map(r -> new AnalyticsRun(r.id(), r.projectId(), r.streamKey(), r.externalRunId(), r.sequence(),
        r.analyzedAt(), r.repositoryId(), r.branchRef(), r.provenance(), r.dataset(), r.commitRef(), r.analysisStatus(),
        r.pipelineOutcome(), r.gateOutcome(), r.ruleSetVersion(), r.detectorVersion(), r.mappingVersion(), r.scopeVersion(),
        r.identityVersion(), r.scope(), null, r.findings().entrySet().stream().filter(e -> {
          var f = e.getValue();
          return (service == null || service.isBlank() || service.equals(f.entityKey())) && (rule == null || rule.isBlank() || rule.equals(f.ruleKey()))
              && (severity == null || severity == f.severity()) && (needle.isEmpty() || (f.ruleKey() + " " + f.ruleName() + " " + f.entityKey() + " " + f.entityName() + " " + f.location() + " " + f.message() + " " + f.identity()).toLowerCase(Locale.ROOT).contains(needle));
        }).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))).toList();
  }
}
