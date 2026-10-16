package akegp.enforcement.policy;

import java.util.Optional;

/** Finds which project (and therefore which READY rules) a GitHub repository belongs to. */
public interface ProjectLinkResolver {
  /** @param repo "owner/name"; matched case-insensitively */
  Optional<String> projectIdFor(String repo);
}
