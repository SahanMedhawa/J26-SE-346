package akegp.analytics;

import akegp.analytics.config.LocalAnalyticsDatabase;
import akegp.analytics.ingestion.*;
import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalyticsIngestion.Receipt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static akegp.analytics.SnapshotTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotIngestionTest {
  private final SnapshotNormalizer normalizer = new SnapshotNormalizer();
  private CanonicalSnapshot normalize(AnalysisSnapshot s) { return normalizer.normalize(s, ModelsDemoFixture.SOURCE); }

  @Test void orderingAndExactDuplicatesDoNotChangeReplayPayload() {
    var a = ModelsDemoFixture.finding("A"); var c = ModelsDemoFixture.finding("C");
    var first = normalize(snapshot("r", 1, List.of(a, c)));
    var second = normalize(snapshot("r", 1, List.of(c, a, a)));
    assertEquals(first.payload(), second.payload());
    assertEquals(2, second.occurrences().size());
  }

  @Test void severityAndLocationChangeContentButNotIdentity() {
    var a = ModelsDemoFixture.finding("A");
    var first = normalize(snapshot("r", 1, List.of(a)));
    var changed = normalize(snapshot("r2", 2, List.of(change(a, null, 1, a.sourceKey(), "file.java:99"))));
    assertEquals(first.occurrences().getFirst().identityKey(), changed.occurrences().getFirst().identityKey());
    assertNotEquals(first.payload(), changed.payload());
    assertEquals(3, first.occurrences().getFirst().finding().severity());
  }

  @Test void ambiguousConcatenationsAndNullsHaveDistinctEncoding() {
    assertNotEquals(SnapshotNormalizer.tuple("ab", "c"), SnapshotNormalizer.tuple("a", "bc"));
    assertNotEquals(SnapshotNormalizer.tuple((Object) null), SnapshotNormalizer.tuple(""));
  }

  @Test void conflictingDuplicatesFail() {
    var a = ModelsDemoFixture.finding("A");
    var error = assertThrows(IngestionException.class, () -> normalize(snapshot("r", 1,
        List.of(a, change(a, null, 1, a.sourceKey(), null)))));
    assertEquals(IngestionException.Code.CONFLICT, error.code());
  }

  @Test void completenessIsExplicitAndIndependentOfGateOutcome() {
    var complete = normalize(snapshot("r", 1, List.of()));
    assertEquals(AnalysisSnapshot.AnalysisStatus.COMPLETE, complete.input().analysisStatus());
    assertEquals("FAILED", complete.input().gateOutcome());
    var partial = normalize(snapshot(ModelsDemoFixture.PROJECT_ID, "main", "r", 1,
        AnalysisSnapshot.AnalysisStatus.PARTIAL, AnalysisSnapshot.ResultMode.SNAPSHOT, List.of()));
    assertEquals(AnalysisSnapshot.AnalysisStatus.PARTIAL, partial.input().analysisStatus());
    assertNotEquals(complete.payload(), partial.payload());
    assertThrows(IngestionException.class, () -> normalize(snapshot(ModelsDemoFixture.PROJECT_ID,
        "main", "r", 1, null, AnalysisSnapshot.ResultMode.SNAPSHOT, List.of())));
  }

  @Test void deltaMissingFindingsAndInvalidSeverityAreRejected() {
    assertThrows(IngestionException.class, () -> normalize(snapshot(ModelsDemoFixture.PROJECT_ID,
        "main", "r", 1, AnalysisSnapshot.AnalysisStatus.COMPLETE, AnalysisSnapshot.ResultMode.DELTA, List.of())));
    assertThrows(IngestionException.class, () -> normalize(snapshot("r", 1, null)));
    var a = ModelsDemoFixture.finding("A");
    assertThrows(IngestionException.class, () -> normalize(snapshot("r", 1,
        List.of(change(a, null, 4, a.sourceKey(), null)))));
  }

  @Test void unverifiedUpstreamIdsAreRejectedAndVerifiedIdsCheckStructure() {
    var a = ModelsDemoFixture.finding("A");
    var first = change(a, "stable-A", 3, a.sourceKey(), null);
    assertThrows(IngestionException.class, () -> normalize(snapshot("r", 1, List.of(first))));
    var source = new AdapterSource("verified-test", AdapterSource.Provenance.UPSTREAM, "", true);
    var input = snapshot("r", 1, List.of(first, change(a, "stable-A", 3, "different-source", null)));
    assertEquals(IngestionException.Code.CONFLICT,
        assertThrows(IngestionException.class, () -> normalizer.normalize(input, source)).code());
  }

  @Test void projectBranchAndProvenanceAreIsolated() {
    var first = snapshot("r", 1, List.of(ModelsDemoFixture.finding("A")));
    var otherProject = snapshot(UUID.randomUUID(), first.branchRef(), "r", 1,
        first.analysisStatus(), first.resultMode(), first.violations());
    var otherBranch = snapshot(first.projectId(), "other", "r", 1,
        first.analysisStatus(), first.resultMode(), first.violations());
    assertNotEquals(normalize(first).streamKey(), normalize(otherProject).streamKey());
    assertNotEquals(normalize(first).streamKey(), normalize(otherBranch).streamKey());
    assertEquals(normalize(first).occurrences().getFirst().identityKey(), normalize(otherBranch).occurrences().getFirst().identityKey());
    var upstream = normalizer.normalize(first, new AdapterSource(ModelsDemoFixture.SOURCE.system(),
        AdapterSource.Provenance.UPSTREAM, "", false));
    assertNotEquals(normalize(first).streamKey(), upstream.streamKey());
  }

  @Test void deniedProjectNeverReachesStorage() {
    var reached = new AtomicBoolean();
    var service = new SnapshotIngestionService(n -> { reached.set(true); return null; }, ModelsDemoFixture.SOURCE,
        p -> { throw new IngestionException(IngestionException.Code.FORBIDDEN, "Denied"); });
    assertThrows(IngestionException.class, () -> service.ingest(ModelsDemoFixture.snapshots().getFirst()));
    assertFalse(reached.get());
  }

  @Test void fixtureUsesCanonicalServiceAndContainsOnlyConstructedMetadata() {
    var stored = new ArrayList<CanonicalSnapshot>();
    var service = new SnapshotIngestionService(n -> {
      stored.add(n);
      return new Receipt(UUID.randomUUID(), false, n.occurrences().size(), n.source().provenance().name(), n.source().label());
    }, ModelsDemoFixture.SOURCE, p -> assertEquals(ModelsDemoFixture.PROJECT_ID, p));
    var receipts = ModelsDemoFixture.ingest(service);
    assertEquals(List.of(3, 2, 2, 3, 5), receipts.stream().map(Receipt::occurrenceCount).toList());
    assertTrue(stored.stream().allMatch(n -> n.input().loc() == null));
    assertEquals(ModelsDemoFixture.REFERENCE_TIME, stored.getLast().input().analyzedAt());
    assertTrue(receipts.stream().allMatch(r -> r.label().equals("Demo snapshot - not upstream analysis")));
    assertEquals(List.of("A", "B", "C", "D", "F"), stored.getLast().occurrences().stream()
        .map(o -> o.finding().ruleKey().substring("demo-rule-".length())).sorted().toList());
  }

  @Test void localDatabaseGuardRejectsRemoteAndUnexpectedTargets() {
    assertDoesNotThrow(() -> LocalAnalyticsDatabase.requireLocalTarget("jdbc:postgresql://127.0.0.1:5432/c4_test", "c4_test"));
    for (String url : List.of("jdbc:postgresql://remote.invalid/c4_test", "jdbc:postgresql://localhost/production",
        "jdbc:postgresql://localhost/c4_test?user=x", "jdbc:postgresql://localhost.evil/c4_test")) {
      assertThrows(IllegalArgumentException.class, () -> LocalAnalyticsDatabase.requireLocalTarget(url, "c4_test"));
    }
  }

  @Test void scopeOrderingIsIrrelevantAndLocRequiresMatchingCoverage() {
    var b = ModelsDemoFixture.snapshots().getFirst();
    var reversed = new ArrayList<>(b.analyzedScope());
    java.util.Collections.reverse(reversed);
    var equivalent = withScope(b, reversed, null);
    assertEquals(normalize(b).payload(), normalize(equivalent).payload());
    assertThrows(IngestionException.class, () -> normalize(withScope(b, List.of("unrelated"), null)));
    assertThrows(IngestionException.class, () -> normalize(withScope(b, b.analyzedScope(),
        new AnalysisSnapshot.ScopedLoc(100, List.of("unrelated")))));
    assertDoesNotThrow(() -> normalize(withScope(b, b.analyzedScope(),
        new AnalysisSnapshot.ScopedLoc(0, reversed))));
  }

  private AnalysisSnapshot withScope(AnalysisSnapshot b, List<String> scope, AnalysisSnapshot.ScopedLoc loc) {
    return new AnalysisSnapshot(b.contractVersion(), b.projectId(), b.repositoryId(), b.branchRef(),
        b.externalRunId(), b.sequence(), b.analyzedAt(), b.commitRef(), b.ruleSetVersion(),
        b.detectorVersion(), b.mappingVersion(), b.scopeVersion(), scope, b.analysisStatus(),
        b.resultMode(), b.pipelineOutcome(), b.gateOutcome(), loc, b.violations());
  }
}
