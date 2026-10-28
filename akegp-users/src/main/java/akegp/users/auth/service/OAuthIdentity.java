package akegp.users.auth.service;

/** Provider evidence resolved exclusively by the server after OAuth authentication. */
public record OAuthIdentity(
    String provider,
    String accountId,
    String email,
    boolean emailVerified,
    boolean autoLinkAllowed,
    String displayName,
    String avatarUrl) {}
