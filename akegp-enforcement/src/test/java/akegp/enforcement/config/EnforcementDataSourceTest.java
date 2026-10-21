package akegp.enforcement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnforcementDataSourceTest {
  @Test void acceptsAJdbcPostgresUrl() {
    String url = "jdbc:postgresql://ep-x.us-east-2.aws.neon.tech/neondb?sslmode=require";
    assertEquals(url, EnforcementDataSource.checkedUrl(" " + url + " "));
  }

  @Test void rejectsTheNeonTemplateAndNeverEchoesItsPassword() {
    String template = "postgresql://neondb_owner:SECRETPW@ep-x.us-east-2.aws.neon.tech/neondb?sslmode=require";
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> EnforcementDataSource.checkedUrl(template));
    assertTrue(e.getMessage().contains("jdbc:postgresql://"));
    assertTrue(e.getMessage().contains("postgresql://"));
    assertFalse(e.getMessage().contains("SECRETPW"));
    assertFalse(e.getMessage().contains("neondb_owner"));
  }

  @Test void rejectsBlankAndMissingUrls() {
    assertThrows(IllegalStateException.class, () -> EnforcementDataSource.checkedUrl(null));
    assertThrows(IllegalStateException.class, () -> EnforcementDataSource.checkedUrl("  "));
  }

  @Test void rejectsCredentialsInsideTheUrlAndNeverEchoesThem() {
    String url = "jdbc:postgresql://neondb_owner:SECRETPW@ep-x.us-east-2.aws.neon.tech/neondb?sslmode=require";
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> EnforcementDataSource.checkedUrl(url));
    assertTrue(e.getMessage().contains("AKEGP_DB_USER"));
    assertFalse(e.getMessage().contains("SECRETPW"));
    assertFalse(e.getMessage().contains("neondb_owner"));
  }

  @Test void anAtSignAfterTheHostIsFine() {
    String url = "jdbc:postgresql://ep-x.neon.tech/neondb?sslmode=require&application=a@b";
    assertEquals(url, EnforcementDataSource.checkedUrl(url));
  }

  @Test void rejectsAnotherDatabaseKind() {
    assertThrows(IllegalStateException.class, () -> EnforcementDataSource.checkedUrl("jdbc:mysql://h/db"));
  }

  @Test void createFailsFastOnABadUrlBeforeTouchingAnyPool() {
    assertThrows(IllegalStateException.class, () -> EnforcementDataSource.create("postgresql://u:p@h/db", "u", "p"));
  }
}
