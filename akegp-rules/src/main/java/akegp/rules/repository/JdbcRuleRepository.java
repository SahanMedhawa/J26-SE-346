package akegp.rules.repository;

import akegp.rules.domain.ArchitectureElementType;
import akegp.rules.domain.ArchitectureRule;
import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import akegp.rules.domain.RuleSeverity;
import akegp.rules.service.DuplicateRuleException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRuleRepository implements RuleRepository {
  private static final String COLUMNS =
      "id, rule_id, project_id, adr_id, source_element, source_element_type, "
          + "relationship_type, target_element, target_element_type, decision, description, "
          + "severity, created_at, updated_at";
  private final JdbcTemplate jdbcTemplate;

  public JdbcRuleRepository(
      @org.springframework.beans.factory.annotation.Qualifier("rulesJdbcTemplate")
          JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public ArchitectureRule insert(ArchitectureRule rule) {
    try {
      jdbcTemplate.update(
          """
          INSERT INTO architecture_rules
              (id, rule_id, project_id, adr_id, source_element, source_element_type,
               relationship_type, target_element, target_element_type, decision, description,
               severity, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          rule.id(), rule.ruleId(), rule.projectId(), rule.adrId(), rule.sourceElement(),
          rule.sourceElementType().name(), rule.relationshipType().name(), rule.targetElement(),
          rule.targetElementType().name(), rule.decision().name(), rule.description(),
          rule.severity().name(), Timestamp.from(rule.createdAt()), Timestamp.from(rule.updatedAt()));
      return rule;
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicate(exception)) {
        throw new DuplicateRuleException(rule.ruleId());
      }
      throw exception;
    }
  }

  @Override
  public List<ArchitectureRule> findByProject(UUID projectId) {
    return jdbcTemplate.query(
        "SELECT " + COLUMNS + " FROM architecture_rules WHERE project_id = ? ORDER BY updated_at DESC",
        RULE_ROW_MAPPER, projectId);
  }

  @Override
  public Optional<ArchitectureRule> findById(UUID projectId, UUID id) {
    return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM architecture_rules WHERE project_id = ? AND id = ?",
            RULE_ROW_MAPPER, projectId, id)
        .stream().findFirst();
  }

  @Override
  public boolean existsByRuleId(UUID projectId, String ruleId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM architecture_rules WHERE project_id = ? AND rule_id = ?",
        Integer.class, projectId, ruleId);
    return count != null && count > 0;
  }

  @Override
  public ArchitectureRule update(ArchitectureRule rule) {
    try {
      jdbcTemplate.update(
          """
          UPDATE architecture_rules
          SET rule_id = ?, adr_id = ?, source_element = ?, source_element_type = ?,
              relationship_type = ?, target_element = ?, target_element_type = ?, decision = ?,
              description = ?, severity = ?, updated_at = ?
          WHERE id = ? AND project_id = ?
          """,
          rule.ruleId(), rule.adrId(), rule.sourceElement(), rule.sourceElementType().name(),
          rule.relationshipType().name(), rule.targetElement(), rule.targetElementType().name(),
          rule.decision().name(), rule.description(), rule.severity().name(),
          Timestamp.from(rule.updatedAt()), rule.id(), rule.projectId());
      return rule;
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicate(exception)) {
        throw new DuplicateRuleException(rule.ruleId());
      }
      throw exception;
    }
  }

  @Override
  public boolean delete(UUID projectId, UUID id) {
    return jdbcTemplate.update(
        "DELETE FROM architecture_rules WHERE project_id = ? AND id = ?", projectId, id) == 1;
  }

  private static final RowMapper<ArchitectureRule> RULE_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new ArchitectureRule(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("rule_id"),
              resultSet.getObject("project_id", UUID.class),
              resultSet.getObject("adr_id", UUID.class),
              resultSet.getString("source_element"),
              ArchitectureElementType.valueOf(resultSet.getString("source_element_type")),
              RelationshipType.valueOf(resultSet.getString("relationship_type")),
              resultSet.getString("target_element"),
              ArchitectureElementType.valueOf(resultSet.getString("target_element_type")),
              RuleDecision.valueOf(resultSet.getString("decision")),
              resultSet.getString("description"),
              RuleSeverity.valueOf(resultSet.getString("severity")),
              resultSet.getTimestamp("created_at").toInstant(),
              resultSet.getTimestamp("updated_at").toInstant());

  private static boolean isDuplicate(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
