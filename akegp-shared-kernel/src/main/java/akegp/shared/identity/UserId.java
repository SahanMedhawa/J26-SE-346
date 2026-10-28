package akegp.shared.identity;
import java.util.UUID;
import java.util.Objects;
public record UserId(UUID value) { public UserId { Objects.requireNonNull(value); } }
