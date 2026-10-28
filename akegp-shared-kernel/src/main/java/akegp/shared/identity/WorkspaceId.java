package akegp.shared.identity;
import java.util.UUID;
import java.util.Objects;
public record WorkspaceId(UUID value) { public WorkspaceId { Objects.requireNonNull(value); } }
