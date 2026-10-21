package akegp.analytics.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;

/** Encapsulates a dedicated pool so sibling modules cannot auto-select this datasource. */
public final class AnalyticsDatabase implements AutoCloseable {
  private final Environment environment;
  private HikariDataSource pool;
  public AnalyticsDatabase(Environment environment) { this.environment = environment; }
  public synchronized DataSource dataSource() {
    if (pool == null) {
      String url = environment.getProperty("AKEGP_ANALYTICS_DB_URL");
      if (url == null || !url.startsWith("jdbc:postgresql://"))
        throw new IllegalStateException("Configure backend AKEGP_ANALYTICS_DB_URL as a PostgreSQL JDBC URL");
      var config = new HikariConfig();
      config.setJdbcUrl(url);
      config.setUsername(environment.getProperty("AKEGP_ANALYTICS_DB_USER"));
      config.setPassword(environment.getProperty("AKEGP_ANALYTICS_DB_PASSWORD"));
      config.setMaximumPoolSize(4);
      config.setConnectionTimeout(15000);
      config.setInitializationFailTimeout(-1);
      config.setPoolName("analytics");
      pool = new HikariDataSource(config);
    }
    return pool;
  }
  @Override public synchronized void close() { if (pool != null) pool.close(); }
}
