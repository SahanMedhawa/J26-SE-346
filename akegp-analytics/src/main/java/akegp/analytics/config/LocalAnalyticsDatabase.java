package akegp.analytics.config;

import java.net.URI;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit development/test composition. No auto-discovery, bean or startup migration. */
public final class LocalAnalyticsDatabase {
  private LocalAnalyticsDatabase() {}

  public static void requireLocalTarget(String url, String database) {
    try {
      if (url == null || !url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
      URI uri = URI.create(url.substring(5));
      if (!Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
          || !uri.getPath().equals("/" + database) || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Only an explicit loopback PostgreSQL URL for " + database + " is allowed; no URL options");
    }
  }

  public static DataSource dataSource(String url, String user, String password, String database) {
    requireLocalTarget(url, database);
    if (user == null || user.isBlank() || password == null)
      throw new IllegalArgumentException("Backend database user and password variables required");
    return new DriverManagerDataSource(url, user, password);
  }

  /** Call only after approval for the target. Never invoked by application startup. */
  public static void migrate(DataSource dataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/analytics")
        .defaultSchema("analytics_private").schemas("analytics_private")
        .table("analytics_schema_history").cleanDisabled(true).load().migrate();
  }
}
