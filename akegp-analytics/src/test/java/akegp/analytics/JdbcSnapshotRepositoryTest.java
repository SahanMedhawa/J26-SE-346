package akegp.analytics;

import akegp.analytics.config.LocalAnalyticsDatabase;
import akegp.analytics.ingestion.*;
import akegp.analytics.repository.JdbcSnapshotRepository;
import akegp.shared.analytics.AnalysisSnapshot;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static akegp.analytics.SnapshotTestData.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only: never reads the application's DB environment or boots Spring. */
@EnabledIfSystemProperty(named = "c4.localDbTests", matches = "true")
class JdbcSnapshotRepositoryTest {
  private static DataSource ds;
  @BeforeAll static void prepare() {
    ds = LocalAnalyticsDatabase.dataSource(System.getenv("C4_TEST_DB_URL"),
        System.getenv("C4_TEST_DB_USER"), System.getenv("C4_TEST_DB_PASSWORD"), "c4_test");
    LocalAnalyticsDatabase.migrate(ds);
  }

  private SnapshotIngestionService service(AdapterSource source, UUID project) {
    return new SnapshotIngestionService(new JdbcSnapshotRepository(ds), source, p -> {
      if (!p.equals(project)) throw new IngestionException(IngestionException.Code.FORBIDDEN, "Denied");
    });
  }
  private AnalysisSnapshot run(UUID p, String id, long sequence, List<AnalysisSnapshot.Finding> findings) {
    return snapshot(p, "main", id, sequence, AnalysisSnapshot.AnalysisStatus.COMPLETE,
        AnalysisSnapshot.ResultMode.SNAPSHOT, findings);
  }

  @Test void replayConflictOrderingEmptyRunsAndHistoricalSeverity() throws Exception {
    UUID p = UUID.randomUUID();
    var service = service(ModelsDemoFixture.SOURCE, p);
    var a = ModelsDemoFixture.finding("A"); var c = ModelsDemoFixture.finding("C");
    var first = service.ingest(run(p, "first", 1, List.of(a, c, a)));
    assertEquals(2, first.occurrenceCount());
    var replay = service.ingest(run(p, "first", 1, List.of(c, a)));
    assertTrue(replay.replay()); assertEquals(first.runId(), replay.runId());
    assertEquals(IngestionException.Code.CONFLICT, assertThrows(IngestionException.class,
        () -> service.ingest(run(p, "first", 1, List.of(a)))).code());
    service.ingest(run(p, "third", 3, List.of(change(a, null, 1, a.sourceKey(), "line:4"))));
    assertEquals(IngestionException.Code.OUT_OF_ORDER, assertThrows(IngestionException.class,
        () -> service.ingest(run(p, "late", 2, List.of()))).code());
    assertThrows(IngestionException.class, () -> service.ingest(run(p, "collision", 3, List.of())));
    assertTrue(service.ingest(run(p, "first", 1, List.of(a, c))).replay());
    assertEquals(0, service.ingest(run(p, "empty", 4, List.of())).occurrenceCount());
    try (var con = ds.getConnection(); var q = con.prepareStatement(
        "SELECT severity FROM analytics_private.occurrences WHERE run_id=? AND rule_key='demo-rule-A'")) {
      q.setObject(1, first.runId()); try (var rs = q.executeQuery()) { assertTrue(rs.next()); assertEquals(3, rs.getInt(1)); }
    }
    assertEquals(3, runCount(p));
  }

  @Test void crossRunIdentityConflictRollsBackEntireRun() throws Exception {
    UUID p = UUID.randomUUID();
    var source = new AdapterSource("verified-test", AdapterSource.Provenance.UPSTREAM, "", true);
    var service = service(source, p);
    var a = ModelsDemoFixture.finding("A");
    service.ingest(run(p, "first", 1, List.of(change(a, "A", 3, a.sourceKey(), null))));
    assertThrows(IngestionException.class, () -> service.ingest(run(p, "bad", 2,
        List.of(ModelsDemoFixture.finding("B"), change(a, "A", 3, "conflicting-anchor", null)))));
    assertEquals(1, runCount(p));
    assertEquals(0, service.ingest(run(p, "fixed", 2, List.of())).occurrenceCount());
  }

  @Test void repeatedFixtureImportIsIdempotent() {
    var service = service(ModelsDemoFixture.SOURCE, ModelsDemoFixture.PROJECT_ID);
    ModelsDemoFixture.ingest(service);
    assertTrue(ModelsDemoFixture.ingest(service).stream().allMatch(r -> r.replay()));
    var history = new akegp.analytics.repository.JdbcAnalyticsReadRepository(ds).readProject(ModelsDemoFixture.PROJECT_ID);
    assertEquals(5, history.size());
    assertEquals(15, history.stream().mapToInt(r -> r.findings().size()).sum());
    var ranking = new akegp.analytics.hotspot.HotspotRankingService().rank(history, history.getLast().id(), 5, null, null,
        akegp.analytics.hotspot.HotspotRankingService.Grouping.SERVICE, 0, 20);
    assertEquals(.8729166666666667, ranking.rows().getFirst().score(), 1e-12);
  }

