package akegp.rules.repository;

import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import akegp.rules.service.DuplicateADRException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcADRRepository implements ADRRepository {
  private static final String COLUMNS =
      "id, adr_id, project_id, title, status, context, decision, consequences, version, created_at, updated_at";
  private final JdbcTemplate jdbcTemplate;

  public JdbcADRRepository(
      @org.springframework.beans.factory.annotation.Qualifier("rulesJdbcTemplate")
          JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public ArchitectureDecisionRecord insert(ArchitectureDecisionRecord adr) {
    try {
      jdbcTemplate.update(
          """
          INSERT INTO adrs
              (id, adr_id, project_id, title, status, context, decision, consequences, version, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          adr.id(), adr.adrId(), adr.projectId(), adr.title(), adr.status().name(), adr.context(),
          adr.decision(), adr.consequences(), adr.version(), Timestamp.from(adr.createdAt()),
          Timestamp.from(adr.updatedAt()));
      return adr;
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicate(exception)) {
        throw new DuplicateADRException(adr.adrId() + " already exists in this project.");
      }
      throw exception;
    }
  }

  @Override
  public List<ArchitectureDecisionRecord> findByProject(UUID projectId, ADRStatus status, String search) {
    StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
        .append(" FROM adrs WHERE project_id = ?");
    java.util.ArrayList<Object> parameters = new java.util.ArrayList<>(List.of(projectId));
    if (status != null) {
      sql.append(" AND status = ?");
      parameters.add(status.name());
    }
    if (search != null && !search.isBlank()) {
      sql.append(" AND (adr_id ILIKE ? OR title ILIKE ? OR context ILIKE ? OR decision ILIKE ? OR consequences ILIKE ?)");
      String term = "%" + search.trim() + "%";
      parameters.addAll(List.of(term, term, term, term, term));
    }
    sql.append(" ORDER BY updated_at DESC");
    return jdbcTemplate.query(sql.toString(), ADR_ROW_MAPPER, parameters.toArray());
  }

  @Override
  public Optional<ArchitectureDecisionRecord> findById(UUID projectId, UUID id) {
    return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM adrs WHERE project_id = ? AND id = ?",
            ADR_ROW_MAPPER, projectId, id)
        .stream().findFirst();
  }

  @Override
  public boolean existsByAdrId(UUID projectId, String adrId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM adrs WHERE project_id = ? AND adr_id = ?", Integer.class, projectId, adrId);
    return count != null && count > 0;
  }

  @Override
  public boolean update(ArchitectureDecisionRecord adr, int expectedVersion) {
    try {
      return jdbcTemplate.update(
          """
          UPDATE adrs SET adr_id = ?, title = ?, status = ?, context = ?, decision = ?,
              consequences = ?, version = ?, updated_at = ?
          WHERE id = ? AND project_id = ? AND version = ?
          """,
          adr.adrId(), adr.title(), adr.status().name(), adr.context(), adr.decision(),
          adr.consequences(), adr.version(), Timestamp.from(adr.updatedAt()), adr.id(),
          adr.projectId(), expectedVersion) == 1;
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicate(exception)) {
        throw new DuplicateADRException(adr.adrId() + " already exists in this project.");
      }
      throw exception;
    }
  }

  @Override
  public boolean delete(UUID projectId, UUID id) {
    return jdbcTemplate.update("DELETE FROM adrs WHERE project_id = ? AND id = ?", projectId, id) == 1;
  }

  private static final RowMapper<ArchitectureDecisionRecord> ADR_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new ArchitectureDecisionRecord(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("adr_id"),
              resultSet.getObject("project_id", UUID.class),
              resultSet.getString("title"),
              ADRStatus.valueOf(resultSet.getString("status")),
              resultSet.getString("context"),
              resultSet.getString("decision"),
              resultSet.getString("consequences"),
              resultSet.getInt("version"),
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
