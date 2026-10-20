package akegp.analytics.lifecycle;

import akegp.analytics.domain.AnalyticsRun;
import java.util.*;

public final class RunComparisonService {
  public enum State { NEW, RESOLVED, PERSISTING }
  public record Entry(State state, AnalyticsRun.Finding finding) {}
  public record Comparison(UUID currentRunId, UUID predecessorRunId, boolean baseline,
      boolean comparable, String reason, boolean nonAdjacent, Integer currentTotal,
      Integer previousTotal, Integer newCount, Integer resolvedCount, Integer persistingCount,
      Integer netChange, String direction, Double densityPerKloc, String label,
      List<Entry> violations, int totalResults, int offset, int limit) {}

  public Comparison compare(List<AnalyticsRun> history, UUID currentId, UUID baselineId,
      State filter, int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 200) throw new IllegalArgumentException("Invalid pagination: limit 1..200");
    AnalyticsRun current = find(history, currentId);
    var stream = history.stream().filter(r -> r.streamKey().equals(current.streamKey()))
        .sorted(Comparator.comparingLong(AnalyticsRun::sequence)).toList();
    int index = stream.indexOf(current);
    if (!current.complete()) return unavailable(current, false, "Current analysis is incomplete", offset, limit);
    if (baselineId == null && index == 0) return unavailable(current, true, "First complete snapshot in stream", offset, limit);
    AnalyticsRun previous = baselineId == null ? stream.get(index - 1) : find(history, baselineId);
    if (!current.compatible(previous) || !previous.complete()) {
      if (baselineId != null) throw new IllegalArgumentException("Selected baseline is incomplete or incompatible");
      return unavailable(current, true, "New baseline after incomplete analysis or changed coverage/version", offset, limit);
    }
    if (previous.sequence() >= current.sequence()) throw new IllegalArgumentException("Baseline must precede current run");
    boolean gap = stream.stream().filter(r -> r.sequence() > previous.sequence() && r.sequence() < current.sequence())
        .anyMatch(r -> !r.complete() || !current.compatible(r));
    if (gap) throw new IllegalArgumentException("Cannot compare across incomplete or incompatible analysis");
    List<Entry> entries = new ArrayList<>();
    current.findings().forEach((key, f) -> entries.add(new Entry(previous.findings().containsKey(key) ? State.PERSISTING : State.NEW, f)));
    previous.findings().forEach((key, f) -> { if (!current.findings().containsKey(key)) entries.add(new Entry(State.RESOLVED, f)); });
    int added = (int) entries.stream().filter(e -> e.state() == State.NEW).count();
    int resolved = (int) entries.stream().filter(e -> e.state() == State.RESOLVED).count();
    int net = added - resolved;
    var selected = entries.stream().filter(e -> filter == null || e.state() == filter)
        .sorted(Comparator.comparing(Entry::state).thenComparing(e -> e.finding().identity())).toList();
    return new Comparison(current.id(), previous.id(), false, true, "Complete compatible snapshots",
        !previous.id().equals(stream.get(index - 1).id()), current.findings().size(), previous.findings().size(),
        added, resolved, current.findings().size() - added, net,
        net > 0 ? "INCREASED" : net < 0 ? "DECREASED" : "UNCHANGED", density(current), current.label(),
        selected.stream().skip(offset).limit(limit).toList(), selected.size(), offset, limit);
  }
  private Comparison unavailable(AnalyticsRun current, boolean baseline, String reason, int offset, int limit) {
    return new Comparison(current.id(), null, baseline, false, reason, false,
        current.complete() ? current.findings().size() : null, null, null, null, null, null, null,
        current.complete() ? density(current) : null, current.label(), List.of(), 0, offset, limit);
  }
  private Double density(AnalyticsRun r) { return r.loc() == null || r.loc() <= 0 ? null : 1000.0 * r.findings().size() / r.loc(); }
  public static AnalyticsRun find(List<AnalyticsRun> runs, UUID id) {
    return runs.stream().filter(r -> r.id().equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Run does not belong to the authorized project"));
  }
}
