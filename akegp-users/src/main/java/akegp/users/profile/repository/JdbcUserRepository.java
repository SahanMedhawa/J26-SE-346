package akegp.users.profile.repository;

import akegp.users.profile.domain.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository {

  private final JdbcTemplate jdbc;

  private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
      UUID.fromString(rs.getString("id")),
      rs.getString("display_name"),
      rs.getString("email"),
      rs.getString("avatar_url"),
      rs.getString("bio"),
      rs.getTimestamp("created_at").toInstant(),
      rs.getTimestamp("updated_at").toInstant()
  );

  public JdbcUserRepository(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void lock(UUID id) {jdbc.queryForObject("SELECT id FROM users_users WHERE id = ? FOR UPDATE", UUID.class,id);}
  public Optional<User> findById(UUID id) {
    var list = jdbc.query("SELECT * FROM users_users WHERE id = ?", ROW_MAPPER, id);
    return list.stream().findFirst();
  }

  public Optional<User> findByEmail(String email) {
    var list = jdbc.query("SELECT * FROM users_users WHERE lower(email) = lower(?)", ROW_MAPPER, email);
    return list.stream().findFirst();
  }

  public User createIfAbsent(User user) {
    jdbc.update("""
        INSERT INTO users_users (id, display_name, email, avatar_url, bio, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT DO NOTHING
        """,
        user.id(), user.displayName(), user.email(), user.avatarUrl(), user.bio(),
        Timestamp.from(user.createdAt()), Timestamp.from(user.updatedAt()));
    return findByEmail(user.email()).orElseThrow();
  }

  public void updateProfile(UUID id, String displayName, String bio, String avatarUrl) {
    jdbc.update("""
        UPDATE users_users SET display_name = ?, bio = ?, avatar_url = ?, updated_at = ?
        WHERE id = ?
        """,
        displayName, bio, avatarUrl, Timestamp.from(Instant.now()), id);
  }
}
