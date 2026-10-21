package akegp.analytics.ingestion;

import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalysisSnapshot.Finding;
import akegp.shared.analytics.AnalyticsIngestion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Constructed metadata, not repository findings. The supplied five-run example only. */
public final class ModelsDemoFixture {
  public static final UUID PROJECT_ID = UUID.fromString("c4000000-0000-4000-8000-000000000001");
  public static final Instant REFERENCE_TIME = Instant.parse("2026-01-05T12:00:00Z");
  public static final AdapterSource SOURCE = new AdapterSource("models-demo-v1",
      AdapterSource.Provenance.DEMO, "models-five-runs-v1", false);
  private ModelsDemoFixture() {}

  public static List<AnalysisSnapshot> snapshots() {
    List<List<String>> runs = List.of(List.of("A", "C", "E"), List.of("A", "C"),
        List.of("A", "B"), List.of("A", "B", "C"), List.of("A", "B", "C", "D", "F"));
    return IntStream.range(0, 5).mapToObj(i -> new AnalysisSnapshot(1, PROJECT_ID,
        "constructed-demo-repository", "refs/heads/demo", "models-run-" + (i + 1), i + 1,
        REFERENCE_TIME.minusSeconds((4L - i) * 86400), "constructed-demo-commit-" + (i + 1),
        "demo-rules-v1", "demo-detector-v1", "severity-1-3-v1", "demo-scope-v1",
        List.of("Payment", "Order", "Notification").stream()
            .map(e -> SnapshotNormalizer.scopeEntity("SERVICE", e)).toList(),
        AnalysisSnapshot.AnalysisStatus.COMPLETE, AnalysisSnapshot.ResultMode.SNAPSHOT,
        "DEMO", "DEMO", null, runs.get(i).stream().map(ModelsDemoFixture::finding).toList())).toList();
  }

  public static Finding finding(String id) {
    String entity = switch (id) {
      case "A", "B" -> "Payment";
      case "C", "D" -> "Order";
      case "E", "F" -> "Notification";
      default -> throw new IllegalArgumentException("Unknown demo ID");
    };
    int severity = switch (id) { case "A" -> 3; case "B", "C" -> 2; default -> 1; };
    return new Finding(null, "demo-rule-" + id, "Constructed rule " + id,
        "SERVICE", entity, entity, "constructed-dependency", "demo-anchor-" + id,
        "demo-target-" + id, "DEPENDS_ON", severity, null,
        "Constructed DEMO finding " + id + " - not upstream analysis");
  }

  public static List<AnalyticsIngestion.Receipt> ingest(AnalyticsIngestion ingestion) {
    return snapshots().stream().map(ingestion::ingest).toList();
  }
}
