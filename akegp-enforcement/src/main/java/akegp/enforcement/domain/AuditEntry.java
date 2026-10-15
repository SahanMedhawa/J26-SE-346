package akegp.enforcement.domain;

import java.time.Instant;

/** Append-only record of what the system did. This is the feed that Component 4 will analyse. */
public record AuditEntry(long id, Instant at, String type, String repo, int prNumber, String detail) {}
