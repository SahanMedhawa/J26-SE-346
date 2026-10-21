package akegp.enforcement.config;

import javax.sql.DataSource;

/**
 * The enforcement module's own database connection, built from spring.datasource.* (AKEGP_DB_URL / AKEGP_DB_USER /
 * AKEGP_DB_PASSWORD). It is deliberately NOT a Spring bean: the rules module already defines the application's only
 * DataSource bean (rulesDataSource), and a second bean would make every plain DataSource injection ambiguous.
 *
 * The connection pool (HikariCP, already used by the application) is created by reflection, so this module needs no
 * extra compile-time dependency.
 */
final class EnforcementDataSource {
  private EnforcementDataSource() {}

  /** Returns the URL when it is a PostgreSQL JDBC URL; never echoes the URL back (it could contain a password). */
  static String checkedUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("akegp.enforcement.persistence=jdbc but spring.datasource.url (AKEGP_DB_URL) is empty. "
          + "Set AKEGP_DB_URL=jdbc:postgresql://HOST/DATABASE?sslmode=require");
    }
    if (!url.trim().startsWith("jdbc:postgresql://")) {
      int scheme = url.indexOf("://");
      String start = scheme > 0 ? url.substring(0, scheme + 3) : "(no scheme)";
      throw new IllegalStateException("AKEGP_DB_URL must start with jdbc:postgresql:// but it starts with " + start
          + " . Use jdbc:postgresql://HOST/DATABASE?sslmode=require and put the user and password in AKEGP_DB_USER and "
          + "AKEGP_DB_PASSWORD, not in the URL.");
    }
    String rest = url.trim().substring("jdbc:postgresql://".length());
    int slash = rest.indexOf('/');
    String authority = slash < 0 ? rest : rest.substring(0, slash);
    if (authority.contains("@")) {
      throw new IllegalStateException("AKEGP_DB_URL contains a user name and password (text before an @). Remove them: "
          + "the URL must be jdbc:postgresql://HOST/DATABASE?sslmode=require, with the user in AKEGP_DB_USER and the "
          + "password in AKEGP_DB_PASSWORD.");
    }
    return url.trim();
  }

  static DataSource create(String url, String user, String password) {
    String checked = checkedUrl(url);
    try {
      Class<?> type = Class.forName("com.zaxxer.hikari.HikariDataSource");
      Object pool = type.getDeclaredConstructor().newInstance();
      type.getMethod("setPoolName", String.class).invoke(pool, "enforcement");
      type.getMethod("setJdbcUrl", String.class).invoke(pool, checked);
      type.getMethod("setUsername", String.class).invoke(pool, user == null ? "" : user);
      type.getMethod("setPassword", String.class).invoke(pool, password == null ? "" : password);
      type.getMethod("setMaximumPoolSize", int.class).invoke(pool, 5);
      type.getMethod("setConnectionTimeout", long.class).invoke(pool, 30_000L);
      return (DataSource) pool;
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("HikariCP is not on the classpath; add spring-boot-starter-jdbc to the application.", e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("could not create the enforcement connection pool: " + e, e);
    }
  }
}
