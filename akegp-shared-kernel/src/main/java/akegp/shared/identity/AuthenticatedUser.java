package akegp.shared.identity;
public record AuthenticatedUser(UserId id, String email, String displayName) {}
