package akegp.rules.repository;

import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.ProjectStatus;
import akegp.rules.domain.RegisteredProject;
import akegp.rules.service.DuplicateProjectException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectRepository implements ProjectRepository {

  private static final String SELECT_COLUMNS =
      "id, name, description, repository_url, architecture_styles, registered_at, status";
  private static final String DUPLICATE_REPOSITORY_URL_CONSTRAINT =
      "registered_projects_repository_url_uq";

  private final JdbcTemplate jdbcTemplate;

  public JdbcProjectRepository(
      @org.springframework.beans.factory.annotation.Qualifier("rulesJdbcTemplate")
          JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public RegisteredProject save(RegisteredProject project) {
    String updateSql =
        """
        UPDATE registered_projects
        SET name = ?, description = ?, repository_url = ?, architecture_styles = ?, status = ?
        WHERE id::text = ?
        """;
    try {
      int updated =
          jdbcTemplate.update(
              connection -> {
                PreparedStatement statement = connection.prepareStatement(updateSql);
                statement.setString(1, project.name());
                statement.setString(2, project.description());
                statement.setString(3, project.repositoryUrl());
                statement.setArray(4, architectureStyles(connection, project.architectureStyles()));
                statement.setString(5, project.status().name());
                statement.setString(6, project.id().toString());
                return statement;
              });
      if (updated == 0) {
        insert(project);
      }
    } catch (DataIntegrityViolationException exception) {
      if (isRepositoryUrlConstraintViolation(exception)) {
        throw new DuplicateProjectException(
            "A project is already registered for repository URL: " + project.repositoryUrl());
      }
      throw exception;
    }
    return project;
  }

  private void insert(RegisteredProject project) {
    String sql =
        """
        INSERT INTO registered_projects
            (id, name, description, repository_url, architecture_styles, registered_at, status)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement = connection.prepareStatement(sql);
          statement.setObject(1, project.id());
          statement.setString(2, project.name());
          statement.setString(3, project.description());
          statement.setString(4, project.repositoryUrl());
          statement.setArray(5, architectureStyles(connection, project.architectureStyles()));
          statement.setTimestamp(6, Timestamp.from(project.registeredAt()));
          statement.setString(7, project.status().name());
          return statement;
        });
  }

  @Override
  public List<RegisteredProject> findAll() {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS + " FROM registered_projects ORDER BY registered_at",
        PROJECT_ROW_MAPPER);
  }

  @Override
  public Optional<RegisteredProject> findById(UUID id) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM registered_projects WHERE id::text = ?",
            PROJECT_ROW_MAPPER,
            id.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RegisteredProject> findByNormalizedRepositoryUrl(String normalizedRepositoryUrl) {
    return jdbcTemplate
        .query(
            "SELECT "
                + SELECT_COLUMNS
                + " FROM registered_projects WHERE repository_url = ?",
            PROJECT_ROW_MAPPER,
            normalizedRepositoryUrl)
        .stream()
        .findFirst();
  }

  private static Array architectureStyles(Connection connection, Set<ArchitectureStyle> styles)
      throws SQLException {
    String[] values = styles.stream().map(Enum::name).sorted(Comparator.naturalOrder()).toArray(String[]::new);
    return connection.createArrayOf("text", values);
  }

  private static final RowMapper<RegisteredProject> PROJECT_ROW_MAPPER =
      (ResultSet resultSet, int rowNumber) -> {
        Array sqlArray = resultSet.getArray("architecture_styles");
        String[] styleNames = (String[]) sqlArray.getArray();
        Set<ArchitectureStyle> styles =
            Arrays.stream(styleNames).map(ArchitectureStyle::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RegisteredProject(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getString("repository_url"),
            styles,
            resultSet.getTimestamp("registered_at").toInstant(),
            ProjectStatus.valueOf(resultSet.getString("status")));
      };

  private static boolean isRepositoryUrlConstraintViolation(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof SQLException sqlException
          && "23505".equals(sqlException.getSQLState())
          && (sqlException.getMessage() == null
              || sqlException.getMessage().contains(DUPLICATE_REPOSITORY_URL_CONSTRAINT))) {
        return true;
      }
    }
    return false;
  }
}
