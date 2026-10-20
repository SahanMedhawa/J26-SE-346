package akegp.analytics;

import akegp.analytics.domain.AnalyticsRun;
import akegp.analytics.ingestion.*;
import akegp.analytics.lifecycle.RunComparisonService;
import akegp.analytics.service.HistoryExplorer;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectHistoryTest {
  static final UUID PROJECT = UUID.fromString("1b7b6197-7fe4-4bf3-afe4-7e500d564b81");
  static ProjectDemoHistory.Dataset dataset() {
    return ProjectDemoHistory.generate(PROJECT, "sahanmedhawa/healthmate", List.of(
        new ProjectDemoHistory.Rule("R-001", "ADR-001", "AppointmentService", "SERVICE", "DIRECT_DATABASE_ACCESS", "PatientDB", "FORBIDDEN", 3,
            List.of("microservices/appointment-service/src/server.ts", "microservices/appointment-service/src/controllers/queue.controller.ts"),
            List.of("microservices/patient-service/src/models/patient-profile.model.ts")),
        new ProjectDemoHistory.Rule("R-004", "ADR-002", "AdminService", "SERVICE", "DIRECT_DATABASE_ACCESS", "DoctorDB", "FORBIDDEN", 3,
            List.of("microservices/admin-service/src/server.ts"), List.of("microservices/doctor-service/src/server.ts"))));
  }
  static List<AnalyticsRun> history() {
    return history(dataset());
  }
  static List<AnalyticsRun> history(ProjectDemoHistory.Dataset data) {
    return data.snapshots().stream().map(s -> {
      var n = new SnapshotNormalizer().normalize(s, data.source());
      var findings = n.occurrences().stream().collect(Collectors.toMap(CanonicalSnapshot.Occurrence::identityKey, o -> {
        var f = o.finding(); return new AnalyticsRun.Finding(o.identityKey(), f.ruleKey(), f.ruleName(), f.entityType(), f.entityKey(), f.entityName(), f.severity(), f.displayLocation(), f.message());
      }));
      return new AnalyticsRun(UUID.nameUUIDFromBytes(s.externalRunId().getBytes(java.nio.charset.StandardCharsets.UTF_8)), s.projectId(), n.streamKey(), s.externalRunId(),
          s.sequence(), s.analyzedAt(), s.repositoryId(), s.branchRef(), "DEMO", data.source().dataset(), s.commitRef(), s.analysisStatus().name(),
          s.pipelineOutcome(), s.gateOutcome(), s.ruleSetVersion(), s.detectorVersion(), s.mappingVersion(), s.scopeVersion(), "identity-v1", s.analyzedScope(), s.loc().lines(), findings);
    }).toList();
  }
  @Test void deterministicNinetySixRunsKeepMetadataAndStableIdentities() {
    var a = dataset(); var b = dataset(); assertEquals(a, b); assertEquals(96, a.snapshots().size());
    assertEquals(84, a.snapshots().stream().filter(s -> s.branchRef().equals("refs/heads/main")).count());
    assertTrue(a.snapshots().stream().allMatch(s -> s.projectId().equals(PROJECT) && s.commitRef().startsWith("synthetic-")));
    var normalizer = new SnapshotNormalizer();
    var first = normalizer.normalize(a.snapshots().getFirst(), a.source());
    var second = normalizer.normalize(a.snapshots().get(1), a.source());
    assertFalse(Collections.disjoint(first.occurrences().stream().map(CanonicalSnapshot.Occurrence::identityKey).toList(),
        second.occurrences().stream().map(CanonicalSnapshot.Occurrence::identityKey).toList()));
    assertThrows(IllegalArgumentException.class, () -> ProjectDemoHistory.generate(PROJECT, "repo", List.of()));
  }
  @Test void failedScansAndVersionBoundariesNeverInventFixes() {
    var history = history(); var engine = new RunComparisonService();
    for (int sequence : List.of(24, 48)) {
      var failed = history.get(sequence - 1);
      assertNull(engine.compare(history, failed.id(), null, null, 0, 20).currentTotal());
      assertTrue(engine.compare(history, history.get(sequence).id(), null, null, 0, 20).baseline());
    }
    assertTrue(engine.compare(history, history.get(56).id(), null, null, 0, 20).baseline());
    assertEquals(0, engine.compare(history, history.get(62).id(), null, null, 0, 20).currentTotal());
    var recurrence = engine.compare(history, history.get(64).id(), null, null, 0, 200);
    assertTrue(recurrence.newCount() > 0); assertEquals(0, recurrence.persistingCount());
  }
  @Test void explorerFiltersBeforePaginationAndKeepsUnknownObservations() {
    var history = history(); var explorer = new HistoryExplorer(); var anchor = history.get(83);
    var all = explorer.explore(history, anchor.id(), null, null, null, null, null, null, 0, 200);
    assertEquals(84, all.points().size()); assertEquals(82, all.completeRuns()); assertEquals(2, all.incompleteRuns());
    assertNull(all.points().get(23).total()); assertNull(all.points().get(24).added());
    assertNotNull(all.points().getLast().density());
    var filtered = explorer.explore(history, anchor.id(), "AdminService", "R-004", 3, null, null, null, 0, 1);
    assertTrue(filtered.totalLifecycles() >= filtered.lifecycles().size());
    assertTrue(filtered.lifecycles().stream().allMatch(l -> l.finding().entityKey().equals("AdminService")));
    assertNull(filtered.points().getLast().density());
    assertTrue(filtered.points().stream().filter(p -> p.total() != null).allMatch(p -> p.total() <= 2));
    var period = explorer.explore(history, anchor.id(), null, null, null, null, history.get(10).analyzedAt(), history.get(12).analyzedAt(), 0, 20);
    assertEquals(2, period.points().size()); assertNotNull(period.points().getFirst().added());
    assertTrue(all.lifecycles().stream().anyMatch(l -> l.timeline().stream().anyMatch(o -> o.state().equals("UNKNOWN"))));
    assertThrows(IllegalArgumentException.class, () -> explorer.explore(history, anchor.id(), null, null, 4, null, null, null, 0, 20));
  }
  static ProjectDemoHistory.Dataset healthMate() {
    String appointment = "microservices/appointment-service/src/", admin = "microservices/admin-service/src/";
    var rules = List.of(
        new ProjectDemoHistory.Rule("R-001", "ADR-001", "AppointmentService", "SERVICE", "DIRECT_DATABASE_ACCESS", "PatientDB", "FORBIDDEN", 3,
            List.of(appointment + "controllers/queue.controller.ts", appointment + "models/appointment.model.ts", appointment + "server.ts"), List.of("microservices/patient-service/src/models/patient-profile.model.ts")),
        new ProjectDemoHistory.Rule("R-002", "ADR-001", "AppointmentService", "SERVICE", "CALLS", "PatientService", "REQUIRED", 2,
            List.of(appointment + "controllers/appointment.controller.ts"), List.of("microservices/patient-service/src/server.ts")),
        new ProjectDemoHistory.Rule("R-003", "ADR-002", "AppointmentService", "SERVICE", "DIRECT_DATABASE_ACCESS", "DoctorDB", "FORBIDDEN", 3,
            List.of("controllers/appointment.controller.ts", "controllers/queue.controller.ts", "middleware/auth.middleware.ts", "models/appointment.model.ts", "models/queue.model.ts", "routes/appointment.routes.ts", "routes/queue.routes.ts", "server.ts", "services/QueueService.ts").stream().map(p -> appointment + p).toList(), List.of("microservices/doctor-service/src/server.ts")),
        new ProjectDemoHistory.Rule("R-004", "ADR-002", "AdminService", "SERVICE", "DIRECT_DATABASE_ACCESS", "DoctorDB", "FORBIDDEN", 3,
            List.of("controllers/admin.controller.ts", "middleware/auth.middleware.ts", "models/admin.model.ts", "routes/admin.routes.ts", "server.ts").stream().map(p -> admin + p).toList(), List.of("microservices/doctor-service/src/server.ts")));
    return ProjectDemoHistory.generate(PROJECT, "sahanmedhawa/healthmate", rules);
  }
  @Test void everyComparisonObeysTheModelAndLatestRunContainsRealChurn() {
    var history = history(healthMate()); var engine = new RunComparisonService();
    for (var run : history) {
      var c = engine.compare(history, run.id(), null, null, 0, 200);
      if (!c.comparable()) continue;
      assertEquals(c.currentTotal(), c.newCount() + c.persistingCount());
      assertEquals(c.previousTotal(), c.resolvedCount() + c.persistingCount());
      assertEquals(c.netChange(), c.newCount() - c.resolvedCount());
      assertEquals(c.netChange(), c.currentTotal() - c.previousTotal());
    }
    var latest = engine.compare(history, history.get(83).id(), null, null, 0, 200);
    assertEquals(16, latest.currentTotal()); assertEquals(3, latest.newCount()); assertEquals(13, latest.persistingCount());
    assertEquals(1, latest.resolvedCount()); assertEquals(2, latest.netChange());
    var r1 = latest.violations().stream().filter(v -> v.finding().ruleKey().equals("R-001")).toList();
    assertEquals(2, r1.stream().filter(v -> v.state() == RunComparisonService.State.NEW).count());
    assertEquals(2, r1.stream().filter(v -> v.state() == RunComparisonService.State.PERSISTING).count());
    assertEquals(1, r1.stream().filter(v -> v.state() == RunComparisonService.State.RESOLVED).count());
    var unchangedTotal = engine.compare(history, history.get(78).id(), null, null, 0, 200);
    assertEquals(0, unchangedTotal.netChange());
    assertTrue(unchangedTotal.newCount() > 0 && unchangedTotal.resolvedCount() > 0);
  }
  @Test void windowsChangePriorityAndModulesProjectWithoutDuplicatingViolations() {
    var history = history(healthMate()); var anchor = history.get(83);
    var engine = new akegp.analytics.hotspot.HotspotRankingService();
    var five = engine.rank(history, anchor.id(), 5, null, null, akegp.analytics.hotspot.HotspotRankingService.Grouping.SERVICE, 0, 200);
    var twenty = engine.rank(history, anchor.id(), 20, null, null, akegp.analytics.hotspot.HotspotRankingService.Grouping.SERVICE, 0, 200);
    assertEquals("AdminService", five.rows().getFirst().entityId());
    assertEquals("AppointmentService", twenty.rows().getFirst().entityId());
    var byDate = engine.rank(history, anchor.id(), 20, history.get(79).analyzedAt(), anchor.analyzedAt().plusSeconds(1), akegp.analytics.hotspot.HotspotRankingService.Grouping.SERVICE, 0, 200);
    assertEquals(five.rows(), byDate.rows()); assertEquals(five.window().runIds(), byDate.window().runIds());
    var modules = engine.rank(history, anchor.id(), 20, null, null, akegp.analytics.hotspot.HotspotRankingService.Grouping.MODULE, 0, 200);
    var rules = engine.rank(history, anchor.id(), 20, null, null, akegp.analytics.hotspot.HotspotRankingService.Grouping.RULE, 0, 200);
    assertTrue(modules.cohortSize() > 2); assertEquals(4, rules.cohortSize());
    assertEquals(twenty.rows().stream().mapToInt(r -> r.rawFrequency()).sum(), modules.rows().stream().mapToInt(r -> r.rawFrequency()).sum());
    assertEquals(16, modules.rows().stream().mapToInt(r -> r.activeCount()).sum());
    for (int size : List.of(5, 20)) {
      var ranked = engine.rank(history, anchor.id(), size, null, null, akegp.analytics.hotspot.HotspotRankingService.Grouping.SERVICE, 0, 200);
      var explored = new HistoryExplorer().explore(history, anchor.id(), null, null, null, null, null, null, 0, 200, size);
      assertEquals(ranked.window().runIds(), explored.points().stream().map(HistoryExplorer.Point::runId).toList());
      for (var lifecycle : explored.lifecycles()) {
        assertTrue(lifecycle.currentStreak() <= size); assertEquals((double) lifecycle.occurrences() / size, lifecycle.recurrence(), 1e-12);
        var detail = ranked.rows().stream().flatMap(r -> r.violations().stream()).filter(v -> v.identity().equals(lifecycle.finding().identity())).findFirst().orElseThrow();
        assertEquals(detail.currentStreak(), lifecycle.currentStreak());
      }
    }
  }
}
