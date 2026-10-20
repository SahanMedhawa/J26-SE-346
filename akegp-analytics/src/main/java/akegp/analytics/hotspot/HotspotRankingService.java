package akegp.analytics.hotspot;

import akegp.analytics.domain.AnalyticsRun;
import akegp.analytics.lifecycle.RunComparisonService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

public final class HotspotRankingService {
  public enum Grouping { SERVICE, MODULE, RULE }
  public record Factors(double frequency, double severity, double persistence) {}
  public record ViolationHistory(String identity, double recurrence, int currentStreak) {}
  public record Row(String entityId, String entityType, String entityName, int rawFrequency,
      double normalizedFrequency, double averageSeverity, double normalizedSeverity, double persistence,
      int activeCount, double recurrence, double score, Factors contributions, List<ViolationHistory> violations) {}
  public record Window(int requestedRuns, int actualRuns, List<UUID> runIds, UUID currentRunId,
      Instant fromInclusive, Instant toExclusive, String timezone, boolean limitedHistory, String boundaryReason) {}
  public record Ranking(Window window, String grouping, int cohortSize, int frequencyMin, int frequencyMax,
      Factors weights, List<Row> rows, int offset, int limit, String label, String status) {}
  private static final Factors WEIGHTS = new Factors(2.0 / 6, 3.0 / 6, 1.0 / 6);

  public Ranking rank(List<AnalyticsRun> history, UUID currentId, int requested, Instant from, Instant to,
      Grouping grouping, int offset, int limit) {
    if (requested < 1 || requested > 1000 || offset < 0 || limit < 1 || limit > 200)
      throw new IllegalArgumentException("Invalid window or pagination (runs 1..1000, limit 1..200)");
    if ((from == null) != (to == null) || (from != null && !from.isBefore(to)))
      throw new IllegalArgumentException("Time window requires from < to, with explicit UTC offsets");
    AnalyticsRun anchor = RunComparisonService.find(history, currentId);
    var stream = history.stream().filter(r -> r.streamKey().equals(anchor.streamKey()) && r.sequence() <= anchor.sequence())
        .sorted(Comparator.comparingLong(AnalyticsRun::sequence)).toList();
    // Select the current run from the requested period before any factor computation.
    if (from != null) {
      var candidates = stream.stream().filter(r -> !r.analyzedAt().isBefore(from) && r.analyzedAt().isBefore(to)).toList();
      if (candidates.isEmpty()) return empty(requested, from, to, grouping, offset, limit, anchor.label(), "No runs in time window");
      long lastSequence = candidates.getLast().sequence();
      stream = stream.stream().filter(r -> r.sequence() <= lastSequence).toList();
    }
    if (stream.isEmpty()) return empty(requested, from, to, grouping, offset, limit, anchor.label(), "No runs before window end");
    AnalyticsRun newest = stream.getLast();
    if (!newest.complete()) return empty(requested, from, to, grouping, offset, limit, newest.label(), "Newest selected analysis is incomplete");
    List<AnalyticsRun> selected = new ArrayList<>();
    String boundary = "Start of stream";
    for (int i = stream.size() - 1; i >= 0; i--) {
      var r = stream.get(i);
      if (!r.complete() || !newest.compatible(r)) { boundary = "Stopped at incomplete or incompatible analysis"; break; }
      if (from == null || (!r.analyzedAt().isBefore(from) && r.analyzedAt().isBefore(to))) selected.add(r);
      if (from == null && selected.size() == requested) { boundary = "Requested run count reached"; break; }
    }
    Collections.reverse(selected);
    if (selected.isEmpty()) return empty(requested, from, to, grouping, offset, limit, newest.label(), "No complete comparable runs in window");
    int w = selected.size();
    var current = selected.getLast();
    Map<String, List<AnalyticsRun.Finding>> entities = new TreeMap<>();
    for (var run : selected) for (var f : run.findings().values().stream().sorted(Comparator.comparing(AnalyticsRun.Finding::identity)).toList()) {
      if (grouping != Grouping.RULE && !f.entityType().equals(grouping.name())) continue;
      entities.computeIfAbsent(key(f, grouping), ignored -> new ArrayList<>()).add(f);
    }
    int min = entities.values().stream().mapToInt(List::size).min().orElse(0);
    int max = entities.values().stream().mapToInt(List::size).max().orElse(0);
    List<Row> rows = new ArrayList<>();
    for (var entity : entities.entrySet()) {
      String key = entity.getKey();
      var occurrences = entity.getValue();
      var last = occurrences.getLast();
      var active = current.findings().values().stream().filter(f -> belongs(f, key, grouping)).toList();
      double average = occurrences.stream().mapToInt(AnalyticsRun.Finding::severity).average().orElse(0);
      double f = max == min ? 0 : (double) (occurrences.size() - min) / (max - min);
      double s = (average - 1) / 2;
      List<ViolationHistory> details = new ArrayList<>();
      for (String identity : occurrences.stream().map(AnalyticsRun.Finding::identity).distinct().sorted().toList()) {
        int streak = 0;
        for (int i = w - 1; i >= 0 && selected.get(i).findings().containsKey(identity); i--) streak++;
        double recurrence = (double) selected.stream().filter(r -> r.findings().containsKey(identity)).count() / w;
        details.add(new ViolationHistory(identity, recurrence, streak));
      }
      double p = active.isEmpty() ? 0 : details.stream().filter(v -> current.findings().containsKey(v.identity()))
          .mapToDouble(v -> (double) v.currentStreak() / w).average().orElse(0);
      double recurrence = (double) selected.stream().filter(r -> r.findings().values().stream()
          .anyMatch(v -> belongs(v, key, grouping))).count() / w;
      Factors parts = new Factors(WEIGHTS.frequency() * f, WEIGHTS.severity() * s, WEIGHTS.persistence() * p);
      rows.add(new Row(key, grouping.name(), grouping == Grouping.RULE ? last.ruleName() : last.entityName(),
          occurrences.size(), f, average, s, p, active.size(), recurrence,
          parts.frequency() + parts.severity() + parts.persistence(), parts, details));
    }
    // Numerically negligible differences do not decide rank. Raw precision is retained in outputs.
    rows.sort(Comparator.<Row, BigDecimal>comparing(r -> BigDecimal.valueOf(r.score()).setScale(12, RoundingMode.HALF_UP))
        .reversed().thenComparing(Row::entityId));
    var window = new Window(requested, w, selected.stream().map(AnalyticsRun::id).toList(), current.id(),
        from, to, "UTC", w == 1, boundary);
    return new Ranking(window, grouping.name(), rows.size(), min, max, WEIGHTS,
        rows.stream().skip(offset).limit(limit).toList(), offset, limit, current.label(), "AVAILABLE");
  }
  private boolean belongs(AnalyticsRun.Finding f, String key, Grouping grouping) {
    return (grouping == Grouping.RULE || f.entityType().equals(grouping.name())) && key(f, grouping).equals(key);
  }
  private String key(AnalyticsRun.Finding f, Grouping grouping) { return grouping == Grouping.RULE ? f.ruleKey() : f.entityKey(); }
  private Ranking empty(int requested, Instant from, Instant to, Grouping g, int offset, int limit, String label, String reason) {
    return new Ranking(new Window(requested, 0, List.of(), null, from, to, "UTC", false, reason),
        g.name(), 0, 0, 0, WEIGHTS, List.of(), offset, limit, label, "INSUFFICIENT_DATA");
  }
}