  @Test void incompleteRunsAndIndependentBranchesRemainDistinct() throws Exception {
    UUID p = UUID.randomUUID();
    var service = service(ModelsDemoFixture.SOURCE, p);
    var partial = snapshot(p, "main", "partial", 1, AnalysisSnapshot.AnalysisStatus.PARTIAL,
        AnalysisSnapshot.ResultMode.SNAPSHOT, List.of());
    var receipt = service.ingest(partial);
    service.ingest(snapshot(p, "other", "partial", 1, AnalysisSnapshot.AnalysisStatus.COMPLETE,
        AnalysisSnapshot.ResultMode.SNAPSHOT, List.of()));
    assertEquals(2, runCount(p));
    try (var c = ds.getConnection(); var q = c.prepareStatement(
        "SELECT analysis_status,gate_outcome FROM analytics_private.runs WHERE id=?")) {
      q.setObject(1, receipt.runId());
      try (var rs = q.executeQuery()) {
        assertTrue(rs.next()); assertEquals("PARTIAL", rs.getString(1)); assertEquals("FAILED", rs.getString(2));
      }
    }
  }

  @Test void concurrentIdenticalImportsCreateOnlyOneRun() throws Exception {
    UUID p = UUID.randomUUID();
    var service = service(ModelsDemoFixture.SOURCE, p);
    var snapshot = run(p, "concurrent", 1, List.of(ModelsDemoFixture.finding("A")));
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> task = () -> { start.await(); return service.ingest(snapshot).replay(); };
      var one = executor.submit(task); var two = executor.submit(task); start.countDown();
      assertNotEquals(one.get(15, java.util.concurrent.TimeUnit.SECONDS), two.get(15, java.util.concurrent.TimeUnit.SECONDS));
    }
    assertEquals(1, runCount(p));
  }
  @Test void fullProjectDatasetBatchesAndReplaysWithoutChangingOccurrences() {
    UUID project = UUID.randomUUID();
    var data = ProjectDemoHistory.generate(project, "sahanmedhawa/healthmate", List.of(
        new ProjectDemoHistory.Rule("R-001", "ADR-001", "AppointmentService", "SERVICE", "DIRECT_DATABASE_ACCESS", "PatientDB", "FORBIDDEN", 3,
            List.of("microservices/appointment-service/src/server.ts"), List.of("microservices/patient-service/src/server.ts"))));
    var ingestion = service(data.source(), project);
    ingestion.ingest(data.snapshots().getFirst()); // An interrupted older per-run importer left a prefix.
    var normalizer = new SnapshotNormalizer();
    var canonical = data.snapshots().stream().map(s -> normalizer.normalize(s, data.source())).toList();
    var importer = new akegp.analytics.repository.JdbcDemoDatasetImporter(ds);
    var firstImport = importer.append(canonical);
    assertTrue(firstImport.getFirst().replay());
    assertEquals(95, firstImport.stream().filter(r -> !r.replay()).count());
    assertTrue(importer.append(canonical).stream().allMatch(r -> r.replay()));
    var history = new akegp.analytics.repository.JdbcAnalyticsReadRepository(ds).readProject(project);
    assertEquals(96, history.size());
    assertEquals(data.snapshots().stream().mapToInt(s -> s.violations().size()).sum(), history.stream().mapToInt(r -> r.findings().size()).sum());
  }
  @Test void bulkDemoConflictRollsBackStreamsAndAllNewRuns() throws Exception {
    UUID project = UUID.randomUUID();
    var source = new AdapterSource("bulk-test", AdapterSource.Provenance.DEMO, "conflict-v1", true);
    var a = ModelsDemoFixture.finding("A");
    var normalizer = new SnapshotNormalizer();
    var first = normalizer.normalize(run(project, "first", 1, List.of(change(a, "stable", 3, "anchor", null))), source);
    var conflicting = normalizer.normalize(run(project, "second", 2, List.of(change(a, "stable", 3, "different-anchor", null))), source);
    assertThrows(IngestionException.class, () -> new akegp.analytics.repository.JdbcDemoDatasetImporter(ds).append(List.of(first, conflicting)));
    assertEquals(0, runCount(project));
    assertThrows(IllegalArgumentException.class, () -> new akegp.analytics.repository.JdbcDemoDatasetImporter(ds).append(List.of(first, first)));
  }

  private long runCount(UUID project) throws Exception {
    try (var c = ds.getConnection(); var q = c.prepareStatement("""
        SELECT count(*) FROM analytics_private.runs r JOIN analytics_private.streams s USING(stream_key)
        WHERE s.project_id=?
        """)) {
      q.setObject(1, project); try (var rs = q.executeQuery()) { rs.next(); return rs.getLong(1); }
    }
  }
}
