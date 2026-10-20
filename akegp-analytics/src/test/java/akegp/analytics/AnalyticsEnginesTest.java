package akegp.analytics;

import akegp.analytics.domain.AnalyticsRun;
import akegp.analytics.hotspot.HotspotRankingService;
import akegp.analytics.ingestion.ModelsDemoFixture;
import akegp.analytics.ingestion.SnapshotNormalizer;
import akegp.analytics.lifecycle.RunComparisonService;
import akegp.analytics.service.AnalyticsQueryService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsEnginesTest {
  private final RunComparisonService comparisons = new RunComparisonService();
  private final HotspotRankingService hotspots = new HotspotRankingService();
  static final UUID PROJECT = ModelsDemoFixture.PROJECT_ID;
  static AnalyticsRun run(int order, String status, String version, String... ids) {
    Map<String, AnalyticsRun.Finding> findings = new HashMap<>();
    for (String id : ids) {
      var f = ModelsDemoFixture.finding(id);
      findings.put(id, new AnalyticsRun.Finding(id, f.ruleKey(), f.ruleName(), f.entityType(), f.entityKey(), f.entityName(), f.severity(), null, null));
    }
    return new AnalyticsRun(new UUID(0, order), PROJECT, "demo-stream", "run-" + order, order,
        Instant.parse("2026-01-01T12:00:00Z").plusSeconds((order - 1L) * 86400), "repo", "main", "DEMO", "fixture",
        "commit-" + order, status, "FAILED", "FAILED", version, "detector-v1", "mapping-v1", "scope-v1",
        "identity-v1", List.of("Payment", "Order", "Notification"), null, findings);
  }
  static List<AnalyticsRun> demo() { return List.of(run(1,"COMPLETE","v1","A","C","E"),
      run(2,"COMPLETE","v1","A","C"),run(3,"COMPLETE","v1","A","B"),
      run(4,"COMPLETE","v1","A","B","C"),run(5,"COMPLETE","v1","A","B","C","D","F")); }
  private RunComparisonService.Comparison compare(List<AnalyticsRun> runs) {
    return comparisons.compare(runs, runs.getLast().id(), null, null, 0, 200);
  }
  private HotspotRankingService.Ranking rank(List<AnalyticsRun> runs, int n) {
    return hotspots.rank(runs, runs.getLast().id(), n, null, null, HotspotRankingService.Grouping.SERVICE, 0, 200);
  }
  @Test void goldenDriftAndZeroNetChurn() {
    var changed = compare(List.of(run(1,"COMPLETE","v1","A","B","C"),run(2,"COMPLETE","v1","A","C","D","E")));
    assertEquals(4, changed.currentTotal()); assertEquals(2, changed.newCount()); assertEquals(1, changed.resolvedCount());
    assertEquals(2, changed.persistingCount()); assertEquals(1, changed.netChange());
    assertEquals(Set.of("D","E"), changed.violations().stream().filter(v -> v.state() == RunComparisonService.State.NEW)
        .map(v -> v.finding().identity()).collect(java.util.stream.Collectors.toSet()));
    var churn = compare(List.of(run(1,"COMPLETE","v1","A","B"),run(2,"COMPLETE","v1","A","C")));
    assertEquals(0, churn.netChange()); assertEquals(1,churn.newCount()); assertEquals(1,churn.resolvedCount());
    assertEquals("UNCHANGED", churn.direction()); assertEquals(3,churn.violations().size());
  }
  @Test void firstRunIsBaselineWithNullDeltasAndNoInventedDensity() {
    var result = compare(List.of(run(1,"COMPLETE","v1","A")));
    assertTrue(result.baseline()); assertFalse(result.comparable()); assertEquals(1,result.currentTotal());
    assertNull(result.netChange()); assertNull(result.newCount()); assertNull(result.previousTotal()); assertNull(result.densityPerKloc());
  }
  @Test void completeEmptyRunResolvesAndReturningFindingIsNew() {
    var runs = List.of(run(1,"COMPLETE","v1","A"),run(2,"COMPLETE","v1"),run(3,"COMPLETE","v1","A"));
    var empty = comparisons.compare(runs,runs.get(1).id(),null,null,0,20);
    assertEquals(1,empty.resolvedCount()); assertEquals(0,empty.currentTotal());
    assertEquals(1,compare(runs).newCount()); assertEquals(0,compare(runs).persistingCount());
    var row = rank(runs,3).rows().getFirst(); assertEquals(1.0/3,row.persistence(),1e-12);
    assertEquals(2.0/3,row.recurrence(),1e-12);
  }
  @Test void incompleteAndIncompatibleRunsResetBaselineAndWindow() {
    for (var gap : List.of(run(2,"PARTIAL","v1"),run(2,"COMPLETE","v2"))) {
      var runs = List.of(run(1,"COMPLETE","v1","A"),gap,run(3,"COMPLETE","v1","A"));
      assertTrue(compare(runs).baseline()); assertNull(compare(runs).resolvedCount());
      assertEquals(1,rank(runs,20).window().actualRuns());
      assertThrows(IllegalArgumentException.class, () -> comparisons.compare(runs,runs.getLast().id(),runs.getFirst().id(),null,0,20));
    }
    var partial = compare(List.of(run(1,"PARTIAL","v1")));
    assertNull(partial.currentTotal()); assertFalse(partial.baseline());
    assertEquals("INSUFFICIENT_DATA",rank(List.of(run(1,"PARTIAL","v1")),5).status());
  }
  @Test void goldenHotspotComponentsAndNormalizationBeforePagination() {
    var result = rank(demo(),5);
    assertEquals(List.of("Payment","Order","Notification"),result.rows().stream().map(r -> r.entityId()).toList());
    double[][] expected = {{8,1,2.625,.8125,.8,.8729166666666667},{5,.5,1.8,.4,.3,.4166666666666667},{2,0,1,0,.2,.03333333333333333}};
    for (int i=0;i<3;i++) {
      var r=result.rows().get(i); var e=expected[i];
      assertEquals((int)e[0],r.rawFrequency()); assertEquals(e[1],r.normalizedFrequency(),1e-12);
      assertEquals(e[2],r.averageSeverity(),1e-12); assertEquals(e[3],r.normalizedSeverity(),1e-12);
      assertEquals(e[4],r.persistence(),1e-12); assertEquals(e[5],r.score(),1e-12);
      assertEquals(r.score(),r.contributions().frequency()+r.contributions().severity()+r.contributions().persistence(),1e-12);
    }
    var page = hotspots.rank(demo(),demo().getLast().id(),5,null,null,HotspotRankingService.Grouping.SERVICE,1,1);
    assertEquals(result.rows().get(1),page.rows().getFirst()); assertEquals(3,page.cohortSize());
  }
  @Test void equalFrequencyAndInactiveEntitiesRetainHistoricalSeverity() {
    var result=rank(List.of(run(1,"COMPLETE","v1","A","C"),run(2,"COMPLETE","v1")),2);
    assertEquals(2,result.cohortSize());
    assertTrue(result.rows().stream().allMatch(r -> r.normalizedFrequency()==0 && r.persistence()==0 && r.activeCount()==0));
    assertEquals(.5,result.rows().getFirst().score(),1e-12);
  }
  @Test void oneRunFewerRunsAndEmptyCohort() {
    var one=rank(List.of(run(1,"COMPLETE","v1","A")),20);
    assertEquals(1,one.window().actualRuns()); assertTrue(one.window().limitedHistory()); assertEquals(1,one.rows().getFirst().persistence());
    assertEquals(5,rank(demo(),20).window().actualRuns());
    var empty=rank(List.of(run(1,"COMPLETE","v1")),5);
    assertEquals(0,empty.cohortSize()); assertEquals(1,empty.window().actualRuns()); assertEquals("AVAILABLE",empty.status());
  }
  @Test void timeWindowUsesUtcExclusiveEndAndSelectedCurrent() {
    var result=hotspots.rank(demo(),demo().getLast().id(),5,Instant.parse("2026-01-02T00:00:00Z"),
        Instant.parse("2026-01-04T00:00:00Z"),HotspotRankingService.Grouping.SERVICE,0,20);
    assertEquals(2,result.window().actualRuns()); assertEquals(demo().get(2).id(),result.window().currentRunId());
    var empty=hotspots.rank(demo(),demo().getLast().id(),5,Instant.parse("2027-01-01T00:00:00Z"),
        Instant.parse("2027-02-01T00:00:00Z"),HotspotRankingService.Grouping.SERVICE,0,20);
    assertEquals(0,empty.window().actualRuns()); assertEquals("INSUFFICIENT_DATA",empty.status());
  }
  @Test void ruleCohortAndDeterministicTies() {
    var result=hotspots.rank(demo(),demo().getLast().id(),5,null,null,HotspotRankingService.Grouping.RULE,0,20);
    assertEquals(6,result.cohortSize());
    var tied=rank(List.of(run(1,"COMPLETE","v1","D","E")),1);
    assertEquals(List.of("Notification","Order"),tied.rows().stream().map(r->r.entityId()).toList());
  }
  @Test void comparisonPaginationKeepsCountsAndRejectsForeignRun() {
    var runs=demo();
    var result=comparisons.compare(runs,runs.getLast().id(),null,RunComparisonService.State.NEW,1,1);
    assertEquals(2,result.newCount()); assertEquals(2,result.totalResults()); assertEquals(1,result.violations().size());
    assertThrows(IllegalArgumentException.class,()->comparisons.compare(runs,UUID.randomUUID(),null,null,0,20));
  }
  @Test void authorizationDenialDoesNotQueryDatabase() {
    var queried=new AtomicBoolean();
    var service=new AnalyticsQueryService(project->{queried.set(true);return demo();},(principal,project)->{throw new SecurityException();});
    assertThrows(SecurityException.class,()->service.runs(null,PROJECT)); assertFalse(queried.get());
  }
}
