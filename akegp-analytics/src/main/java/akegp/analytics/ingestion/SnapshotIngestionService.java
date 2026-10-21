package akegp.analytics.ingestion;

import akegp.analytics.repository.SnapshotRepository;
import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalyticsIngestion;
import java.util.Objects;

public final class SnapshotIngestionService implements AnalyticsIngestion {
  private final SnapshotRepository repository;
  private final AdapterSource source;
  private final ProjectWriteAuthorizer authorizer;
  private final SnapshotNormalizer normalizer = new SnapshotNormalizer();

  public SnapshotIngestionService(SnapshotRepository repository, AdapterSource source,
      ProjectWriteAuthorizer authorizer) {
    this.repository = Objects.requireNonNull(repository);
    this.source = Objects.requireNonNull(source);
    this.authorizer = Objects.requireNonNull(authorizer);
  }

  @Override public Receipt ingest(AnalysisSnapshot snapshot) {
    if (snapshot == null || snapshot.projectId() == null)
      throw new IngestionException(IngestionException.Code.INVALID, "Project required");
    authorizer.requireWrite(snapshot.projectId());
    return repository.append(normalizer.normalize(snapshot, source));
  }
}
