package akegp.analytics.repository;

import akegp.analytics.ingestion.CanonicalSnapshot;
import akegp.shared.analytics.AnalyticsIngestion.Receipt;

public interface SnapshotRepository {
  /** Atomically append, or return an identical replay; never partially persist a run. */
  Receipt append(CanonicalSnapshot snapshot);
}
