package akegp.shared.analytics;

import java.util.UUID;

/** Internal adapter port, not an unauthenticated HTTP API. */
public interface AnalyticsIngestion {
  Receipt ingest(AnalysisSnapshot snapshot);
  record Receipt(UUID runId, boolean replay, int occurrenceCount, String provenance,
      String label) {}
}
