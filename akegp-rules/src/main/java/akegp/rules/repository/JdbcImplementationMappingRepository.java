package akegp.rules.repository;

import akegp.rules.domain.ImplementationMappingSide;
import akegp.rules.domain.RuleImplementationMapping;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcImplementationMappingRepository implements ImplementationMappingRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcImplementationMappingRepository(
      @Qualifier("rulesJdbcTemplate") JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean lockRule(UUID projectId, UUID ruleUuid) {
    return !jdbcTemplate.query(
        "SELECT id FROM architecture_rules WHERE project_id = ? AND id = ? FOR UPDATE",
        (rs, row) -> rs.getObject("id", UUID.class), projectId, ruleUuid).isEmpty();
  }

  @Override
  public List<RuleImplementationMapping> findByRule(UUID ruleUuid) {
    return jdbcTemplate.query(
        "SELECT id, rule_id, side, file_path FROM rule_implementation_mappings "
            + "WHERE rule_id = ? ORDER BY side, file_path",
        (rs, row) -> new RuleImplementationMapping(rs.getObject("id", UUID.class),
            rs.getObject("rule_id", UUID.class), ImplementationMappingSide.valueOf(rs.getString("side")),
            rs.getString("file_path")), ruleUuid);
  }

  @Override
  public void insert(RuleImplementationMapping mapping) {
    jdbcTemplate.update(
        "INSERT INTO rule_implementation_mappings (id, rule_id, side, file_path) VALUES (?, ?, ?, ?)",
        mapping.id(), mapping.ruleId(), mapping.side().name(), mapping.filePath());
  }

  @Override
  public void delete(UUID ruleUuid, UUID mappingId) {
    jdbcTemplate.update("DELETE FROM rule_implementation_mappings WHERE rule_id = ? AND id = ?",
        ruleUuid, mappingId);
  }
}
