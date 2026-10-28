package akegp.users.profile.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Core user identity, created on first OAuth login.
 */
public record User(
    UUID id,
    String displayName,
    String email,
    String avatarUrl,
    String bio,
    Instant createdAt,
    Instant updatedAt
) {
  public User withProfile(String displayName, String bio, String avatarUrl) {
    return new User(id, displayName, email, avatarUrl, bio, createdAt, Instant.now());
  }
}
