package akegp.users.profile.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A linked OAuth provider account (Google or GitHub).
 */
public record OAuthAccount(
    UUID id,
    UUID userId,
    String provider,
    String providerAccountId,
    String providerEmail,
    Instant linkedAt,
    boolean emailVerified,
    boolean emailAuthoritative
) {}
