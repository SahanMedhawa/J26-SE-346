package akegp.analytics.ingestion;

import akegp.analytics.config.AnalyticsDatabase;
import akegp.analytics.config.LocalAnalyticsDatabase;
import akegp.analytics.repository.JdbcAnalyticsReadRepository;
import akegp.analytics.repository.JdbcSnapshotRepository;
import akegp.analytics.hotspot.HotspotRankingService;
import akegp.analytics.lifecycle.RunComparisonService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Explicit cloud administration: only Analytics schema and the fixed DEMO project. */
public final class ConfiguredAnalyticsCommand {
  private ConfiguredAnalyticsCommand() {}
  public static void main(String[] args) {
    // Drivers and migration libraries may otherwise log connection URLs. Never print their exceptions.
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
    try {
      if (args.length != 3 || !args[2].equals("--confirm-neon-analytics"))
        throw new IllegalArgumentException();
      var environment = environment(Path.of(args[1]));
      String url = environment.getProperty("AKEGP_ANALYTICS_DB_URL");
      requireNeon(url);
      try (var database = new AnalyticsDatabase(environment)) {
        var ds = database.dataSource();
        switch (args[0]) {
          case "check" -> {
            try (var c = ds.getConnection(); var q = c.createStatement(); var rs = q.executeQuery("SELECT 1")) {
              if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException();
            }
            System.out.println("Neon PostgreSQL connection verified; no credentials displayed.");
          }
          case "migrate" -> {
            LocalAnalyticsDatabase.migrate(ds);
            System.out.println("Analytics-only Flyway migration complete.");
          }
          case "demo" -> {
            var service = new SnapshotIngestionService(new JdbcSnapshotRepository(ds), ModelsDemoFixture.SOURCE,
                p -> { if (!ModelsDemoFixture.PROJECT_ID.equals(p)) throw new SecurityException(); });
            var receipts = ModelsDemoFixture.ingest(service);
            System.out.println("Demo snapshot - not upstream analysis: runs=" + receipts.size()
                + ", replays=" + receipts.stream().filter(r -> r.replay()).count());
          }
          case "verify" -> {
            long started = System.nanoTime();
            var runs = new JdbcAnalyticsReadRepository(ds).readProject(ModelsDemoFixture.PROJECT_ID);
            if (runs.size() != 5) throw new IllegalStateException();
            var current = runs.getLast();
            var ranking = new HotspotRankingService().rank(runs, current.id(), 5, null, null,
                HotspotRankingService.Grouping.SERVICE, 0, 50);
            double[] expected = {0.8729166666666667, 0.4166666666666667, 0.03333333333333333};
            List<String> names = List.of("Payment", "Order", "Notification");
            if (ranking.rows().size() != 3) throw new IllegalStateException();
            for (int i = 0; i < 3; i++) {
              if (!ranking.rows().get(i).entityId().equals(names.get(i))
                  || Math.abs(ranking.rows().get(i).score() - expected[i]) > 1e-10) throw new IllegalStateException();
            }
            var comparison = new RunComparisonService().compare(runs, current.id(), null, null, 0, 50);
            if (comparison.newCount() != 2 || comparison.persistingCount() != 3 || comparison.resolvedCount() != 0)
              throw new IllegalStateException();
            System.out.printf(Locale.ROOT, "Verified DEMO: 5 runs, 15 occurrences, 3 services; read+calculation=%.3f ms; measuredAt=%s%n",
                (System.nanoTime() - started) / 1e6, Instant.now());
          }
          default -> throw new IllegalArgumentException();
        }
      }
    } catch (Exception e) {
      System.err.println("Analytics command failed (" + e.getClass().getSimpleName()
          + "). Verify command, backend environment, Neon TLS and schema permissions. Details suppressed to protect credentials.");
      System.exit(1);
    }
  }

  public static StandardEnvironment environment(Path envFile) throws java.io.IOException {
    var env = new StandardEnvironment();
    Set<String> allowed = Set.of("AKEGP_ANALYTICS_DB_URL", "AKEGP_ANALYTICS_DB_USER", "AKEGP_ANALYTICS_DB_PASSWORD");
    Map<String, Object> values = new HashMap<>();
    // Values stay inside this backend process; no file contents or environment dump is returned.
    try (var lines = Files.lines(envFile)) {
      lines.forEach(line -> {
        int equals = line.indexOf('=');
        if (equals < 0) return;
        String name = line.substring(0, equals).strip().replaceFirst("^export\\s+", "");
        if (!allowed.contains(name)) return;
        String value = line.substring(equals + 1).strip();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
        values.put(name, value);
      });
    }
    env.getPropertySources().addLast(new MapPropertySource("analytics-env-file", values));
    return env;
  }

  public static void requireNeon(String url) {
    if (url == null || !url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
    URI uri = URI.create(url.substring(5));
    if (uri.getHost() == null || !uri.getHost().endsWith(".neon.tech") || uri.getUserInfo() != null
        || uri.getRawQuery() == null || !Arrays.stream(uri.getRawQuery().split("&"))
            .anyMatch(p -> p.equals("sslmode=require") || p.equals("sslmode=verify-full"))) {
      throw new IllegalArgumentException();
    }
  }
}
