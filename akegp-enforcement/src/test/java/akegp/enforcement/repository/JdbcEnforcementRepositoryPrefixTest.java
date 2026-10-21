package akegp.enforcement.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The table prefix is part of every SQL statement, so anything that is not a plain identifier is refused. */
class JdbcEnforcementRepositoryPrefixTest {
  @Test void refusesPrefixesThatCouldInjectSql() {
    assertThrows(IllegalArgumentException.class, () -> new JdbcEnforcementRepository(null, "enf_; DROP TABLE x;--"));
    assertThrows(IllegalArgumentException.class, () -> new JdbcEnforcementRepository(null, "Enf_"));
    assertThrows(IllegalArgumentException.class, () -> new JdbcEnforcementRepository(null, "9enf_"));
    assertThrows(IllegalArgumentException.class, () -> new JdbcEnforcementRepository(null, ""));
  }

  @Test void acceptsTheDefaultPrefix() { new JdbcEnforcementRepository(null, "enf_"); }
}
