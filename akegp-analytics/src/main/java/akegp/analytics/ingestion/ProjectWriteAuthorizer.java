package akegp.analytics.ingestion;

import java.util.UUID;

/** Must throw on denial. The host adapter supplies authenticated project authorization. */
@FunctionalInterface
public interface ProjectWriteAuthorizer {
  void requireWrite(UUID projectId);
}
