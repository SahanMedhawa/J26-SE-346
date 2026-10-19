package akegp.rules.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReadableIdRepository implements ReadableIdRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcReadableIdRepository(@Qualifier("rulesJdbcTemplate") JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(transactionManager = "rulesTransactionManager", propagation = Propagation.MANDATORY)
  public String nextId(UUID projectId, RecordType type) {
    BigDecimal number = jdbcTemplate.queryForObject(
        """
        INSERT INTO project_record_counters (project_id, record_type, last_number)
        VALUES (?, ?, 1)
        ON CONFLICT (project_id, record_type)
        DO UPDATE SET last_number = project_record_counters.last_number + 1
        RETURNING last_number
        """, BigDecimal.class, projectId, type.name());
    String digits = number.toBigIntegerExact().toString();
    return (type == RecordType.ADR ? "ADR-" : "R-")
        + "0".repeat(Math.max(0, 3 - digits.length())) + digits;
  }
}
