package akegp.analytics.ingestion;

import akegp.shared.analytics.AnalysisSnapshot;
import java.util.List;

public record CanonicalSnapshot(AnalysisSnapshot input, AdapterSource source,
    String streamKey, String streamTuple, String identityNamespace,
    String payloadHash, String payload, List<Occurrence> occurrences) {
  public record Occurrence(String identityKey, String structure,
      AnalysisSnapshot.Finding finding) {}
}
