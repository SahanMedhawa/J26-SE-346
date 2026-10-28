package akegp.users.profile.repository;

import akegp.users.profile.domain.OAuthAccount;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOAuthAccountRepository {

  private final JdbcTemplate jdbc;

  private static final RowMapper<OAuthAccount> ROW_MAPPER = (rs, rowNum) -> new OAuthAccount(
      UUID.fromString(rs.getString("id")),
      UUID.fromString(rs.getString("user_id")),
      rs.getString("provider"),
      rs.getString("provider_account_id"),
      rs.getString("provider_email"),
      rs.getTimestamp("linked_at").toInstant(),
      rs.getBoolean("email_verified"),
      rs.getBoolean("email_authoritative")
  );

  public JdbcOAuthAccountRepository(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<OAuthAccount> findByProviderAndAccountId(String provider, String providerAccountId) {
    var list = jdbc.query(
        "SELECT * FROM users_oauth_accounts WHERE provider = ? AND provider_account_id = ?",
        ROW_MAPPER, provider, providerAccountId);
    return list.stream().findFirst();
  }

  public List<OAuthAccount> findByUserId(UUID userId) {
    return jdbc.query("SELECT * FROM users_oauth_accounts WHERE user_id = ?", ROW_MAPPER, userId);
  }

  public Optional<OAuthAccount> insertIfAbsent(OAuthAccount account) {
    jdbc.update("""
        INSERT INTO users_oauth_accounts (id, user_id, provider, provider_account_id, provider_email, linked_at, email_verified, email_authoritative)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT DO NOTHING
        """,
        account.id(), account.userId(), account.provider(),
        account.providerAccountId(), account.providerEmail(),
        Timestamp.from(account.linkedAt()), account.emailVerified(), account.emailAuthoritative());
    return findByProviderAndAccountId(account.provider(), account.providerAccountId());
  }

  public void updateEmailEvidence(UUID id, String email, boolean verified, boolean authoritative) {
    jdbc.update("UPDATE users_oauth_accounts SET provider_email = ?, email_verified = ?, email_authoritative = ? WHERE id = ?",
        email, verified, authoritative, id);
  }

  public void deleteByUserIdAndProvider(UUID userId, String provider) {
    jdbc.update("DELETE FROM users_oauth_accounts WHERE user_id = ? AND provider = ?", userId, provider);
  }

  public long countByUserId(UUID userId) {
    var count = jdbc.queryForObject("SELECT COUNT(*) FROM users_oauth_accounts WHERE user_id = ?", Long.class, userId);
    return count != null ? count : 0;
  }
}
