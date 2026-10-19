package akegp.rules.repository;

import java.util.UUID;

/** Allocates durable project-scoped business identifiers within the caller's transaction. */
public interface ReadableIdRepository {
  enum RecordType { ADR, RULE }

  String nextId(UUID projectId, RecordType type);
}
