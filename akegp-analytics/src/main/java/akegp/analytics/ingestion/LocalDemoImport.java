package akegp.analytics.ingestion;

import akegp.analytics.config.LocalAnalyticsDatabase;
import akegp.analytics.repository.JdbcSnapshotRepository;

/** Manually invoked development command. No reset operation or application startup hook. */
public final class LocalDemoImport {
  private LocalDemoImport() {}
  public static void main(String[] args) {
    if (args.length != 2 || !args[1].equals("--confirm-local-demo")
        || !(args[0].equals("migrate") || args[0].equals("import"))
        || !"development".equals(System.getenv("C4_ENV"))) {
      throw new IllegalArgumentException("Development only: migrate|import --confirm-local-demo, C4_ENV=development");
    }
    var ds = LocalAnalyticsDatabase.dataSource(System.getenv("C4_DB_URL"),
        System.getenv("C4_DB_USER"), System.getenv("C4_DB_PASSWORD"), "c4_demo");
    if (args[0].equals("migrate")) { LocalAnalyticsDatabase.migrate(ds); return; }
    var ingestion = new SnapshotIngestionService(new JdbcSnapshotRepository(ds), ModelsDemoFixture.SOURCE,
        project -> {
          if (!ModelsDemoFixture.PROJECT_ID.equals(project))
            throw new IngestionException(IngestionException.Code.FORBIDDEN, "Only the constructed demo project is permitted");
        });
    var receipts = ModelsDemoFixture.ingest(ingestion);
    System.out.println("Demo snapshot - not upstream analysis: " + receipts.size()
        + " runs processed; " + receipts.stream().filter(r -> r.replay()).count() + " replays");
  }
}
